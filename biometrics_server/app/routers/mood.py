import logging

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_db
from app.routers.auth import get_user_id
from app.services.mood_journal import mood_journal_service

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/mood", tags=["mood-journal"])


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
