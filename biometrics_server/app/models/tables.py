import datetime

from sqlalchemy import Column, String, Float, Integer, DateTime, JSON, Index, Text
from sqlalchemy.dialects.sqlite import JSON as SQLiteJSON

from app.db.database import Base


class SpeakerEmbedding(Base):
    __tablename__ = "speaker_embeddings"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String(128), unique=True, nullable=False, index=True)
    embedding = Column(JSON, nullable=False)
    sample_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)


class MoodJournal(Base):
    __tablename__ = "mood_journals"

    id = Column(Integer, primary_key=True, autoincrement=True)
    session_id = Column(String(128), unique=True, nullable=False, index=True)
    user_id = Column(String(128), nullable=False, index=True)
    initial_answer = Column(Text, nullable=True)
    detected_sentiment = Column(String(32), nullable=True)
    detected_emotion = Column(String(64), nullable=True)
    emotion_confidence = Column(Float, nullable=True)
    valence = Column(Float, nullable=True)
    arousal = Column(Float, nullable=True)
    stress_score = Column(Float, nullable=True)
    stress_confidence = Column(Float, nullable=True)
    acoustic_emotion = Column(String(64), nullable=True)
    tone_descriptor = Column(String(128), nullable=True)
    vocal_stress_score = Column(Float, nullable=True)
    fusion_confidence = Column(Float, nullable=True)
    fusion_valence = Column(Float, nullable=True)
    fusion_arousal = Column(Float, nullable=True)
    fusion_stress_index = Column(Float, nullable=True)
    mismatch_detected = Column(String(64), nullable=True)
    prosody_features = Column(JSON, nullable=True)
    followup_questions = Column(JSON, nullable=True)
    followup_answers = Column(JSON, nullable=True)
    summary = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, index=True)

    __table_args__ = (
        Index("ix_mood_user_created", "user_id", "created_at"),
    )


class GarminToken(Base):
    __tablename__ = "garmin_tokens"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String(128), unique=True, nullable=False, index=True)
    oauth_token = Column(String(512), nullable=False)
    oauth_token_secret = Column(String(512), nullable=False)
    connected_at = Column(DateTime, default=datetime.datetime.utcnow)
    last_sync = Column(DateTime, nullable=True)


class GarminHrvData(Base):
    __tablename__ = "garmin_hrv_data"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String(128), nullable=False, index=True)
    hrv_value = Column(Float, nullable=True)
    resting_hr = Column(Float, nullable=True)
    sleep_score = Column(Float, nullable=True)
    stress_level = Column(Float, nullable=True)
    calendar_date = Column(String(10), nullable=True)
    recorded_at = Column(DateTime, default=datetime.datetime.utcnow)

    __table_args__ = (
        Index("ix_garmin_user_date", "user_id", "calendar_date"),
    )
