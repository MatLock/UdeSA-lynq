#!/usr/bin/env bash
#
# Exports the environment variables required by the lynq-agent service.
#
# Usage (must be *sourced* so the vars land in your current shell):
#
#   source ./set_env.sh

export PORT="${PORT:-8090}"

export DB_URL="${DB_URL:-mysql+aiomysql://root:root@localhost:3306/lynq_agent_db}"
export DB_ECHO="${DB_ECHO:-false}"
export DB_MIGRATE_ON_STARTUP="${DB_MIGRATE_ON_STARTUP:-true}"

export AGENT_HOUSEKEEPING_ENABLED="${AGENT_HOUSEKEEPING_ENABLED:-true}"
export AGENT_HOUSEKEEPING_INTERVAL="${AGENT_HOUSEKEEPING_INTERVAL:-3600}"
export AGENT_ABANDON_AFTER_DAYS="${AGENT_ABANDON_AFTER_DAYS:-7}"
export AGENT_TRACE_TTL_DAYS="${AGENT_TRACE_TTL_DAYS:-30}"
export AGENT_CONVERSATION_TTL_DAYS="${AGENT_CONVERSATION_TTL_DAYS:-180}"

export AGENT_MAX_TURNS="${AGENT_MAX_TURNS:-10}"
export AGENT_MAX_STEPS="${AGENT_MAX_STEPS:-12}"
export AGENT_TURN_TIMEOUT="${AGENT_TURN_TIMEOUT:-600}"
export AGENT_JOB_DESCRIPTION_MAX_CHARS="${AGENT_JOB_DESCRIPTION_MAX_CHARS:-6000}"

export LYNQ_ML_URL="${LYNQ_ML_URL:-http://localhost:8084/lynq-ml}"
export ML_TIMEOUT="${ML_TIMEOUT:-300}"
export LYNQ_AGENT_SYSTEM_USER_ID="${LYNQ_AGENT_SYSTEM_USER_ID:-00000000-0000-0000-0000-0000000a6e17}"

export LLM_PROVIDER="${LLM_PROVIDER:-ollama}"
export LLM_TIMEOUT="${LLM_TIMEOUT:-300}"

export OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
export OLLAMA_MODEL="${OLLAMA_MODEL:-qwen2.5:7b}"

export BEDROCK_MODEL_ID="${BEDROCK_MODEL_ID:-}"
export BEDROCK_REGION="${BEDROCK_REGION:-${AWS_REGION:-us-east-1}}"
export BEDROCK_MAX_TOKENS="${BEDROCK_MAX_TOKENS:-4096}"
export BEDROCK_TEMPERATURE="${BEDROCK_TEMPERATURE:-0}"

# USD per million tokens, frozen onto every conversation when it is created.
# Forced to zero while LLM_PROVIDER=ollama, which is free.
export AGENT_INPUT_PRICE_PER_1M="${AGENT_INPUT_PRICE_PER_1M:-0.8}"
export AGENT_OUTPUT_PRICE_PER_1M="${AGENT_OUTPUT_PRICE_PER_1M:-3.2}"

if [[ "$LLM_PROVIDER" == "bedrock" ]] && [[ -z "$BEDROCK_MODEL_ID" ]]; then
  echo "WARNING: LLM_PROVIDER=bedrock but BEDROCK_MODEL_ID is empty." >&2
fi

echo "lynq-agent env set: PORT=$PORT LLM_PROVIDER=$LLM_PROVIDER DB_URL=$DB_URL"
