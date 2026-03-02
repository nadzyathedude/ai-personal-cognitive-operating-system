import logging

logger = logging.getLogger(__name__)

EMOTION_VALENCE = {
    "joy": 0.9, "love": 0.85, "surprise": 0.4, "neutral": 0.0,
    "sadness": -0.7, "anger": -0.8, "fear": -0.75, "disgust": -0.6,
}

EMOTION_AROUSAL = {
    "joy": 0.7, "love": 0.5, "surprise": 0.8, "neutral": 0.1,
    "sadness": -0.3, "anger": 0.9, "fear": 0.85, "disgust": 0.3,
}

TONE_LABELS = {
    (True, True): "energized-positive",
    (True, False): "calm-positive",
    (False, True): "agitated-negative",
    (False, False): "subdued-negative",
}


class EmotionFusionEngine:
    """Fuse text-based and acoustic-based emotion analysis results."""

    def __init__(self, text_weight: float = 0.55, acoustic_weight: float = 0.45):
        self.text_weight = text_weight
        self.acoustic_weight = acoustic_weight

    def fuse(self, text_result: dict, acoustic_result: dict) -> dict:
        text_emotion = text_result.get("primary_emotion", "neutral")
        text_valence = text_result.get("valence", 0.0)
        text_arousal = text_result.get("arousal", 0.0)
        text_confidence = text_result.get("confidence", 0.5)

        acoustic_emotion = acoustic_result.get("detected_emotion", "neutral")
        acoustic_valence = acoustic_result.get("valence_estimate", 0.0)
        acoustic_arousal = acoustic_result.get("arousal", 0.0)
        acoustic_stress = acoustic_result.get("vocal_stress_score", 0.0)
        acoustic_confidence = acoustic_result.get("confidence", 0.5)
        tone_descriptor = acoustic_result.get("tone_descriptor", "indeterminate")

        tw = self.text_weight * text_confidence
        aw = self.acoustic_weight * acoustic_confidence
        total_w = tw + aw + 1e-10

        final_valence = (tw * text_valence + aw * acoustic_valence) / total_w
        arousal_level = (tw * text_arousal + aw * acoustic_arousal) / total_w
        stress_index = (tw * (1 - (text_valence + 1) / 2) + aw * acoustic_stress) / total_w

        primary_emotion = self._select_primary_emotion(
            text_emotion, acoustic_emotion, text_confidence, acoustic_confidence
        )

        mismatch = self._detect_mismatch(text_result, acoustic_result)
        fusion_confidence = self._compute_fusion_confidence(
            text_confidence, acoustic_confidence, mismatch
        )

        is_positive = final_valence > 0
        is_high_arousal = arousal_level > 0.4
        composite_tone = TONE_LABELS.get((is_positive, is_high_arousal), "neutral")

        result = {
            "primary_emotion": primary_emotion,
            "final_valence": round(float(final_valence), 4),
            "stress_index": round(float(max(0, min(1, stress_index))), 4),
            "arousal_level": round(float(arousal_level), 4),
            "tone_descriptor": tone_descriptor,
            "composite_tone": composite_tone,
            "fusion_confidence": round(float(fusion_confidence), 4),
            "mismatch_detected": mismatch["detected"],
            "text_emotion": text_emotion,
            "acoustic_emotion": acoustic_emotion,
        }

        if mismatch["detected"]:
            result["mismatch_type"] = mismatch["type"]
            result["reflective_prompt"] = mismatch["reflective_prompt"]

        return result

    def _select_primary_emotion(
        self, text_emotion: str, acoustic_emotion: str,
        text_conf: float, acoustic_conf: float
    ) -> str:
        if text_emotion == acoustic_emotion:
            return text_emotion

        if text_conf > acoustic_conf * 1.5:
            return text_emotion
        if acoustic_conf > text_conf * 1.5:
            return acoustic_emotion

        text_score = EMOTION_VALENCE.get(text_emotion, 0) * text_conf
        acoustic_score = EMOTION_VALENCE.get(acoustic_emotion, 0) * acoustic_conf

        if abs(text_score) >= abs(acoustic_score):
            return text_emotion
        return acoustic_emotion

    def _detect_mismatch(self, text_result: dict, acoustic_result: dict) -> dict:
        text_valence = text_result.get("valence", 0.0)
        acoustic_valence = acoustic_result.get("valence_estimate", 0.0)
        acoustic_stress = acoustic_result.get("vocal_stress_score", 0.0)
        text_emotion = text_result.get("primary_emotion", "neutral")

        positive_text_stressed_voice = (
            text_valence > 0.3 and acoustic_stress > 0.5
        )

        valence_contradiction = (
            (text_valence > 0.3 and acoustic_valence < -0.3) or
            (text_valence < -0.3 and acoustic_valence > 0.3)
        )

        if positive_text_stressed_voice:
            return {
                "detected": True,
                "type": "positive_words_stressed_tone",
                "reflective_prompt": (
                    "You mentioned things are going well, but your voice "
                    "sounds a bit tense. Would you like to share more about "
                    "how you're really feeling?"
                ),
            }

        if valence_contradiction:
            if text_valence > 0:
                return {
                    "detected": True,
                    "type": "valence_contradiction",
                    "reflective_prompt": (
                        "Your words sound positive, but your tone suggests "
                        "something different. It's okay to feel mixed emotions — "
                        "would you like to explore that?"
                    ),
                }
            else:
                return {
                    "detected": True,
                    "type": "valence_contradiction",
                    "reflective_prompt": (
                        "It sounds like there might be more to how you're feeling "
                        "than what you've shared. Would you like to talk about it?"
                    ),
                }

        return {"detected": False, "type": None, "reflective_prompt": None}

    def _compute_fusion_confidence(
        self, text_conf: float, acoustic_conf: float, mismatch: dict
    ) -> float:
        base = (text_conf + acoustic_conf) / 2
        if mismatch["detected"]:
            base *= 0.7
        return max(0.0, min(1.0, base))


emotion_fusion_engine = EmotionFusionEngine()
