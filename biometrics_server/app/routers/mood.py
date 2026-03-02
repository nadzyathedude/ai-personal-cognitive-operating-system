import logging
from io import BytesIO

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from openai import AsyncOpenAI
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.db.database import get_db
from app.routers.auth import get_user_id
from app.services.mood_journal import mood_journal_service

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/mood", tags=["mood-journal"])

_openai_client: AsyncOpenAI | None = None


def _get_openai() -> AsyncOpenAI:
    global _openai_client
    if _openai_client is None:
        _openai_client = AsyncOpenAI(api_key=settings.openai_api_key)
    return _openai_client


class InitialAnswerRequest(BaseModel):
    session_id: str
    answer: str


class FollowupAnswerRequest(BaseModel):
    session_id: str
    question_index: int
    answer: str


@router.post("/start")
async def start_mood_session(
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    result = await mood_journal_service.start_session(user_id, db)
    return result


@router.post("/answer")
async def process_initial_answer(
    req: InitialAnswerRequest,
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    if not req.answer.strip():
        raise HTTPException(status_code=400, detail="Answer cannot be empty")

    try:
        result = await mood_journal_service.process_initial_answer(
            req.session_id, req.answer, db
        )
        return result
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.post("/answer-voice")
async def process_initial_answer_voice(
    audio: UploadFile = File(...),
    session_id: str = Form(...),
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    """Accept voice audio, transcribe with Whisper, then process as mood answer."""
    audio_bytes = await audio.read()
    if len(audio_bytes) < 1000:
        raise HTTPException(status_code=400, detail="Audio too short")

    transcription = await _transcribe_audio(audio_bytes)
    if not transcription.strip():
        raise HTTPException(status_code=400, detail="Could not transcribe audio")

    logger.info(f"Transcribed voice answer: {transcription[:100]}...")

    try:
        result = await mood_journal_service.process_initial_answer(
            session_id, transcription, db
        )
        result["transcription"] = transcription
        return result
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.post("/followup")
async def process_followup_answer(
    req: FollowupAnswerRequest,
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    if not req.answer.strip():
        raise HTTPException(status_code=400, detail="Answer cannot be empty")
    if req.question_index not in (0, 1, 2):
        raise HTTPException(status_code=400, detail="question_index must be 0, 1, or 2")

    try:
        result = await mood_journal_service.process_followup_answer(
            req.session_id, req.question_index, req.answer, db
        )
        return result
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.post("/followup-voice")
async def process_followup_answer_voice(
    audio: UploadFile = File(...),
    session_id: str = Form(...),
    question_index: int = Form(...),
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    """Accept voice audio for follow-up, transcribe with Whisper, then process."""
    audio_bytes = await audio.read()
    if len(audio_bytes) < 1000:
        raise HTTPException(status_code=400, detail="Audio too short")

    transcription = await _transcribe_audio(audio_bytes)
    if not transcription.strip():
        raise HTTPException(status_code=400, detail="Could not transcribe audio")

    logger.info(f"Transcribed follow-up answer: {transcription[:100]}...")

    try:
        result = await mood_journal_service.process_followup_answer(
            session_id, question_index, transcription, db
        )
        result["transcription"] = transcription
        return result
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


async def _transcribe_audio(audio_bytes: bytes) -> str:
    """Transcribe audio bytes using OpenAI Whisper API."""
    client = _get_openai()
    transcription = await client.audio.transcriptions.create(
        model="whisper-1",
        file=("recording.wav", BytesIO(audio_bytes), "audio/wav"),
    )
    return transcription.text
