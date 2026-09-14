#!/usr/bin/env bash
#
# Exports the environment variables required by the lynq-agent service.
#
# Usage (must be *sourced* so the vars land in your current shell):
#
#   source ./set_env.sh
#
# Override any value beforehand and it is respected, e.g.:
#
#   LLM_PROVIDER=bedrock BEDROCK_MODEL_ID=amazon.nova-pro-v1:0 source ./set_env.sh

# ----------------------------------------------------------------------------
# LLM provider selection: "ollama" (default) or "bedrock".
# Nova Pro is the model the ReAct loop is sized for; Nova Lite loses track of
# multi-step tool use.
# ----------------------------------------------------------------------------
export LLM_PROVIDER="${LLM_PROVIDER:-ollama}"

export OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
export OLLAMA_MODEL="${OLLAMA_MODEL:-qwen2.5:7b}"

export BEDROCK_MODEL_ID="${BEDROCK_MODEL_ID:-amazon.nova-pro-v1:0}"
export BEDROCK_REGION="${BEDROCK_REGION:-us-east-1}"
export BEDROCK_MAX_TOKENS="${BEDROCK_MAX_TOKENS:-4096}"
export BEDROCK_TEMPERATURE="${BEDROCK_TEMPERATURE:-0}"

# ----------------------------------------------------------------------------
# Database. The conversation, its messages, every resume version and the whole
# trace live here.
# ----------------------------------------------------------------------------
export DB_URL="${DB_URL:-mysql+aiomysql://root:root@localhost:3306/lynq_agent_db}"
export DB_MIGRATE_ON_STARTUP="${DB_MIGRATE_ON_STARTUP:-true}"

# Liquibase migrates the schema on startup. The image ships it under
# /opt/liquibase; outside Docker it needs java plus either LIQUIBASE_HOME or
# liquibase on the PATH. Set DB_MIGRATE_ON_STARTUP=false to skip it.
export LIQUIBASE_HOME="${LIQUIBASE_HOME:-/opt/liquibase}"

# ----------------------------------------------------------------------------
# Downstream services.
# ----------------------------------------------------------------------------
export LYNQ_ML_URL="${LYNQ_ML_URL:-http://localhost:8084/lynq-ml}"
export ML_TIMEOUT="${ML_TIMEOUT:-300}"

# ----------------------------------------------------------------------------
# The two limits. AGENT_MAX_STEPS bounds one turn's ReAct loop; AGENT_MAX_TURNS
# bounds the conversation. Both are copied onto the conversation row when it is
# created, so changing them never alters a conversation already under way.
#
# AGENT_TURN_TIMEOUT is operational instead: how long a turn may stay RUNNING
# before the next turn treats it as a dead process and takes over. Keep it above
# the gateway read timeout (310s in lynq-bff).
# ----------------------------------------------------------------------------
export AGENT_MAX_STEPS="${AGENT_MAX_STEPS:-12}"
export AGENT_MAX_TURNS="${AGENT_MAX_TURNS:-10}"
export AGENT_TURN_TIMEOUT="${AGENT_TURN_TIMEOUT:-600}"

export PORT="${PORT:-8090}"
