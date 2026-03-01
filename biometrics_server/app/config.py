from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    jwt_secret: str = "your-jwt-secret-min-32-chars-long-here"
    jwt_algorithm: str = "HS256"
    database_url: str = "sqlite+aiosqlite:///./voice_assistant.db"
    cosine_similarity_threshold: float = 0.65
    verification_audio_seconds: float = 3.0
    sample_rate: int = 16000
    rate_limit_max: int = 100
    rate_limit_window_seconds: int = 60
    max_audio_bytes: int = 5 * 1024 * 1024
    stress_model_threshold: float = 0.5
    openai_api_key: str = ""
    llm_model: str = "gpt-4o"

    model_config = {"env_file": ".env", "extra": "ignore"}


settings = Settings()
