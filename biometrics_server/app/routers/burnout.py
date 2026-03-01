import logging

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_db
from app.routers.auth import get_user_id
from app.services.burnout.burnout_model import burnout_model
from app.services.burnout.feature_builder import feature_builder
from app.services.burnout.restructuring import (
    restructuring_engine,
    strategy_selector,
)

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/goals", tags=["burnout"])


@router.get("/{goal_id}/burnout-risk")
async def get_burnout_risk(
    goal_id: str,
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    features = await feature_builder.build_features(user_id, goal_id, db)
    prediction = burnout_model.predict(features)

    suggestion = None
    if prediction["risk_level"] == "HIGH":
        suggestion = (
            "Your stress levels have been elevated. Consider breaking this goal "
            "into smaller steps or adjusting your timeline. Remember, progress "
            "isn't always linear."
        )
    elif prediction["risk_level"] == "MODERATE":
        suggestion = "How are you feeling about your progress on this goal?"

    prediction["suggestion"] = suggestion
    prediction["goal_id"] = goal_id

    return prediction


@router.get("/{goal_id}/restructure")
async def get_restructuring_plan(
    goal_id: str,
    goal_title: str = Query(...),
    goal_description: str = Query(""),
    deadline: str = Query(""),
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    features = await feature_builder.build_features(user_id, goal_id, db)
    prediction = burnout_model.predict(features)
    strategy = strategy_selector.select(features, prediction)

    plan = await restructuring_engine.generate_plan(
        goal_title=goal_title,
        goal_description=goal_description,
        deadline=deadline,
        strategy=strategy["primary_strategy"],
        burnout_risk=prediction["burnout_risk"],
    )

    return {
        "burnout_prediction": prediction,
        "selected_strategy": strategy,
        "restructured_plan": plan,
    }
