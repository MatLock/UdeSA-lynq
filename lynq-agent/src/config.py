from __future__ import annotations

import os

DEFAULT_DB_URL = "mysql+aiomysql://root:root@localhost:3306/lynq_agent_db"
DEFAULT_ML_URL = "http://localhost:8084/lynq-ml"
DEFAULT_SYSTEM_USER_ID = "00000000-0000-0000-0000-0000000a6e17"


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


class Settings:

    def __init__(self) -> None:
        self.db_url: str = os.getenv("DB_URL", DEFAULT_DB_URL)
        self.db_echo: bool = os.getenv("DB_ECHO", "false").lower() == "true"

        self.ml_url: str = os.getenv("LYNQ_ML_URL", DEFAULT_ML_URL).rstrip("/")
        self.ml_timeout: float = _float("ML_TIMEOUT", 300.0)
        self.system_user_id: str = os.getenv(
            "LYNQ_AGENT_SYSTEM_USER_ID", DEFAULT_SYSTEM_USER_ID
        )

        self.max_steps: int = _int("AGENT_MAX_STEPS", 12)
        self.max_turns: int = _int("AGENT_MAX_TURNS", 10)
        self.turn_timeout_seconds: int = _int("AGENT_TURN_TIMEOUT", 600)

        self.history_pairs: int = _int("AGENT_HISTORY_PAIRS", 4)
        self.summary_threshold: int = _int("AGENT_SUMMARY_THRESHOLD", 8)


_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings


def reset_settings() -> None:
    global _settings
    _settings = None
