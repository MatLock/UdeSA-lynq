#!/usr/bin/env bash
#
# Exports the environment variables required by the lynq-feeders service.
#
# Usage (must be *sourced* so the vars land in your current shell):
#
#   source ./set_env.sh
#
# Override any value beforehand and it is respected, e.g.:
#
#   FEEDER_JOBS_PER_CATEGORY=3 source ./set_env.sh

export HOST="${HOST:-0.0.0.0}"
export PORT="${PORT:-8089}"

export LYNQ_LLM_URL="${LYNQ_LLM_URL:-http://localhost:8084/lynq-llm}"
export LYNQ_BACKEND_URL="${LYNQ_BACKEND_URL:-http://localhost:8082/lynq-backend-app}"

export LYNQ_FEEDERS_SYSTEM_USER_ID="${LYNQ_FEEDERS_SYSTEM_USER_ID:-00000000-0000-0000-0000-00000000feed}"

# Shared secret checked by the lynq-app-backend ingest endpoint. The default
# matches the one lynq-app-backend falls back to outside the production
# profile, so the local stack works with no setup. It is not a secret and is
# rejected by any real environment — never commit a real value here, load it
# from ~/.config/mendel/credentials or the cluster Secret.
export LYNQ_INTERNAL_TOKEN="${LYNQ_INTERNAL_TOKEN:-local-internal-token-not-a-secret}"

export FEEDER_JOBS_PER_CATEGORY="${FEEDER_JOBS_PER_CATEGORY:-10}"
export FEEDER_CATEGORIES="${FEEDER_CATEGORIES:-ADMINISTRACION,TECNOLOGIA,CONTABILIDAD,RECURSOS HUMANOS,JAVA,PYTHON,BACKEND,FRONTEND,ABOGACIA,MEDICINA,ENFERMERIA}"
export FEEDER_SOURCES="${FEEDER_SOURCES:-bumeran,computrabajo}"

export HTTP_TIMEOUT="${HTTP_TIMEOUT:-30}"
export SCRAPE_TIMEOUT="${SCRAPE_TIMEOUT:-25}"
# Sized for the LLM leg, not a normal HTTP call: a whole run is ~80 postings
# and each one is a generation on lynq-llm.
export LYNQ_LLM_TIMEOUT="${LYNQ_LLM_TIMEOUT:-300}"
export LYNQ_LLM_CONCURRENCY="${LYNQ_LLM_CONCURRENCY:-2}"

export VERIFY_MAX_CHECKS="${VERIFY_MAX_CHECKS:-40}"
export VERIFY_MAX_CONSECUTIVE_FAILURES="${VERIFY_MAX_CONSECUTIVE_FAILURES:-3}"

echo "lynq-feeders env set: PORT=$PORT, sources=$FEEDER_SOURCES"
