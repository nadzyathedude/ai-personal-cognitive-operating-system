import logging
from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import RedirectResponse
from requests_oauthlib import OAuth1Session
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.db.database import get_db
from app.models.tables import GarminToken, GarminHrvData
from app.routers.auth import get_user_id

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/garmin", tags=["garmin"])

# Garmin OAuth 1.0a endpoints
REQUEST_TOKEN_URL = "https://connectapi.garmin.com/oauth-service/oauth/request_token"
AUTHORIZE_URL = "https://connect.garmin.com/oauthConfirm"
ACCESS_TOKEN_URL = "https://connectapi.garmin.com/oauth-service/oauth/access_token"

# In-memory store for temporary OAuth request tokens (maps token -> (secret, user_id))
_pending_tokens: dict[str, tuple[str, str]] = {}


@router.get("/connect")
async def initiate_garmin_connect(
    user_id: str = Depends(get_user_id),
):
    """Start OAuth 1.0a flow — returns authorization URL for user to visit."""
    if not settings.garmin_consumer_key or not settings.garmin_consumer_secret:
        raise HTTPException(
            status_code=503,
            detail="Garmin API credentials not configured. Set GARMIN_CONSUMER_KEY and GARMIN_CONSUMER_SECRET in .env"
        )

    callback_url = f"{settings.server_base_url}/garmin/callback"

    try:
        oauth = OAuth1Session(
            settings.garmin_consumer_key,
            client_secret=settings.garmin_consumer_secret,
            callback_uri=callback_url,
        )
        fetch_response = oauth.fetch_request_token(REQUEST_TOKEN_URL)
        resource_owner_key = fetch_response["oauth_token"]
        resource_owner_secret = fetch_response["oauth_token_secret"]

        _pending_tokens[resource_owner_key] = (resource_owner_secret, user_id)

        authorization_url = oauth.authorization_url(AUTHORIZE_URL)
        return {"authorization_url": authorization_url}

    except Exception as e:
        logger.error(f"Garmin OAuth initiation failed: {e}")
        raise HTTPException(status_code=502, detail=f"Failed to connect to Garmin: {str(e)}")


@router.get("/callback")
async def garmin_callback(
    oauth_token: str = Query(...),
    oauth_verifier: str = Query(...),
    db: AsyncSession = Depends(get_db),
):
    """Handle OAuth 1.0a callback from Garmin."""
    pending = _pending_tokens.pop(oauth_token, None)
    if pending is None:
        raise HTTPException(status_code=400, detail="Invalid or expired OAuth token")

    resource_owner_secret, user_id = pending

    try:
        oauth = OAuth1Session(
            settings.garmin_consumer_key,
            client_secret=settings.garmin_consumer_secret,
            resource_owner_key=oauth_token,
            resource_owner_secret=resource_owner_secret,
            verifier=oauth_verifier,
        )
        tokens = oauth.fetch_access_token(ACCESS_TOKEN_URL)
        access_token = tokens["oauth_token"]
        access_secret = tokens["oauth_token_secret"]

    except Exception as e:
        logger.error(f"Garmin OAuth access token failed: {e}")
        raise HTTPException(status_code=502, detail="Failed to complete Garmin authorization")

    # Save or update tokens
    result = await db.execute(
        select(GarminToken).where(GarminToken.user_id == user_id)
    )
    existing = result.scalar_one_or_none()

    if existing:
        existing.oauth_token = access_token
        existing.oauth_token_secret = access_secret
        existing.connected_at = datetime.utcnow()
    else:
        db.add(GarminToken(
            user_id=user_id,
            oauth_token=access_token,
            oauth_token_secret=access_secret,
        ))

    await db.commit()
    logger.info(f"Garmin connected for user {user_id}")

    return {"status": "connected", "message": "Garmin account linked successfully"}


