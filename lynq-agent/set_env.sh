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
export AGENT_MODEL_RETRIES="${AGENT_MODEL_RETRIES:-2}"
export AGENT_TURN_TIMEOUT="${AGENT_TURN_TIMEOUT:-600}"
export AGENT_JOB_DESCRIPTION_MAX_CHARS="${AGENT_JOB_DESCRIPTION_MAX_CHARS:-6000}"

export LYNQ_LLM_URL="${LYNQ_LLM_URL:-http://localhost:8084/lynq-llm}"
export LYNQ_LLM_TIMEOUT="${LYNQ_LLM_TIMEOUT:-300}"

# Every /dmz route resolves who is calling against lynq-iam from the request's
# Authorization header, and that same credential is relayed to lynq-llm.
export LYNQ_IAM_URL="${LYNQ_IAM_URL:-http://localhost:8080/lynq-iam}"
export LYNQ_IAM_TIMEOUT="${LYNQ_IAM_TIMEOUT:-10}"

export LLM_PROVIDER="${LLM_PROVIDER:-ollama}"
export LLM_TIMEOUT="${LLM_TIMEOUT:-300}"

export OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
export OLLAMA_MODEL="${OLLAMA_MODEL:-qwen2.5:7b}"
export OLLAMA_INTENT_MODEL="${OLLAMA_INTENT_MODEL:-}"
export OLLAMA_JUDGE_MODEL="${OLLAMA_JUDGE_MODEL:-}"

export BEDROCK_MODEL_ID="${BEDROCK_MODEL_ID:-}"
export BEDROCK_INTENT_MODEL_ID="${BEDROCK_INTENT_MODEL_ID:-}"
export BEDROCK_JUDGE_MODEL_ID="${BEDROCK_JUDGE_MODEL_ID:-amazon.nova-lite-v1:0}"
export BEDROCK_REGION="${BEDROCK_REGION:-${AWS_REGION:-us-east-1}}"
export BEDROCK_MAX_TOKENS="${BEDROCK_MAX_TOKENS:-4096}"
export BEDROCK_TEMPERATURE="${BEDROCK_TEMPERATURE:-0}"
export BEDROCK_GUARDRAIL_ID="${BEDROCK_GUARDRAIL_ID:-}"
export BEDROCK_GUARDRAIL_VERSION="${BEDROCK_GUARDRAIL_VERSION:-DRAFT}"

# The USD per million tokens of each model live in src/llm/pricing.py and are
# frozen onto every conversation when it is created. Ollama is billed at zero.

if [[ "$LLM_PROVIDER" == "bedrock" ]] && [[ -z "$BEDROCK_MODEL_ID" ]]; then
  echo "WARNING: LLM_PROVIDER=bedrock but BEDROCK_MODEL_ID is empty." >&2
fi

echo "lynq-agent env set: PORT=$PORT LLM_PROVIDER=$LLM_PROVIDER DB_URL=$DB_URL"
