import base64
import io
import logging
import wave

from fastapi import WebSocket, WebSocketDisconnect
from jose import JWTError, jwt
from sqlalchemy import select

from app.config import settings
from app.db.database import async_session
from app.models.tables import SpeakerEmbedding, MoodJournal
from app.services.emotion_analyzer import emotion_analyzer

try:
    from app.services.speaker_embedding import speaker_service
except ImportError:
    speaker_service = None

try:
    from app.services.voice_stress import feature_extractor, stress_model, hybrid_combiner
except ImportError:
    feature_extractor = None
    stress_model = None
    hybrid_combiner = None
from app.websocket.protocol import (
    ClientMessageType,
    ServerMessageType,
    parse_message,
    server_msg,
)

logger = logging.getLogger(__name__)


def _pcm_to_wav(pcm_data: bytes, sample_rate: int = 16000) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(pcm_data)
    return buf.getvalue()


async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()

    user_id = None
    audio_buffer = bytearray()
    current_mode = None

    try:
        # Authenticate first message
        raw = await websocket.receive_text()
        data = parse_message(raw)
        if not data or data.get("type") != "authenticate":
            await websocket.send_text(
                server_msg(ServerMessageType.ERROR, {"message": "Authentication required"})
            )
            await websocket.close(4001)
            return

        token = data.get("token", "")
        try:
            payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
            user_id = payload.get("sub")
            if not user_id:
                raise JWTError("Missing sub claim")
        except JWTError:
            await websocket.send_text(
                server_msg(ServerMessageType.ERROR, {"message": "Invalid token"})
            )
            await websocket.close(4003)
            return

        await websocket.send_text(
            server_msg(ServerMessageType.VERIFICATION_RESULT, {"authenticated": True})
        )

        while True:
            raw = await websocket.receive_text()
            data = parse_message(raw)
            if not data:
                continue

            msg_type = data.get("type")

            if msg_type == ClientMessageType.VOICE_VERIFICATION_START:
                audio_buffer = bytearray()
                current_mode = "verification"

            elif msg_type == ClientMessageType.VOICE_VERIFICATION_AUDIO:
                if current_mode != "verification":
                    continue
                chunk = data.get("data", "")
                if isinstance(chunk, str):
                    decoded = base64.b64decode(chunk)
                    if len(audio_buffer) + len(decoded) > settings.max_audio_bytes:
                        await websocket.send_text(
                            server_msg(ServerMessageType.VERIFICATION_ERROR, {
                                "message": "Audio buffer exceeded"
                            })
                        )
                        audio_buffer = bytearray()
                        current_mode = None
                        continue
                    audio_buffer.extend(decoded)

            elif msg_type == ClientMessageType.VOICE_VERIFICATION_END:
                if current_mode != "verification" or len(audio_buffer) == 0:
                    await websocket.send_text(
                        server_msg(ServerMessageType.VERIFICATION_ERROR, {
                            "message": "No audio data"
                        })
                    )
                    current_mode = None
                    continue

                wav_data = _pcm_to_wav(bytes(audio_buffer))
                audio_buffer = bytearray()
                current_mode = None

                async with async_session() as db:
                    result = await db.execute(
                        select(SpeakerEmbedding).where(
                            SpeakerEmbedding.user_id == user_id
                        )
                    )
                    record = result.scalar_one_or_none()

                if record is None:
                    await websocket.send_text(
                        server_msg(ServerMessageType.VERIFICATION_ERROR, {
                            "message": "No enrolled voiceprint found"
                        })
                    )
                    continue

                try:
                    verification = speaker_service.verify(wav_data, record.embedding)
                    await websocket.send_text(
                        server_msg(ServerMessageType.VERIFICATION_RESULT, verification)
                    )
                except Exception as e:
                    logger.error(f"Verification failed: {e}")
                    await websocket.send_text(
                        server_msg(ServerMessageType.VERIFICATION_ERROR, {
                            "message": "Verification processing failed"
                        })
                    )

            elif msg_type == ClientMessageType.STRESS_ANALYSIS_START:
                audio_buffer = bytearray()
                current_mode = "stress"

            elif msg_type == ClientMessageType.STRESS_ANALYSIS_AUDIO:
                if current_mode != "stress":
                    continue
                chunk = data.get("data", "")
                if isinstance(chunk, str):
                    decoded = base64.b64decode(chunk)
                    if len(audio_buffer) + len(decoded) > settings.max_audio_bytes:
                        await websocket.send_text(
                            server_msg(ServerMessageType.STRESS_ERROR, {
                                "message": "Audio buffer exceeded"
                            })
                        )
                        audio_buffer = bytearray()
                        current_mode = None
                        continue
                    audio_buffer.extend(decoded)

            elif msg_type == ClientMessageType.STRESS_ANALYSIS_END:
                if current_mode != "stress" or len(audio_buffer) == 0:
                    await websocket.send_text(
                        server_msg(ServerMessageType.STRESS_ERROR, {
                            "message": "No audio data"
                        })
                    )
                    current_mode = None
                    continue

                wav_data = _pcm_to_wav(bytes(audio_buffer))
                transcription = data.get("transcription", "")
                session_id = data.get("session_id")
                audio_buffer = bytearray()
                current_mode = None

                try:
                    features = feature_extractor.extract(wav_data)
                    acoustic_stress = stress_model.predict(features)

                    text_emotion = {}
                    combined = acoustic_stress
                    if transcription:
                        text_emotion = emotion_analyzer.analyze(transcription)
                        combined = hybrid_combiner.combine(text_emotion, acoustic_stress)

                    # Store stress score in mood journal if session exists
                    if session_id:
                        async with async_session() as db:
                            from sqlalchemy import update
                            await db.execute(
                                update(MoodJournal)
                                .where(MoodJournal.session_id == session_id)
                                .values(
                                    stress_score=acoustic_stress["stress_score"],
                                    stress_confidence=acoustic_stress["confidence"],
                                )
                            )
                            await db.commit()

                    await websocket.send_text(
                        server_msg(ServerMessageType.STRESS_RESULT, {
                            "acoustic_stress": acoustic_stress,
                            "text_emotion": text_emotion,
                            "combined": combined,
                            "features": {
                                k: v for k, v in features.items()
                                if k not in ("mfcc_means", "mfcc_stds")
                            },
                        })
                    )
                except Exception as e:
                    logger.error(f"Stress analysis failed: {e}")
                    await websocket.send_text(
                        server_msg(ServerMessageType.STRESS_ERROR, {
                            "message": f"Stress analysis failed: {str(e)}"
                        })
                    )

    except WebSocketDisconnect:
        logger.info(f"WebSocket disconnected: user={user_id}")
    except Exception as e:
        logger.error(f"WebSocket error: {e}")
        try:
            await websocket.close(1011)
        except Exception:
            pass
