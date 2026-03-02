import logging

from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_db
from app.routers.auth import get_user_id
from app.models.tables import MoodJournal
from app.services.emotion_analyzer import emotion_analyzer
from app.services.prosody_analyzer import prosody_extractor
from app.services.acoustic_emotion import acoustic_emotion_model
from app.services.emotion_fusion import emotion_fusion_engine

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/tone", tags=["voice-tone"])


@router.post("/analyze")
async def analyze_voice_tone(
    audio: UploadFile = File(...),
    transcription: str = Form(default=""),
    session_id: str = Form(default=""),
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    audio_bytes = await audio.read()
    if len(audio_bytes) < 1000:
        raise HTTPException(status_code=400, detail="Audio too short")

    try:
        prosody_features = prosody_extractor.extract(audio_bytes)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    serializable_features = {
        k: v for k, v in prosody_features.items()
        if k != "raw_pitch"
    }

    acoustic_result = acoustic_emotion_model.predict(prosody_features)

    text_result = None
    fusion_result = None

    if transcription.strip():
        text_result = emotion_analyzer.analyze(transcription)
        fusion_result = emotion_fusion_engine.fuse(text_result, acoustic_result)

    if session_id:
        result = await db.execute(
            select(MoodJournal).where(MoodJournal.session_id == session_id)
        )
        journal = result.scalar_one_or_none()
        if journal:
            journal.acoustic_emotion = acoustic_result["detected_emotion"]
            journal.tone_descriptor = acoustic_result["tone_descriptor"]
            journal.vocal_stress_score = acoustic_result["vocal_stress_score"]
            journal.prosody_features = serializable_features

            if fusion_result:
                journal.fusion_confidence = fusion_result["fusion_confidence"]
                journal.fusion_valence = fusion_result["final_valence"]
                journal.fusion_arousal = fusion_result["arousal_level"]
                journal.fusion_stress_index = fusion_result["stress_index"]
                journal.stress_score = fusion_result["stress_index"]
                if fusion_result.get("mismatch_detected"):
                    journal.mismatch_detected = fusion_result["mismatch_type"]
            else:
                # Use vocal stress as fallback when no text fusion available
                journal.stress_score = acoustic_result["vocal_stress_score"]

            await db.commit()

    response = {
        "acoustic": acoustic_result,
        "prosody_features": serializable_features,
    }

    if text_result:
        response["text_emotion"] = text_result
    if fusion_result:
        response["fusion"] = fusion_result

    return response


@router.post("/fuse")
async def fuse_emotions(
    text_emotion: str = Form(...),
    text_valence: float = Form(...),
    text_arousal: float = Form(...),
    text_confidence: float = Form(...),
    acoustic_emotion: str = Form(...),
    acoustic_valence: float = Form(...),
    acoustic_arousal: float = Form(...),
    acoustic_stress: float = Form(...),
    acoustic_confidence: float = Form(...),
    user_id: str = Depends(get_user_id),
):
    text_result = {
        "primary_emotion": text_emotion,
        "valence": text_valence,
        "arousal": text_arousal,
        "confidence": text_confidence,
    }

    acoustic_result = {
        "detected_emotion": acoustic_emotion,
        "valence_estimate": acoustic_valence,
        "arousal": acoustic_arousal,
        "vocal_stress_score": acoustic_stress,
        "confidence": acoustic_confidence,
        "tone_descriptor": "",
    }

    fusion = emotion_fusion_engine.fuse(text_result, acoustic_result)
    return fusion
