from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Configuration comes from the environment only; no secrets live in code."""

    model_config = SettingsConfigDict(env_prefix="CREDISYNCH_", env_file=".env", extra="ignore")

    model_dir: str = "models"
    model_version: str = "stub-0.1.0"
    log_level: str = "INFO"


@lru_cache
def get_settings() -> Settings:
    return Settings()
