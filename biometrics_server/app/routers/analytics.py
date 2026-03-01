import logging

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_db
from app.routers.auth import get_user_id
from app.services.weekly_analytics import weekly_aggregator

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/analytics", tags=["analytics"])


@router.get("/weekly")
async def get_weekly_analytics(
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    result = await weekly_aggregator.aggregate(user_id, db)
    return result
