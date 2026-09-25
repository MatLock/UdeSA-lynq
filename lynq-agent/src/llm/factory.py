from __future__ import annotations

from langchain_aws import ChatBedrockConverse
from langchain_ollama import ChatOllama

from config import BEDROCK, Settings, get_settings


def bedrock_guardrail_config(settings: Settings) -> dict[str, str] | None:
    if not settings.bedrock_guardrail_id:
        return None
    return {
        "guardrailIdentifier": settings.bedrock_guardrail_id,
        "guardrailVersion": settings.bedrock_guardrail_version,
        "trace": "enabled",
    }


def build_model(settings: Settings | None = None):
    settings = settings or get_settings()

    if settings.llm_provider == BEDROCK:
        if not settings.llm_model:
            raise ValueError("BEDROCK_MODEL_ID is required when LLM_PROVIDER=bedrock")
        return ChatBedrockConverse(
            model=settings.llm_model,
            region_name=settings.bedrock_region,
            max_tokens=settings.bedrock_max_tokens,
            temperature=settings.bedrock_temperature,
            guardrail_config=bedrock_guardrail_config(settings),
        )

    return ChatOllama(
        base_url=settings.ollama_base_url,
        model=settings.llm_model,
        temperature=0,
    )
