import io
import logging

import numpy as np

logger = logging.getLogger(__name__)

try:
    import librosa
    HAS_LIBROSA = True
except ImportError:
    HAS_LIBROSA = False
    logger.warning("librosa not installed, prosody analysis will use fallback")


class ProsodyFeatureExtractor:
    """Extract comprehensive prosodic features from audio for emotion detection."""

    def __init__(self, sr: int = 16000):
        self.sr = sr

    def extract(self, audio_bytes: bytes) -> dict:
        if not HAS_LIBROSA:
            return self._fallback_features()

        buffer = io.BytesIO(audio_bytes)
        y, sr = librosa.load(buffer, sr=self.sr, mono=True)

        if len(y) < sr * 0.5:
            raise ValueError("Audio too short for prosody extraction")

        pitch = self._extract_pitch(y, sr)
        energy = self._extract_energy(y)
        mfcc = self._extract_mfcc(y, sr, n_mfcc=40)
        spectral = self._extract_spectral(y, sr)
        temporal = self._extract_temporal(y, sr)
        perturbation = self._extract_perturbation(y, sr, pitch["raw_pitch"])
        tremor = self._extract_tremor(y, sr, pitch["raw_pitch"])

        return {
            **pitch,
            **energy,
            **mfcc,
            **spectral,
            **temporal,
            **perturbation,
            **tremor,
        }

    def _extract_pitch(self, y: np.ndarray, sr: int) -> dict:
        f0, voiced_flag, _ = librosa.pyin(y, fmin=50, fmax=500, sr=sr, frame_length=2048)
        f0_clean = f0[~np.isnan(f0)] if f0 is not None else np.array([])

        if len(f0_clean) < 2:
            return {
                "f0_mean": 0.0, "f0_std": 0.0, "f0_min": 0.0, "f0_max": 0.0,
                "f0_range": 0.0, "f0_slope": 0.0, "f0_variability": 0.0,
                "voiced_fraction": 0.0, "raw_pitch": f0_clean,
            }

        voiced = voiced_flag[~np.isnan(f0)] if voiced_flag is not None else np.array([])
        voiced_frac = float(np.mean(voiced)) if len(voiced) > 0 else 0.0

        indices = np.arange(len(f0_clean))
        slope = float(np.polyfit(indices, f0_clean, 1)[0]) if len(f0_clean) > 2 else 0.0

        f0_diff = np.abs(np.diff(f0_clean))
        variability = float(np.mean(f0_diff) / (np.mean(f0_clean) + 1e-10))

        return {
            "f0_mean": float(np.mean(f0_clean)),
            "f0_std": float(np.std(f0_clean)),
            "f0_min": float(np.min(f0_clean)),
            "f0_max": float(np.max(f0_clean)),
            "f0_range": float(np.ptp(f0_clean)),
            "f0_slope": slope,
            "f0_variability": variability,
            "voiced_fraction": voiced_frac,
            "raw_pitch": f0_clean,
        }

    def _extract_energy(self, y: np.ndarray) -> dict:
        rms = librosa.feature.rms(y=y, frame_length=2048, hop_length=512)[0]
        rms_db = librosa.amplitude_to_db(rms + 1e-10)

        rms_diff = np.diff(rms)
        energy_contour_slope = float(np.mean(rms_diff)) if len(rms_diff) > 0 else 0.0

        return {
            "rms_mean": float(np.mean(rms)),
            "rms_std": float(np.std(rms)),
            "rms_max": float(np.max(rms)),
            "rms_db_mean": float(np.mean(rms_db)),
            "rms_db_range": float(np.ptp(rms_db)),
            "energy_contour_slope": energy_contour_slope,
        }

    def _extract_mfcc(self, y: np.ndarray, sr: int, n_mfcc: int = 40) -> dict:
        mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=n_mfcc, n_fft=2048, hop_length=512)
        delta_mfcc = librosa.feature.delta(mfcc)
        delta2_mfcc = librosa.feature.delta(mfcc, order=2)

        return {
            "mfcc_means": [float(np.mean(c)) for c in mfcc],
            "mfcc_stds": [float(np.std(c)) for c in mfcc],
            "delta_mfcc_means": [float(np.mean(c)) for c in delta_mfcc[:13]],
            "delta2_mfcc_means": [float(np.mean(c)) for c in delta2_mfcc[:13]],
        }

    def _extract_spectral(self, y: np.ndarray, sr: int) -> dict:
        centroid = librosa.feature.spectral_centroid(y=y, sr=sr, n_fft=2048, hop_length=512)[0]
        bandwidth = librosa.feature.spectral_bandwidth(y=y, sr=sr, n_fft=2048, hop_length=512)[0]
        rolloff = librosa.feature.spectral_rolloff(y=y, sr=sr, n_fft=2048, hop_length=512, roll_percent=0.85)[0]
        contrast = librosa.feature.spectral_contrast(y=y, sr=sr, n_fft=2048, hop_length=512)
        flatness = librosa.feature.spectral_flatness(y=y, n_fft=2048, hop_length=512)[0]

        return {
            "spectral_centroid_mean": float(np.mean(centroid)),
            "spectral_centroid_std": float(np.std(centroid)),
            "spectral_bandwidth_mean": float(np.mean(bandwidth)),
            "spectral_rolloff_mean": float(np.mean(rolloff)),
            "spectral_contrast_mean": float(np.mean(contrast)),
            "spectral_flatness_mean": float(np.mean(flatness)),
        }

    def _extract_temporal(self, y: np.ndarray, sr: int) -> dict:
        zcr = librosa.feature.zero_crossing_rate(y, frame_length=2048, hop_length=512)[0]

        frame_len = int(0.025 * sr)
        hop_len = int(0.010 * sr)
        if len(y) > frame_len:
            frames = librosa.util.frame(y, frame_length=frame_len, hop_length=hop_len)
            frame_energies = np.sum(frames**2, axis=0)
            threshold = np.percentile(frame_energies, 25)
            voiced_frames = frame_energies > threshold
            transitions = np.sum(np.abs(np.diff(voiced_frames.astype(int))))
            speech_rate = transitions / (len(y) / sr)

            voiced_segments = np.sum(voiced_frames)
            total_frames = len(voiced_frames)
            articulation_rate = voiced_segments / (total_frames + 1e-10)
        else:
            speech_rate = 0.0
            articulation_rate = 0.0

        duration = len(y) / sr

        return {
            "zcr_mean": float(np.mean(zcr)),
            "zcr_std": float(np.std(zcr)),
            "speech_rate": float(speech_rate),
            "articulation_rate": float(articulation_rate),
            "duration_seconds": float(duration),
        }

    def _extract_perturbation(self, y: np.ndarray, sr: int, f0_clean: np.ndarray) -> dict:
        if len(f0_clean) > 2:
            periods = 1.0 / (f0_clean + 1e-10)
            jitter_abs = float(np.mean(np.abs(np.diff(periods))))
            jitter_rel = jitter_abs / (np.mean(periods) + 1e-10)
            jitter_rap = float(np.mean(np.abs(
                periods[1:-1] - (periods[:-2] + periods[1:-1] + periods[2:]) / 3
            )) / (np.mean(periods) + 1e-10))
        else:
            jitter_abs = 0.0
            jitter_rel = 0.0
            jitter_rap = 0.0

        rms = librosa.feature.rms(y=y, frame_length=2048, hop_length=512)[0]
        if len(rms) > 2:
            shimmer_abs = float(np.mean(np.abs(np.diff(rms))))
            shimmer_rel = shimmer_abs / (np.mean(rms) + 1e-10)
            shimmer_apq3 = float(np.mean(np.abs(
                rms[1:-1] - (rms[:-2] + rms[1:-1] + rms[2:]) / 3
            )) / (np.mean(rms) + 1e-10))
        else:
            shimmer_abs = 0.0
            shimmer_rel = 0.0
            shimmer_apq3 = 0.0

        hnr = self._estimate_hnr(y, sr)

        return {
            "jitter_abs": float(jitter_abs),
            "jitter_rel": float(jitter_rel),
            "jitter_rap": float(jitter_rap),
            "shimmer_abs": float(shimmer_abs),
            "shimmer_rel": float(shimmer_rel),
            "shimmer_apq3": float(shimmer_apq3),
            "hnr": float(hnr),
        }

    def _extract_tremor(self, y: np.ndarray, sr: int, f0_clean: np.ndarray) -> dict:
        if len(f0_clean) < 20:
            return {"tremor_frequency": 0.0, "tremor_intensity": 0.0, "tremor_index": 0.0}

        f0_detrended = f0_clean - np.mean(f0_clean)

        from scipy.signal import welch
        freqs, psd = welch(f0_detrended, fs=sr / 512, nperseg=min(len(f0_detrended), 64))

        tremor_band = (freqs >= 3.0) & (freqs <= 12.0)
        if np.any(tremor_band):
            tremor_power = float(np.max(psd[tremor_band]))
            tremor_freq_idx = np.argmax(psd[tremor_band])
            tremor_frequency = float(freqs[tremor_band][tremor_freq_idx])
            total_power = float(np.sum(psd)) + 1e-10
            tremor_intensity = tremor_power / total_power
        else:
            tremor_frequency = 0.0
            tremor_intensity = 0.0

        tremor_index = float(np.std(f0_detrended) / (np.mean(f0_clean) + 1e-10))

        return {
            "tremor_frequency": round(tremor_frequency, 4),
            "tremor_intensity": round(tremor_intensity, 4),
            "tremor_index": round(tremor_index, 4),
        }

    def _estimate_hnr(self, y: np.ndarray, sr: int) -> float:
        autocorr = np.correlate(y[:sr], y[:sr], mode="full")
        autocorr = autocorr[len(autocorr) // 2:]
        autocorr = autocorr / (autocorr[0] + 1e-10)

        min_lag = sr // 500
        max_lag = sr // 50
        if max_lag >= len(autocorr):
            max_lag = len(autocorr) - 1
        if min_lag >= max_lag:
            return 0.0

        search = autocorr[min_lag:max_lag]
        if len(search) == 0:
            return 0.0

        peak = float(np.max(search))
        if peak <= 0 or peak >= 1.0:
            return 0.0

        return float(10 * np.log10(peak / (1 - peak + 1e-10)))

    def _fallback_features(self) -> dict:
        return {
            "f0_mean": 0.0, "f0_std": 0.0, "f0_min": 0.0, "f0_max": 0.0,
            "f0_range": 0.0, "f0_slope": 0.0, "f0_variability": 0.0,
            "voiced_fraction": 0.0, "raw_pitch": np.array([]),
            "rms_mean": 0.0, "rms_std": 0.0, "rms_max": 0.0,
            "rms_db_mean": 0.0, "rms_db_range": 0.0, "energy_contour_slope": 0.0,
            "mfcc_means": [0.0] * 40, "mfcc_stds": [0.0] * 40,
            "delta_mfcc_means": [0.0] * 13, "delta2_mfcc_means": [0.0] * 13,
            "spectral_centroid_mean": 0.0, "spectral_centroid_std": 0.0,
            "spectral_bandwidth_mean": 0.0, "spectral_rolloff_mean": 0.0,
            "spectral_contrast_mean": 0.0, "spectral_flatness_mean": 0.0,
            "zcr_mean": 0.0, "zcr_std": 0.0, "speech_rate": 0.0,
            "articulation_rate": 0.0, "duration_seconds": 0.0,
            "jitter_abs": 0.0, "jitter_rel": 0.0, "jitter_rap": 0.0,
            "shimmer_abs": 0.0, "shimmer_rel": 0.0, "shimmer_apq3": 0.0,
            "hnr": 0.0,
            "tremor_frequency": 0.0, "tremor_intensity": 0.0, "tremor_index": 0.0,
        }


prosody_extractor = ProsodyFeatureExtractor()