@router.get("/status")
async def garmin_status(
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    """Check if user has a linked Garmin account."""
    result = await db.execute(
        select(GarminToken).where(GarminToken.user_id == user_id)
    )
    token = result.scalar_one_or_none()

    return {
        "connected": token is not None,
        "connected_at": token.connected_at.isoformat() if token else None,
        "last_sync": token.last_sync.isoformat() if token and token.last_sync else None,
    }


@router.post("/sync")
async def sync_garmin_data(
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    """Fetch latest data from Garmin Health API and store it."""
    result = await db.execute(
        select(GarminToken).where(GarminToken.user_id == user_id)
    )
    token = result.scalar_one_or_none()
    if token is None:
        raise HTTPException(status_code=404, detail="Garmin not connected")

    try:
        oauth = OAuth1Session(
            settings.garmin_consumer_key,
            client_secret=settings.garmin_consumer_secret,
            resource_owner_key=token.oauth_token,
            resource_owner_secret=token.oauth_token_secret,
        )

        # Fetch daily summaries for last 7 days
        today = datetime.utcnow()
        start_epoch = int((today - timedelta(days=7)).timestamp())
        end_epoch = int(today.timestamp())

        daily_url = (
            f"{settings.garmin_base_url}/wellness-api/rest/dailies"
            f"?uploadStartTimeInSeconds={start_epoch}&uploadEndTimeInSeconds={end_epoch}"
        )

        resp = oauth.get(daily_url)
        if resp.status_code != 200:
            logger.warning(f"Garmin daily summaries returned {resp.status_code}: {resp.text}")
            raise HTTPException(status_code=502, detail="Failed to fetch Garmin data")

        summaries = resp.json()
        synced_count = 0

        for summary in summaries:
            calendar_date = summary.get("calendarDate", "")
            hrv_value = summary.get("hrvValue")  # Not always present
            resting_hr = summary.get("restingHeartRateInBeatsPerMinute")
            stress = summary.get("averageStressLevel")

            # Check if we already have this date
            existing = await db.execute(
                select(GarminHrvData).where(
                    GarminHrvData.user_id == user_id,
                    GarminHrvData.calendar_date == calendar_date,
                )
            )
            record = existing.scalar_one_or_none()

            if record:
                record.hrv_value = hrv_value
                record.resting_hr = resting_hr
                record.stress_level = stress
                record.recorded_at = datetime.utcnow()
            else:
                db.add(GarminHrvData(
                    user_id=user_id,
                    hrv_value=hrv_value,
                    resting_hr=resting_hr,
                    stress_level=stress,
                    calendar_date=calendar_date,
                ))
            synced_count += 1

        # Try to fetch sleep data
        sleep_url = (
            f"{settings.garmin_base_url}/wellness-api/rest/epochs"
            f"?uploadStartTimeInSeconds={start_epoch}&uploadEndTimeInSeconds={end_epoch}"
        )
        # Sleep score might be in a separate endpoint - handle gracefully
        try:
            sleep_resp = oauth.get(
                f"{settings.garmin_base_url}/wellness-api/rest/sleeps"
                f"?uploadStartTimeInSeconds={start_epoch}&uploadEndTimeInSeconds={end_epoch}"
            )
            if sleep_resp.status_code == 200:
                sleeps = sleep_resp.json()
                for sleep in sleeps:
                    cal_date = sleep.get("calendarDate", "")
                    sleep_score = sleep.get("overallSleepScore")
                    if sleep_score and cal_date:
                        existing = await db.execute(
                            select(GarminHrvData).where(
                                GarminHrvData.user_id == user_id,
                                GarminHrvData.calendar_date == cal_date,
                            )
                        )
                        record = existing.scalar_one_or_none()
                        if record:
                            record.sleep_score = sleep_score
        except Exception:
            pass  # Sleep data is optional

        token.last_sync = datetime.utcnow()
        await db.commit()

        return {"synced": synced_count, "status": "ok"}

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Garmin sync failed: {e}")
        raise HTTPException(status_code=502, detail=f"Garmin sync failed: {str(e)}")


@router.get("/hrv")
async def get_hrv_data(
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    """Get latest HRV data for the user."""
    # Check connection first
    result = await db.execute(
        select(GarminToken).where(GarminToken.user_id == user_id)
    )
    token = result.scalar_one_or_none()
    if token is None:
        return {"connected": False, "data": None}

    # Get most recent HRV record
    result = await db.execute(
        select(GarminHrvData)
        .where(GarminHrvData.user_id == user_id)
        .order_by(GarminHrvData.calendar_date.desc())
        .limit(1)
    )
    latest = result.scalar_one_or_none()

    if latest is None:
        return {"connected": True, "data": None}

    return {
        "connected": True,
        "data": {
            "hrv": latest.hrv_value or 0,
            "resting_hr": latest.resting_hr or 0,
            "sleep_score": latest.sleep_score or 0,
            "stress_level": latest.stress_level or 0,
            "date": latest.calendar_date,
        },
    }


@router.get("/daily")
async def get_daily_hrv(
    days: int = Query(default=7, le=30),
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    """Get daily HRV data for the last N days."""
    result = await db.execute(
        select(GarminHrvData)
        .where(GarminHrvData.user_id == user_id)
        .order_by(GarminHrvData.calendar_date.desc())
        .limit(days)
    )
    records = result.scalars().all()

    return {
        "daily": [
            {
                "date": r.calendar_date,
                "hrv": r.hrv_value or 0,
                "resting_hr": r.resting_hr or 0,
                "sleep_score": r.sleep_score or 0,
                "stress_level": r.stress_level or 0,
            }
            for r in reversed(records)
        ]
    }


@router.delete("/disconnect")
async def disconnect_garmin(
    user_id: str = Depends(get_user_id),
    db: AsyncSession = Depends(get_db),
):
    """Remove Garmin connection for the user."""
    result = await db.execute(
        select(GarminToken).where(GarminToken.user_id == user_id)
    )
    token = result.scalar_one_or_none()
    if token:
        await db.delete(token)
        await db.commit()

    return {"status": "disconnected"}
