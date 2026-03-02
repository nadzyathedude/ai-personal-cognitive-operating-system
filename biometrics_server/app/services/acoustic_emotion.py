import logging

import numpy as np

logger = logging.getLogger(__name__)

EMOTION_PROFILES = {
    "anger": {"f0_mean": (180, 350), "f0_variability": (0.08, 0.25), "rms_mean": (0.08, 0.3), "speech_rate": (5, 10), "spectral_centroid_mean": (2500, 5000)},
    "sadness": {"f0_mean": (80, 160), "f0_variability": (0.01, 0.06), "rms_mean": (0.01, 0.04), "speech_rate": (1, 4), "spectral_centroid_mean": (800, 2000)},
    "joy": {"f0_mean": (200, 400), "f0_variability": (0.06, 0.20), "rms_mean": (0.06, 0.2), "speech_rate": (4, 9), "spectral_centroid_mean": (2000, 4500)},
    "fear": {"f0_mean": (200, 380), "f0_variability": (0.10, 0.30), "rms_mean": (0.03, 0.12), "speech_rate": (5, 11), "spectral_centroid_mean": (2200, 4800)},
    "neutral": {"f0_mean": (100, 220), "f0_variability": (0.03, 0.10), "rms_mean": (0.03, 0.08), "speech_rate": (3, 6), "spectral_centroid_mean": (1200, 3000)},
    "surprise": {"f0_mean": (220, 450), "f0_variability": (0.12, 0.30), "rms_mean": (0.05, 0.15), "speech_rate": (3, 7), "spectral_centroid_mean": (2000, 4500)},
    "disgust": {"f0_mean": (100, 200), "f0_variability": (0.04, 0.12), "rms_mean": (0.04, 0.10), "speech_rate": (2, 5), "spectral_centroid_mean": (1000, 2500)},
}

VALENCE_MAP = {
    "joy": 0.85, "surprise": 0.4, "neutral": 0.0,
    "sadness": -0.7, "anger": -0.8, "fear": -0.75, "disgust": -0.6,
}

AROUSAL_MAP = {
    "anger": 0.9, "fear": 0.85, "joy": 0.75, "surprise": 0.8,
    "neutral": 0.15, "sadness": -0.3, "disgust": 0.3,
}

TONE_DESCRIPTORS = {
    "anger": "tense and forceful",
    "sadness": "subdued and low-energy",
    "joy": "bright and energetic",
    "fear": "shaky and high-pitched",
    "neutral": "calm and steady",
    "surprise": "heightened and breathy",
    "disgust": "constricted and monotone",
}


class AcousticEmotionModel:
    """Rule-based acoustic emotion detection from prosodic features.
    Falls back gracefully when ML models are unavailable."""

    def __init__(self):
        self._wav2vec_model = None
        self._use_fallback = True

    def _try_load_model(self):
        try:
            from transformers import pipeline
            self._wav2vec_model = pipeline(
                "audio-classification",
                model="ehcalabres/wav2vec2-lg-xlsr-en-speech-emotion-recognition",
                device=-1,
            )
            self._use_fallback = False
            logger.info("Wav2Vec2 emotion model loaded")
        except Exception:
            logger.info("Using rule-based acoustic emotion detection")
            self._use_fallback = True

    def predict(self, features: dict) -> dict:
        if self._use_fallback:
            return self._rule_based_predict(features)
        return self._model_predict(features)

    def _rule_based_predict(self, features: dict) -> dict:
        # If all features are zero (fallback/no real audio), return neutral
        key_features = ["f0_mean", "rms_mean", "spectral_centroid_mean"]
        if all(features.get(k, 0.0) == 0.0 for k in key_features):
            return {
                "detected_emotion": "neutral",
                "arousal": 0.15,
                "valence_estimate": 0.0,
                "vocal_stress_score": 0.0,
                "confidence": 0.1,
                "tone_descriptor": "indeterminate",
                "all_scores": {k: 1.0 / len(EMOTION_PROFILES) for k in EMOTION_PROFILES},
            }

        scores = {}
        for emotion, profile in EMOTION_PROFILES.items():
            emotion_score = 0.0
            count = 0
            for feat_name, (low, high) in profile.items():
                value = features.get(feat_name, 0.0)
                if isinstance(value, (list, np.ndarray)):
                    continue
                mid = (low + high) / 2
                spread = (high - low) / 2 + 1e-10
                distance = abs(value - mid) / spread
                fit = max(0.0, 1.0 - distance)
                emotion_score += fit
                count += 1
            scores[emotion] = emotion_score / max(count, 1)

        total = sum(scores.values()) + 1e-10
        probs = {k: v / total for k, v in scores.items()}

        detected = max(probs, key=probs.get)
        confidence = probs[detected]

        vocal_stress = self._compute_vocal_stress(features)

        return {
            "detected_emotion": detected,
            "arousal": round(AROUSAL_MAP.get(detected, 0.0), 4),
            "valence_estimate": round(VALENCE_MAP.get(detected, 0.0), 4),
            "vocal_stress_score": round(vocal_stress, 4),
            "confidence": round(confidence, 4),
            "tone_descriptor": TONE_DESCRIPTORS.get(detected, "indeterminate"),
            "all_scores": {k: round(v, 4) for k, v in probs.items()},
        }

    def _model_predict(self, features: dict) -> dict:
        # Placeholder for Wav2Vec2 inference path
        return self._rule_based_predict(features)

    def _compute_vocal_stress(self, features: dict) -> float:
        indicators = []

        f0_var = features.get("f0_variability", 0.0)
        indicators.append(min(1.0, f0_var / 0.15))

        jitter = features.get("jitter_rel", 0.0)
        indicators.append(min(1.0, jitter / 0.03))

        shimmer = features.get("shimmer_rel", 0.0)
        indicators.append(min(1.0, shimmer / 0.10))

        tremor = features.get("tremor_index", 0.0)
        indicators.append(min(1.0, tremor / 0.05))

        speech_rate = features.get("speech_rate", 4.0)
        rate_deviation = abs(speech_rate - 4.5) / 4.5
        indicators.append(min(1.0, rate_deviation))

        hnr = features.get("hnr", 10.0)
        hnr_stress = max(0.0, 1.0 - hnr / 20.0)
        indicators.append(hnr_stress)

        weights = [0.20, 0.20, 0.15, 0.15, 0.15, 0.15]
        stress = sum(i * w for i, w in zip(indicators, weights))
        return max(0.0, min(1.0, stress))


acoustic_emotion_model = AcousticEmotionModel()
