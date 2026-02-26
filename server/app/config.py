"""
ZK Backup — Configuration.

All settings are read from environment variables (or .env file via docker-compose).
"""

import os
from pathlib import Path


class Settings:
    # ── Database ───────────────────────────────────────────────
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        "postgresql+asyncpg://zkbackup:zkbackup@localhost:5432/zkbackup",
    )

    # ── JWT ────────────────────────────────────────────────────
    JWT_SECRET: str = os.getenv("JWT_SECRET", "CHANGE-ME-IN-PRODUCTION")
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 15
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30

    # ── Storage ────────────────────────────────────────────────
    STORAGE_BACKEND: str = os.getenv("STORAGE_BACKEND", "local")  # "local" or "s3"
    STORAGE_PATH: str = os.getenv("STORAGE_PATH", "/data/storage")

    # S3-compatible settings (used when STORAGE_BACKEND == "s3")
    S3_ENDPOINT: str = os.getenv("S3_ENDPOINT", "")
    S3_ACCESS_KEY: str = os.getenv("S3_ACCESS_KEY", "")
    S3_SECRET_KEY: str = os.getenv("S3_SECRET_KEY", "")
    S3_BUCKET: str = os.getenv("S3_BUCKET", "zkbackup")
    S3_REGION: str = os.getenv("S3_REGION", "us-east-1")

    # ── Server ─────────────────────────────────────────────────
    API_V1_PREFIX: str = "/api/v1"
    ALLOWED_ORIGINS: list[str] = os.getenv("ALLOWED_ORIGINS", "*").split(",")

    # ── Limits ─────────────────────────────────────────────────
    MAX_CHUNK_SIZE: int = 10 * 1024 * 1024  # 10 MB (slightly above 8 MB to allow overhead)


settings = Settings()
