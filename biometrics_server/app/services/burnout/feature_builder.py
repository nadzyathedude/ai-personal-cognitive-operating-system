import logging
from datetime import datetime, timedelta

import numpy as np
from scipy import stats
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.tables import MoodJournal

logger = logging.getLogger(__name__)


class TemporalFeatureBuilder:
    def __init__(self, lookback_days: int = 14):
        self.lookback_days = lookback_days

    async def build_features(
        self, user_id: str, goal_id: str, db: AsyncSession
    ) -> dict:
        cutoff = datetime.utcnow() - timedelta(days=self.lookback_days)

        result = await db.execute(
            select(MoodJournal)
            .where(
                MoodJournal.user_id == user_id,
                MoodJournal.created_at >= cutoff,
            )
            .order_by(MoodJournal.created_at.asc())
        )
        entries = result.scalars().all()

        if not entries:
            return self._empty_features()

        stresses = [e.stress_score or 0.0 for e in entries]
        valences = [e.valence or 0.0 for e in entries]

        features = {}
        features.update(self._compute_stats(stresses, "stress"))
        features.update(self._compute_stats(valences, "valence"))

        features["entry_count"] = len(entries)
        features["days_covered"] = min(self.lookback_days, len(entries))

        volatility_stress = self._volatility_index(stresses)
        volatility_valence = self._volatility_index(valences)
        features["stress_volatility"] = volatility_stress
        features["valence_volatility"] = volatility_valence

        return features

    def _compute_stats(self, values: list[float], prefix: str) -> dict:
        arr = np.array(values)
        result = {
            f"{prefix}_mean": float(np.mean(arr)),
            f"{prefix}_std": float(np.std(arr)) if len(arr) > 1 else 0.0,
            f"{prefix}_slope": 0.0,
            f"{prefix}_trend_strength": 0.0,
        }

        if len(arr) >= 2:
            x = np.arange(len(arr))
            slope, intercept, r_value, p_value, std_err = stats.linregress(x, arr)
            result[f"{prefix}_slope"] = float(slope)
            result[f"{prefix}_trend_strength"] = float(r_value ** 2)

        return result

    def _volatility_index(self, values: list[float]) -> float:
        if len(values) < 2:
            return 0.0
        diffs = np.abs(np.diff(values))
        return float(np.mean(diffs))

    def _empty_features(self) -> dict:
        return {
            "stress_mean": 0.0,
            "stress_std": 0.0,
            "stress_slope": 0.0,
            "stress_trend_strength": 0.0,
            "valence_mean": 0.0,
            "valence_std": 0.0,
            "valence_slope": 0.0,
            "valence_trend_strength": 0.0,
            "stress_volatility": 0.0,
            "valence_volatility": 0.0,
            "entry_count": 0,
            "days_covered": 0,
        }


feature_builder = TemporalFeatureBuilder()
