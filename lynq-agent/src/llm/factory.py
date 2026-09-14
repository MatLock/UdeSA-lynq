from __future__ import annotations

import os
from dataclasses import dataclass
from enum import Enum


class LLMProvider(str, Enum):
    OLLAMA = "ollama"
    BEDROCK = "bedrock"


DEFAULT_REGION = "us-east-1"
DEFAULT_OLLAMA_MODEL = "qwen2.5:7b"


@dataclass
class ModelHandle:
    model: object
    provider: str
    model_id: str


def selected_provider() -> LLMProvider:
    raw = os.getenv("LLM_PROVIDER", LLMProvider.OLLAMA.value).lower()
    try:
        return LLMProvider(raw)
    except ValueError as exc:
        raise ValueError(f"Unsupported LLM_PROVIDER: {raw!r}") from exc


def selected_model_id() -> str:
    if selected_provider() is LLMProvider.BEDROCK:
        model = os.getenv("BEDROCK_MODEL_ID")
        if not model:
            raise ValueError("BEDROCK_MODEL_ID is required when LLM_PROVIDER=bedrock")
        return model
    return os.getenv("OLLAMA_MODEL", DEFAULT_OLLAMA_MODEL)


def build_model() -> ModelHandle:
    provider = selected_provider()
    model_id = selected_model_id()

    if provider is LLMProvider.BEDROCK:
        from langchain_aws import ChatBedrockConverse

        model = ChatBedrockConverse(
            model=model_id,
            region_name=os.getenv("BEDROCK_REGION")
            or os.getenv("AWS_REGION")
            or DEFAULT_REGION,
            max_tokens=int(os.getenv("BEDROCK_MAX_TOKENS", "4096")),
            temperature=float(os.getenv("BEDROCK_TEMPERATURE", "0")),
        )
        return ModelHandle(model=model, provider=provider.value, model_id=model_id)

    from langchain_ollama import ChatOllama

    model = ChatOllama(
        base_url=os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"),
        model=model_id,
        temperature=0,
    )
    return ModelHandle(model=model, provider=provider.value, model_id=model_id)
