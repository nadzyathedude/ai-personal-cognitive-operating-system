import logging

import numpy as np

logger = logging.getLogger(__name__)

FEATURE_NAMES = [
    "stress_mean",
    "stress_std",
    "stress_slope",
    "stress_trend_strength",
    "valence_mean",
    "valence_std",
    "valence_slope",
    "valence_trend_strength",
    "stress_volatility",
    "valence_volatility",
    "entry_count",
    "days_covered",
]

FEATURE_WEIGHTS = {
    "stress_mean": 0.20,
    "stress_slope": 0.15,
    "stress_volatility": 0.10,
    "valence_mean": -0.15,
    "valence_slope": -0.10,
    "valence_volatility": 0.10,
    "stress_std": 0.05,
    "valence_std": 0.05,
    "stress_trend_strength": 0.05,
    "valence_trend_strength": 0.05,
}


class BurnoutModel:
    def predict(self, features: dict) -> dict:
        score = 0.0
        total_weight = 0.0
        drivers = []

        for feat_name, weight in FEATURE_WEIGHTS.items():
            value = features.get(feat_name, 0.0)
            contribution = value * weight
            score += contribution
            total_weight += abs(weight)

            if abs(contribution) > 0.05:
                direction = "high" if contribution > 0 else "low"
                drivers.append(
                    f"{feat_name.replace('_', ' ')} ({direction}: {value:.3f})"
                )

        normalized = (score + total_weight) / (2 * total_weight)
        burnout_risk = max(0.0, min(1.0, normalized))

        entry_count = features.get("entry_count", 0)
        confidence = min(1.0, entry_count / 14.0) * 0.8 + 0.2

        if burnout_risk < 0.4:
            risk_level = "LOW"
        elif burnout_risk < 0.7:
            risk_level = "MODERATE"
        else:
            risk_level = "HIGH"

        stress_slope = features.get("stress_slope", 0.0)
        projection_7day = burnout_risk + stress_slope * 7
        projection_7day = max(0.0, min(1.0, projection_7day))

        drivers.sort(key=lambda x: abs(float(x.split(":")[-1].strip(")"))), reverse=True)

        return {
            "burnout_risk": round(burnout_risk, 4),
            "confidence": round(confidence, 4),
            "risk_level": risk_level,
            "main_drivers": drivers[:5],
            "projection_7day": round(projection_7day, 4),
        }


burnout_model = BurnoutModel()
