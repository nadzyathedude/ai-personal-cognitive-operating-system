import io
import logging

import numpy as np
import torch
import torchaudio
from speechbrain.inference.speaker import EncoderClassifier

from app.config import settings

logger = logging.getLogger(__name__)


class SpeakerEmbeddingService:
    def __init__(self):
        self._model: EncoderClassifier | None = None

    def _load_model(self):
        if self._model is None:
            logger.info("Loading ECAPA-TDNN model...")
            self._model = EncoderClassifier.from_hparams(
                source="speechbrain/spkrec-ecapa-voxceleb",
                savedir="/tmp/speechbrain_ecapa",
                run_opts={"device": "cpu"},
            )
            logger.info("ECAPA-TDNN model loaded")

    def extract_embedding(self, audio_bytes: bytes) -> np.ndarray:
        self._load_model()

        buffer = io.BytesIO(audio_bytes)
        waveform, sr = torchaudio.load(buffer)

        if sr != settings.sample_rate:
            resampler = torchaudio.transforms.Resample(sr, settings.sample_rate)
            waveform = resampler(waveform)

        if waveform.shape[0] > 1:
            waveform = waveform.mean(dim=0, keepdim=True)

        with torch.no_grad():
            embedding = self._model.encode_batch(waveform)

        emb = embedding.squeeze().cpu().numpy()
        norm = np.linalg.norm(emb)
        if norm > 0:
            emb = emb / norm

        return emb

    def compute_average_embedding(self, embeddings: list[np.ndarray]) -> np.ndarray:
        stacked = np.stack(embeddings)
        avg = np.mean(stacked, axis=0)
        norm = np.linalg.norm(avg)
        if norm > 0:
            avg = avg / norm
        return avg

    @staticmethod
    def cosine_similarity(emb1: np.ndarray, emb2: np.ndarray) -> float:
        return float(np.dot(emb1, emb2))

    def verify(self, audio_bytes: bytes, stored_embedding: list[float]) -> dict:
        embedding = self.extract_embedding(audio_bytes)
        stored = np.array(stored_embedding)
        score = self.cosine_similarity(embedding, stored)
        verified = score >= settings.cosine_similarity_threshold

        return {
            "verified": verified,
            "score": round(score, 4),
            "threshold": settings.cosine_similarity_threshold,
        }


speaker_service = SpeakerEmbeddingService()
