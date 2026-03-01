import io
import logging

import librosa
import numpy as np

logger = logging.getLogger(__name__)


class AntiSpoofDetector:
    """Privacy-first voice anti-spoof detection using acoustic analysis."""

    REPLAY_THRESHOLD = 0.6
    SYNTHETIC_THRESHOLD = 0.5

    def detect(self, audio_bytes: bytes) -> dict:
        try:
            buffer = io.BytesIO(audio_bytes)
            y, sr = librosa.load(buffer, sr=16000, mono=True)

            if len(y) < sr * 0.5:
                return {"is_live": False, "reason": "audio_too_short", "confidence": 1.0}

            replay_score = self._detect_replay(y, sr)
            synthetic_score = self._detect_synthetic(y, sr)
            liveness_score = 1.0 - max(replay_score, synthetic_score)

            is_live = (
                replay_score < self.REPLAY_THRESHOLD
                and synthetic_score < self.SYNTHETIC_THRESHOLD
            )

            result = {
                "is_live": is_live,
                "liveness_score": round(liveness_score, 4),
                "replay_score": round(replay_score, 4),
                "synthetic_score": round(synthetic_score, 4),
            }

            if not is_live:
                if replay_score >= self.REPLAY_THRESHOLD:
                    result["reason"] = "possible_replay_attack"
                else:
                    result["reason"] = "possible_synthetic_voice"
                logger.warning(f"Spoof detected: {result}")

            return result

        except Exception as e:
            logger.error(f"Anti-spoof detection failed: {e}")
            return {"is_live": False, "reason": "detection_error", "confidence": 0.0}

    def _detect_replay(self, y: np.ndarray, sr: int) -> float:
        """Detect replay attacks via spectral analysis."""
        # Replay artifacts: reduced high-frequency content, compression artifacts
        spectral_centroid = librosa.feature.spectral_centroid(y=y, sr=sr)[0]
        spectral_bandwidth = librosa.feature.spectral_bandwidth(y=y, sr=sr)[0]
        spectral_rolloff = librosa.feature.spectral_rolloff(y=y, sr=sr, roll_percent=0.95)[0]

        # Live speech typically has higher spectral diversity
        centroid_std = float(np.std(spectral_centroid))
        bandwidth_mean = float(np.mean(spectral_bandwidth))
        rolloff_mean = float(np.mean(spectral_rolloff))

        # Normalized scores (empirical thresholds)
        centroid_score = max(0, 1.0 - centroid_std / 500.0)
        bandwidth_score = max(0, 1.0 - bandwidth_mean / 3000.0)
        rolloff_score = max(0, 1.0 - rolloff_mean / 6000.0)

        return float(np.mean([centroid_score, bandwidth_score, rolloff_score]))

    def _detect_synthetic(self, y: np.ndarray, sr: int) -> float:
        """Detect synthetic/TTS voice via prosody analysis."""
        # Synthetic voices often have unnaturally smooth pitch contours
        pitch, voiced_flag, _ = librosa.pyin(y, fmin=50, fmax=500, sr=sr)
        pitch_clean = pitch[~np.isnan(pitch)] if pitch is not None else np.array([])

        if len(pitch_clean) < 10:
            return 0.3  # Not enough data, uncertain

        # Natural speech has more pitch variation
        pitch_std = float(np.std(pitch_clean))
        pitch_jitter = float(np.mean(np.abs(np.diff(pitch_clean))))

        # Synthetic voices tend to have lower jitter and more regular pitch
        jitter_score = max(0, 1.0 - pitch_jitter / 10.0)
        smoothness = max(0, 1.0 - pitch_std / 40.0)

        # MFCC variance: synthetic voices have less MFCC variation
        mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13)
        mfcc_var = float(np.mean(np.var(mfcc, axis=1)))
        mfcc_score = max(0, 1.0 - mfcc_var / 50.0)

        return float(np.mean([jitter_score, smoothness, mfcc_score]))


antispoof_detector = AntiSpoofDetector()
