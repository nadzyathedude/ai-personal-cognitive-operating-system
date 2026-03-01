import logging
from enum import Enum

from openai import AsyncOpenAI

from app.config import settings

logger = logging.getLogger(__name__)


class RestructuringStrategy(str, Enum):
    TIMELINE_EXTENSION = "timeline_extension"
    GOAL_DECOMPOSITION = "goal_decomposition"
    INTENSITY_REDUCTION = "intensity_reduction"
    EFFORT_REBALANCING = "effort_rebalancing"
    RECOVERY_BUFFER_INSERTION = "recovery_buffer_insertion"


STRATEGY_CONDITIONS = {
    RestructuringStrategy.TIMELINE_EXTENSION: lambda f: f.get("stress_slope", 0) > 0.02,
    RestructuringStrategy.GOAL_DECOMPOSITION: lambda f: f.get("burnout_risk", 0) > 0.7,
    RestructuringStrategy.INTENSITY_REDUCTION: lambda f: f.get("stress_mean", 0) > 0.6,
    RestructuringStrategy.EFFORT_REBALANCING: lambda f: f.get("stress_volatility", 0) > 0.15,
    RestructuringStrategy.RECOVERY_BUFFER_INSERTION: lambda f: (
        f.get("valence_slope", 0) < -0.02 and f.get("stress_mean", 0) > 0.5
    ),
}


class StrategySelector:
    def select(self, features: dict, burnout_prediction: dict) -> dict:
        features_with_burnout = {**features, **burnout_prediction}

        selected = []
        drivers = []

        for strategy, condition in STRATEGY_CONDITIONS.items():
            if condition(features_with_burnout):
                selected.append(strategy)
                drivers.append(f"{strategy.value}: condition met")

        if not selected:
            selected = [RestructuringStrategy.TIMELINE_EXTENSION]
            drivers = ["default: no specific trigger"]

        primary = selected[0]
        if burnout_prediction.get("risk_level") == "HIGH":
            if RestructuringStrategy.GOAL_DECOMPOSITION in selected:
                primary = RestructuringStrategy.GOAL_DECOMPOSITION
            elif RestructuringStrategy.RECOVERY_BUFFER_INSERTION in selected:
                primary = RestructuringStrategy.RECOVERY_BUFFER_INSERTION

        return {
            "primary_strategy": primary.value,
            "all_strategies": [s.value for s in selected],
            "reasoning_drivers": drivers,
        }


RESTRUCTURING_PROMPT = """You are a supportive coaching assistant. Based on the following context, generate a restructured goal plan.

Current goal: {goal_title}
Description: {goal_description}
Deadline: {deadline}
Strategy: {strategy}
Current burnout risk: {burnout_risk}

Generate a brief restructured plan with:
1. A revised, more achievable approach
2. 3-5 smaller actionable micro-tasks
3. Suggested adjusted timeline (if applicable)
4. One encouraging note

Be warm, supportive, and non-judgmental. Never use medical language or diagnose burnout.
Format as JSON with keys: "revised_approach", "micro_tasks" (list of strings), "adjusted_deadline", "encouragement"."""


class GoalRestructuringEngine:
    def __init__(self):
        self._client: AsyncOpenAI | None = None

    def _get_client(self) -> AsyncOpenAI:
        if self._client is None:
            self._client = AsyncOpenAI(api_key=settings.openai_api_key)
        return self._client

    async def generate_plan(
        self,
        goal_title: str,
        goal_description: str,
        deadline: str,
        strategy: str,
        burnout_risk: float,
    ) -> dict:
        client = self._get_client()

        prompt = RESTRUCTURING_PROMPT.format(
            goal_title=goal_title,
            goal_description=goal_description,
            deadline=deadline,
            strategy=strategy,
            burnout_risk=f"{burnout_risk:.0%}",
        )

        response = await client.chat.completions.create(
            model=settings.llm_model,
            messages=[{"role": "user", "content": prompt}],
            max_tokens=500,
            temperature=0.7,
            response_format={"type": "json_object"},
        )

        import json
        return json.loads(response.choices[0].message.content)


strategy_selector = StrategySelector()
restructuring_engine = GoalRestructuringEngine()
