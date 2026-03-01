import datetime
import logging

import numpy as np
from openai import AsyncOpenAI
from scipy import stats
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.tables import MoodJournal

logger = logging.getLogger(__name__)


class WeeklyMoodAggregator:
    def __init__(self):
        self._client: AsyncOpenAI | None = None

    def _get_client(self) -> AsyncOpenAI:
        if self._client is None:
            self._client = AsyncOpenAI(api_key=settings.openai_api_key)
        return self._client

    async def aggregate(self, user_id: str, db: AsyncSession) -> dict:
        seven_days_ago = datetime.datetime.utcnow() - datetime.timedelta(days=7)

        result = await db.execute(
            select(MoodJournal)
            .where(
                MoodJournal.user_id == user_id,
                MoodJournal.created_at >= seven_days_ago,
            )
            .order_by(MoodJournal.created_at.asc())
        )
        entries = result.scalars().all()

        if not entries:
            return {
                "daily_mood_scores": [],
                "daily_stress_scores": [],
                "emotion_distribution": {},
                "weekly_summary": "No mood data recorded this week.",
                "stats": {},
            }

        daily_mood = []
        daily_stress = []
        emotions = {}

        for entry in entries:
            date_str = entry.created_at.strftime("%Y-%m-%d") if entry.created_at else ""
            daily_mood.append({
                "date": date_str,
                "valence": entry.valence or 0.0,
            })
            daily_stress.append({
                "date": date_str,
                "stress_score": entry.stress_score or 0.0,
            })
            if entry.detected_emotion:
                emotions[entry.detected_emotion] = emotions.get(entry.detected_emotion, 0) + 1

        valences = [e.valence for e in entries if e.valence is not None]
        stresses = [e.stress_score for e in entries if e.stress_score is not None]

        avg_valence = float(np.mean(valences)) if valences else 0.0
        avg_stress = float(np.mean(stresses)) if stresses else 0.0
        mood_variability = float(np.std(valences)) if len(valences) > 1 else 0.0

        dominant_emotion = max(emotions, key=emotions.get) if emotions else "neutral"

        stress_trend = 0.0
        if len(stresses) >= 2:
            x = np.arange(len(stresses))
            slope, _, _, _, _ = stats.linregress(x, stresses)
            stress_trend = float(slope)

        summary = await self._generate_weekly_summary(
            avg_valence, avg_stress, mood_variability, dominant_emotion,
            stress_trend, len(entries),
        )

        return {
            "daily_mood_scores": daily_mood,
            "daily_stress_scores": daily_stress,
            "emotion_distribution": emotions,
            "weekly_summary": summary,
            "stats": {
                "avg_valence": round(avg_valence, 4),
                "avg_stress": round(avg_stress, 4),
                "mood_variability": round(mood_variability, 4),
                "dominant_emotion": dominant_emotion,
                "stress_trend": round(stress_trend, 4),
                "total_entries": len(entries),
            },
        }

    async def _generate_weekly_summary(
        self,
        avg_valence: float,
        avg_stress: float,
        mood_variability: float,
        dominant_emotion: str,
        stress_trend: float,
        entry_count: int,
    ) -> str:
        client = self._get_client()

        trend_desc = "decreasing" if stress_trend < -0.01 else (
            "increasing" if stress_trend > 0.01 else "stable"
        )
        mood_desc = "positive" if avg_valence > 0.2 else (
            "negative" if avg_valence < -0.2 else "neutral"
        )

        prompt = f"""Write a brief, supportive weekly mood summary (3-4 sentences) based on these stats:
- {entry_count} mood check-ins this week
- Overall mood: {mood_desc} (valence: {avg_valence:.2f})
- Average stress level: {avg_stress:.2f}/1.0
- Mood variability: {mood_variability:.2f}
- Dominant emotion: {dominant_emotion}
- Stress trend: {trend_desc}

Be warm and encouraging. Do NOT give medical advice. Highlight positive patterns. If stress is high, gently suggest self-care."""

        response = await client.chat.completions.create(
            model=settings.llm_model,
            messages=[{"role": "user", "content": prompt}],
            max_tokens=200,
            temperature=0.7,
        )

        return response.choices[0].message.content.strip()


weekly_aggregator = WeeklyMoodAggregator()
