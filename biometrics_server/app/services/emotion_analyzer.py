import logging

from transformers import pipeline

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


class EmotionAnalyzer:
    def __init__(self):
        self._classifier = None

    def _load_model(self):
        if self._classifier is None:
            logger.info("Loading emotion classification model...")
            self._classifier = pipeline(
                "text-classification",
                model="j-hartmann/emotion-english-distilroberta-base",
                top_k=None,
                device=-1,
            )
            logger.info("Emotion model loaded")

    def analyze(self, text: str) -> dict:
        self._load_model()

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


emotion_analyzer = EmotionAnalyzer()
