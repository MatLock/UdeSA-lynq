#!/usr/bin/env bash
#
# Exports the environment variables required by the lynq-agent service.
#
# Usage (must be *sourced* so the vars land in your current shell):
#
#   source ./set_env.sh

export PORT="${PORT:-8090}"

export LLM_PROVIDER="${LLM_PROVIDER:-ollama}"
export LLM_TIMEOUT="${LLM_TIMEOUT:-300}"

export OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
export OLLAMA_MODEL="${OLLAMA_MODEL:-qwen2.5:7b}"

export BEDROCK_MODEL_ID="${BEDROCK_MODEL_ID:-}"
export BEDROCK_REGION="${BEDROCK_REGION:-${AWS_REGION:-us-east-1}}"
export BEDROCK_MAX_TOKENS="${BEDROCK_MAX_TOKENS:-4096}"
export BEDROCK_TEMPERATURE="${BEDROCK_TEMPERATURE:-0}"

if [[ "$LLM_PROVIDER" == "bedrock" ]] && [[ -z "$BEDROCK_MODEL_ID" ]]; then
  echo "WARNING: LLM_PROVIDER=bedrock but BEDROCK_MODEL_ID is empty." >&2
fi

echo "lynq-agent env set: PORT=$PORT LLM_PROVIDER=$LLM_PROVIDER"
