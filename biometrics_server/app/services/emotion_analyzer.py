import json
import logging

from openai import AsyncOpenAI

from app.config import settings

logger = logging.getLogger(__name__)

EMOTION_TO_VALENCE = {
    "joy": 0.9,
    "love": 0.85,
    "surprise": 0.5,
    "neutral": 0.0,
    "sadness": -0.7,
    "anger": -0.8,
    "fear": -0.75,
    "disgust": -0.6,
}

EMOTION_TO_AROUSAL = {
    "joy": 0.7,
    "love": 0.5,
    "surprise": 0.8,
    "neutral": 0.1,
    "sadness": -0.3,
    "anger": 0.9,
    "fear": 0.8,
    "disgust": 0.4,
}

EMOTION_CLASSIFICATION_PROMPT = """Classify the emotion in the following text. Return a JSON object with these fields:
- "primary_emotion": one of "joy", "sadness", "anger", "fear", "surprise", "disgust", "love", "neutral"
- "confidence": a float between 0.0 and 1.0

Analyze the emotional tone carefully. Consider implicit emotions, not just explicit keywords.
For example, "my day was fine" could be neutral, but "I had a really long exhausting day" suggests sadness/fatigue.

Text: "{text}"

Return ONLY the JSON object, nothing else."""


class EmotionAnalyzer:
    def __init__(self):
        self._classifier = None
        self._use_fallback = False
        self._openai_client: AsyncOpenAI | None = None

    def _get_openai_client(self) -> AsyncOpenAI:
        if self._openai_client is None:
            self._openai_client = AsyncOpenAI(api_key=settings.openai_api_key)
        return self._openai_client

    def _load_model(self):
        if self._classifier is None and not self._use_fallback:
            try:
                from transformers import pipeline
                logger.info("Loading emotion classification model...")
                self._classifier = pipeline(
                    "text-classification",
                    model="j-hartmann/emotion-english-distilroberta-base",
                    top_k=None,
                    device=-1,
                )
                logger.info("Emotion model loaded successfully")
            except Exception as e:
                logger.warning(f"Failed to load transformers model: {e}. Using OpenAI fallback.")
                self._use_fallback = True

    def analyze(self, text: str) -> dict:
        self._load_model()

        if self._use_fallback:
            # Will be handled by async analyze_async
            return self._analyze_keywords(text)

        results = self._classifier(text[:512])
        scores = {r["label"]: r["score"] for r in results[0]}

        primary_emotion = max(scores, key=scores.get)
        confidence = scores[primary_emotion]

        positive_emotions = {"joy", "love", "surprise"}
        negative_emotions = {"anger", "sadness", "fear", "disgust"}

        if primary_emotion in positive_emotions:
            sentiment = "positive"
        elif primary_emotion in negative_emotions:
            sentiment = "negative"
        else:
            sentiment = "neutral"

        valence = EMOTION_TO_VALENCE.get(primary_emotion, 0.0)
        arousal = EMOTION_TO_AROUSAL.get(primary_emotion, 0.0)

        return {
            "sentiment": sentiment,
            "primary_emotion": primary_emotion,
            "confidence": round(confidence, 4),
            "valence": round(valence, 4),
            "arousal": round(arousal, 4),
            "all_scores": {k: round(v, 4) for k, v in scores.items()},
        }

    async def analyze_async(self, text: str) -> dict:
        """Async version that uses OpenAI when transformers model is unavailable."""
        self._load_model()

        if not self._use_fallback:
            return self.analyze(text)

        return await self._analyze_openai(text)

    async def _analyze_openai(self, text: str) -> dict:
        """Use OpenAI GPT for emotion classification."""
        try:
            client = self._get_openai_client()
            response = await client.chat.completions.create(
                model="gpt-4o-mini",
                messages=[
                    {"role": "user", "content": EMOTION_CLASSIFICATION_PROMPT.format(text=text)},
                ],
                max_tokens=100,
                temperature=0.1,
            )
            content = response.choices[0].message.content.strip()
            # Strip markdown code block if present
            if content.startswith("```"):
                content = content.split("\n", 1)[1].rsplit("```", 1)[0].strip()

            result = json.loads(content)
            primary_emotion = result.get("primary_emotion", "neutral")
            confidence = float(result.get("confidence", 0.7))

            positive_emotions = {"joy", "love", "surprise"}
            negative_emotions = {"anger", "sadness", "fear", "disgust"}

            if primary_emotion in positive_emotions:
                sentiment = "positive"
            elif primary_emotion in negative_emotions:
                sentiment = "negative"
            else:
                sentiment = "neutral"

            valence = EMOTION_TO_VALENCE.get(primary_emotion, 0.0)
            arousal = EMOTION_TO_AROUSAL.get(primary_emotion, 0.0)

            return {
                "sentiment": sentiment,
                "primary_emotion": primary_emotion,
                "confidence": round(confidence, 4),
                "valence": round(valence, 4),
                "arousal": round(arousal, 4),
                "all_scores": {primary_emotion: round(confidence, 4)},
            }
        except Exception as e:
            logger.error(f"OpenAI emotion analysis failed: {e}")
            return self._analyze_keywords(text)

    def _analyze_keywords(self, text: str) -> dict:
        text_lower = text.lower()
        scores = {}
        for emotion, keywords in KEYWORD_EMOTIONS.items():
            score = sum(1 for kw in keywords if kw in text_lower)
            scores[emotion] = score

        total = sum(scores.values())
        if total == 0:
            primary_emotion = "neutral"
            confidence = 0.5
        else:
            primary_emotion = max(scores, key=scores.get)
            confidence = scores[primary_emotion] / max(total, 1)

        positive_emotions = {"joy", "love", "surprise"}
        negative_emotions = {"anger", "sadness", "fear", "disgust"}

        if primary_emotion in positive_emotions:
            sentiment = "positive"
        elif primary_emotion in negative_emotions:
            sentiment = "negative"
        else:
            sentiment = "neutral"

        valence = EMOTION_TO_VALENCE.get(primary_emotion, 0.0)
        arousal = EMOTION_TO_AROUSAL.get(primary_emotion, 0.0)

        return {
            "sentiment": sentiment,
            "primary_emotion": primary_emotion,
            "confidence": round(confidence, 4),
            "valence": round(valence, 4),
            "arousal": round(arousal, 4),
            "all_scores": {k: round(v / max(total, 1), 4) for k, v in scores.items()},
        }


# Keyword fallback when transformers is not installed
KEYWORD_EMOTIONS = {
    "joy": ["happy", "great", "wonderful", "fantastic", "amazing", "good", "awesome", "excellent", "love", "excited"],
    "sadness": ["sad", "down", "depressed", "unhappy", "miserable", "lonely", "heartbroken", "disappointed"],
    "anger": ["angry", "furious", "mad", "annoyed", "irritated", "frustrated", "rage", "upset"],
    "fear": ["scared", "afraid", "anxious", "worried", "nervous", "terrified", "panic", "stress", "stressed"],
    "surprise": ["surprised", "shocked", "unexpected", "wow", "amazed", "astonished"],
    "disgust": ["disgusted", "gross", "revolting", "horrible", "awful", "terrible"],
    "love": ["love", "adore", "cherish", "care", "grateful", "thankful", "blessed"],
}

emotion_analyzer = EmotionAnalyzer()
