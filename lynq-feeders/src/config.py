from __future__ import annotations

import os

DEFAULT_RUBROS = "ADMINISTRACION,TECNOLOGIA,CONTABILIDAD,RECURSOS_HUMANOS"
DEFAULT_SOURCES = "bumeran,computrabajo"
DEFAULT_SYSTEM_USER_ID = "00000000-0000-0000-0000-00000000feed"


def _int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except ValueError:
        return default


def _float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except ValueError:
        return default


def _csv(name: str, default: str) -> list[str]:
    raw = os.getenv(name) or default
    return [token.strip() for token in raw.split(",") if token.strip()]


class Settings:

    def __init__(self) -> None:
        self.ml_url: str = os.getenv(
            "LYNQ_ML_URL", "http://localhost:8084/lynq-ml"
        ).rstrip("/")
        self.backend_url: str = os.getenv(
            "LYNQ_BACKEND_URL", "http://localhost:8082/lynq-backend-app"
        ).rstrip("/")

        self.system_user_id: str = os.getenv(
            "LYNQ_FEEDERS_SYSTEM_USER_ID", DEFAULT_SYSTEM_USER_ID
        )
        self.internal_token: str = os.getenv("LYNQ_INTERNAL_TOKEN", "")

        self.jobs_per_rubro: int = _int("FEEDER_JOBS_PER_RUBRO", 10)
        self.rubros: list[str] = _csv("FEEDER_RUBROS", DEFAULT_RUBROS)
        self.sources: list[str] = _csv("FEEDER_SOURCES", DEFAULT_SOURCES)

        self.http_timeout: float = _float("HTTP_TIMEOUT", 30.0)
        self.ml_timeout: float = _float("ML_TIMEOUT", 300.0)
        self.ml_concurrency: int = _int("ML_CONCURRENCY", 2)
        self.scrape_timeout: float = _float("SCRAPE_TIMEOUT", 25.0)


def get_settings() -> Settings:
    return Settings()
