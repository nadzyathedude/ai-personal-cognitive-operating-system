import logging

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.routers.auth import get_user_id
from app.services.coaching import (
    get_user_policy,
    get_user_profile,
    reward_estimator,
)

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/coaching", tags=["coaching"])


class FeedbackRequest(BaseModel):
    strategy: str
    stress_before: float
    stress_after: float
    valence_before: float
    valence_after: float
    hrv_before: float = 0.0
    hrv_after: float = 0.0


@router.get("/recommendation")
async def get_recommendation(user_id: str = Depends(get_user_id)):
    policy = get_user_policy(user_id)
    profile = get_user_profile(user_id)

    selected = policy.select()

    return {
        "recommended_strategy": selected,
        "policy_stats": policy.get_stats(),
        "user_profile": profile.to_dict(),
    }


@router.post("/feedback")
async def submit_feedback(
    req: FeedbackRequest,
    user_id: str = Depends(get_user_id),
):
    reward = reward_estimator.calculate(
        stress_before=req.stress_before,
        stress_after=req.stress_after,
        valence_before=req.valence_before,
        valence_after=req.valence_after,
        hrv_before=req.hrv_before,
        hrv_after=req.hrv_after,
    )

    policy = get_user_policy(user_id)
    policy.update(req.strategy, reward)

    profile = get_user_profile(user_id)
    profile.update_from_feedback(req.strategy, reward)

    return {
        "reward": round(reward, 4),
        "updated_stats": policy.get_stats(),
        "updated_profile": profile.to_dict(),
    }
