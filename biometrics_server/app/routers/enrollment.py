import logging

from fastapi import APIRouter, Depends, HTTPException, UploadFile, File
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.db.database import get_db
from app.models.tables import SpeakerEmbedding
from app.routers.auth import get_user_id
try:
    from app.services.speaker_embedding import speaker_service
except ImportError:
    speaker_service = None

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/enroll", tags=["enrollment"])


@router.post("")
async def enroll_speaker(
    files: list[UploadFile] = File(...),
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    if len(files) < 1:
        raise HTTPException(status_code=400, detail="At least 1 audio sample required")
    if len(files) > 10:
        raise HTTPException(status_code=400, detail="Maximum 10 audio samples")

    embeddings = []
    for f in files:
        audio_bytes = await f.read()
        if len(audio_bytes) > settings.max_audio_bytes:
            raise HTTPException(status_code=400, detail=f"File {f.filename} too large")
        if len(audio_bytes) == 0:
            raise HTTPException(status_code=400, detail=f"File {f.filename} is empty")

        try:
            emb = speaker_service.extract_embedding(audio_bytes)
            embeddings.append(emb)
        except Exception as e:
            logger.error(f"Embedding extraction failed for {f.filename}: {e}")
            raise HTTPException(
                status_code=400,
                detail=f"Failed to process {f.filename}: {str(e)}",
            )

    avg_embedding = speaker_service.compute_average_embedding(embeddings)

    result = await db.execute(
        select(SpeakerEmbedding).where(SpeakerEmbedding.user_id == user_id)
    )
    existing = result.scalar_one_or_none()

    if existing:
        existing.embedding = avg_embedding.tolist()
        existing.sample_count = len(embeddings)
    else:
        record = SpeakerEmbedding(
            user_id=user_id,
            embedding=avg_embedding.tolist(),
            sample_count=len(embeddings),
        )
        db.add(record)

    await db.commit()

    return {
        "status": "enrolled",
        "user_id": user_id,
        "sample_count": len(embeddings),
        "embedding_dim": len(avg_embedding),
    }
