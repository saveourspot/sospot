"""Database connection helpers for the SOSpot data pipeline."""

from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv
from sqlalchemy import Engine, create_engine
from sqlalchemy.engine import URL, make_url


PIPELINE_DIR = Path(__file__).resolve().parents[1]
PROJECT_ROOT = PIPELINE_DIR.parent


def _load_environment() -> None:
    """Load local settings without overriding explicitly supplied variables."""
    load_dotenv(PROJECT_ROOT / ".env", override=False)
    load_dotenv(PIPELINE_DIR / ".env", override=False)


def get_database_url() -> URL:
    """Return a psycopg2 SQLAlchemy URL from the configured environment."""
    _load_environment()

    if db_url := os.getenv("DB_URL"):
        url = make_url(db_url)
        if url.drivername == "postgresql":
            return url.set(drivername="postgresql+psycopg2")
        return url

    db_user = os.getenv("DB_USER")
    db_password = os.getenv("DB_PASSWORD")
    if not db_user or not db_password:
        raise RuntimeError(
            "Database configuration is missing. Set DB_URL, or both DB_USER "
            "and DB_PASSWORD, in pipeline/.env or the project .env file."
        )

    return URL.create(
        drivername="postgresql+psycopg2",
        username=db_user,
        password=db_password,
        host=os.getenv("DB_HOST", "localhost"),
        port=int(os.getenv("DB_PORT", "5432")),
        database=os.getenv("DB_NAME", "sospot"),
    )


def get_engine(**kwargs: object) -> Engine:
    """Create a SQLAlchemy engine for the SOSpot PostgreSQL database."""
    return create_engine(get_database_url(), pool_pre_ping=True, **kwargs)
