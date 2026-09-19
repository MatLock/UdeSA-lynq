from __future__ import annotations

import os

DEFAULT_DB_URL = "mysql+aiomysql://root:root@localhost:3306/lynq_agent_db"


def _int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except ValueError:
        return default


def _bool(name: str, default: bool) -> bool:
    return os.getenv(name, str(default)).strip().lower() == "true"


class Settings:

    def __init__(self) -> None:
        self.db_url: str = os.getenv("DB_URL", DEFAULT_DB_URL)
        self.db_echo: bool = _bool("DB_ECHO", False)
        self.db_migrate_on_startup: bool = _bool("DB_MIGRATE_ON_STARTUP", True)

        self.housekeeping_enabled: bool = _bool("AGENT_HOUSEKEEPING_ENABLED", True)
        self.housekeeping_interval_seconds: int = _int(
            "AGENT_HOUSEKEEPING_INTERVAL", 3600
        )
        self.abandon_after_days: int = _int("AGENT_ABANDON_AFTER_DAYS", 7)
        self.trace_ttl_days: int = _int("AGENT_TRACE_TTL_DAYS", 30)
        self.conversation_ttl_days: int = _int("AGENT_CONVERSATION_TTL_DAYS", 180)


_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings


def reset_settings() -> None:
    global _settings
    _settings = None
