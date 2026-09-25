from __future__ import annotations

import os
from decimal import Decimal

from llm.pricing import FREE, prices_for

DEFAULT_DB_URL = "mysql+aiomysql://root:root@localhost:3306/lynq_agent_db"
DEFAULT_LYNQ_ML_URL = "http://localhost:8084/lynq-ml"
DEFAULT_OLLAMA_MODEL = "qwen2.5:7b"
DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434"
DEFAULT_BEDROCK_REGION = "us-east-1"

OLLAMA = "ollama"
BEDROCK = "bedrock"


def _int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except ValueError:
        return default


def _bool(name: str, default: bool) -> bool:
    return os.getenv(name, str(default)).strip().lower() == "true"


def _float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except ValueError:
        return default


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

        self.max_turns: int = _int("AGENT_MAX_TURNS", 10)
        self.max_steps: int = _int("AGENT_MAX_STEPS", 12)
        self.turn_timeout_seconds: int = _int("AGENT_TURN_TIMEOUT", 600)
        self.job_description_max_chars: int = _int(
            "AGENT_JOB_DESCRIPTION_MAX_CHARS", 6000
        )

        self.llm_provider: str = os.getenv("LLM_PROVIDER", OLLAMA).strip().lower()
        self.llm_model: str = (
            os.getenv("BEDROCK_MODEL_ID", "")
            if self.llm_provider == BEDROCK
            else os.getenv("OLLAMA_MODEL", DEFAULT_OLLAMA_MODEL)
        )
        self.ollama_base_url: str = os.getenv(
            "OLLAMA_BASE_URL", DEFAULT_OLLAMA_BASE_URL
        )
        self.bedrock_region: str = (
            os.getenv("BEDROCK_REGION")
            or os.getenv("AWS_REGION")
            or DEFAULT_BEDROCK_REGION
        )
        self.bedrock_max_tokens: int = _int("BEDROCK_MAX_TOKENS", 4096)
        self.bedrock_temperature: float = _float("BEDROCK_TEMPERATURE", 0.0)
        self.bedrock_guardrail_id: str = os.getenv("BEDROCK_GUARDRAIL_ID", "").strip()
        self.bedrock_guardrail_version: str = os.getenv(
            "BEDROCK_GUARDRAIL_VERSION", "DRAFT"
        ).strip()

        self.input_price_per_1m, self.output_price_per_1m = self._prices()

        self.lynq_ml_url: str = os.getenv("LYNQ_ML_URL", DEFAULT_LYNQ_ML_URL).rstrip("/")
        self.lynq_ml_timeout_seconds: int = _int("ML_TIMEOUT", 300)
        self.system_user_id: str = os.getenv("LYNQ_AGENT_SYSTEM_USER_ID", "").strip()

    def _prices(self) -> tuple[Decimal, Decimal]:
        if self.llm_provider == OLLAMA:
            return FREE
        return prices_for(self.llm_model)


_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings


def reset_settings() -> None:
    global _settings
    _settings = None
