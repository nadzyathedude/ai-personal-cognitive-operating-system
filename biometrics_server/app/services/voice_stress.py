import io
import logging

import librosa
import numpy as np
from scipy import stats

from app.config import settings

logger = logging.getLogger(__name__)


class VoiceFeatureExtractor:
    def __init__(self, sr: int = 16000):
        self.sr = sr

    def extract(self, audio_bytes: bytes) -> dict:
        buffer = io.BytesIO(audio_bytes)
        y, sr = librosa.load(buffer, sr=self.sr, mono=True)

        if len(y) < self.sr * 0.5:
            raise ValueError("Audio too short for feature extraction")

        pitch, voiced_flag, _ = librosa.pyin(
            y, fmin=50, fmax=500, sr=sr, frame_length=2048
        )
        pitch_clean = pitch[~np.isnan(pitch)] if pitch is not None else np.array([])

        rms = librosa.feature.rms(y=y, frame_length=2048, hop_length=512)[0]

        zcr = librosa.feature.zero_crossing_rate(y, frame_length=2048, hop_length=512)[0]

        mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13, n_fft=2048, hop_length=512)

        spectral_centroid = librosa.feature.spectral_centroid(
            y=y, sr=sr, n_fft=2048, hop_length=512
        )[0]

        frame_length_samples = int(0.025 * sr)
        hop_length_samples = int(0.010 * sr)
        frames = librosa.util.frame(y, frame_length=frame_length_samples, hop_length=hop_length_samples)
        frame_energies = np.sum(frames**2, axis=0)
        voiced_frames = frame_energies > np.percentile(frame_energies, 25)
        speaking_rate = np.sum(np.diff(voiced_frames.astype(int)) > 0) / (len(y) / sr)

        if len(pitch_clean) > 1:
            pitch_periods = 1.0 / (pitch_clean + 1e-10)
            jitter = np.mean(np.abs(np.diff(pitch_periods))) / (np.mean(pitch_periods) + 1e-10)
        else:
            jitter = 0.0

        if len(rms) > 1:
            shimmer = np.mean(np.abs(np.diff(rms))) / (np.mean(rms) + 1e-10)
        else:
            shimmer = 0.0

        return {
            "pitch_mean": float(np.mean(pitch_clean)) if len(pitch_clean) > 0 else 0.0,
            "pitch_std": float(np.std(pitch_clean)) if len(pitch_clean) > 0 else 0.0,
            "pitch_range": float(np.ptp(pitch_clean)) if len(pitch_clean) > 0 else 0.0,
            "rms_mean": float(np.mean(rms)),
            "rms_std": float(np.std(rms)),
            "zcr_mean": float(np.mean(zcr)),
            "zcr_std": float(np.std(zcr)),
            "mfcc_means": [float(np.mean(c)) for c in mfcc],
            "mfcc_stds": [float(np.std(c)) for c in mfcc],
            "spectral_centroid_mean": float(np.mean(spectral_centroid)),
            "spectral_centroid_std": float(np.std(spectral_centroid)),
            "speaking_rate": float(speaking_rate),
            "jitter": float(jitter),
            "shimmer": float(shimmer),
        }


class StressModel:
    STRESS_WEIGHTS = {
        "pitch_std": 0.20,
        "pitch_range": 0.10,
        "rms_std": 0.10,
        "zcr_mean": 0.10,
        "spectral_centroid_mean": 0.10,
        "speaking_rate": 0.15,
        "jitter": 0.15,
        "shimmer": 0.10,
    }

    BASELINE = {
        "pitch_std": (20.0, 80.0),
        "pitch_range": (50.0, 200.0),
        "rms_std": (0.005, 0.05),
        "zcr_mean": (0.03, 0.15),
        "spectral_centroid_mean": (1000.0, 4000.0),
        "speaking_rate": (2.0, 8.0),
        "jitter": (0.005, 0.05),
        "shimmer": (0.02, 0.15),
    }

    def predict(self, features: dict) -> dict:
        scores = []
        weights = []

        for feat_name, weight in self.STRESS_WEIGHTS.items():
            value = features.get(feat_name, 0.0)
            low, high = self.BASELINE[feat_name]

            normalized = (value - low) / (high - low + 1e-10)
            normalized = max(0.0, min(1.0, normalized))

            scores.append(normalized)
            weights.append(weight)

        stress_score = sum(s * w for s, w in zip(scores, weights)) / sum(weights)
        stress_score = max(0.0, min(1.0, stress_score))

        confidence = 1.0 - np.std(scores) if len(scores) > 1 else 0.5

        return {
            "stress_score": round(stress_score, 4),
            "confidence": round(max(0.0, min(1.0, confidence)), 4),
        }


class HybridEmotionCombiner:
    def combine(
        self,
        text_emotion: dict,
        acoustic_stress: dict,
        text_weight: float = 0.6,
        acoustic_weight: float = 0.4,
    ) -> dict:
        text_valence = text_emotion.get("valence", 0.0)
        text_arousal = text_emotion.get("arousal", 0.0)
        stress = acoustic_stress.get("stress_score", 0.0)

        acoustic_valence = -(stress * 2 - 1)

        combined_valence = text_weight * text_valence + acoustic_weight * acoustic_valence
        combined_stress = text_weight * (1 - (text_valence + 1) / 2) + acoustic_weight * stress

        return {
            "combined_valence": round(combined_valence, 4),
            "combined_stress": round(max(0.0, min(1.0, combined_stress)), 4),
            "text_emotion": text_emotion,
            "acoustic_stress": acoustic_stress,
        }


feature_extractor = VoiceFeatureExtractor(sr=settings.sample_rate)
stress_model = StressModel()
hybrid_combiner = HybridEmotionCombiner()
