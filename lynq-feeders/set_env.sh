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
#   FEEDER_JOBS_PER_RUBRO=3 source ./set_env.sh

export HOST="${HOST:-0.0.0.0}"
export PORT="${PORT:-8089}"

export LYNQ_ML_URL="${LYNQ_ML_URL:-http://localhost:8084/lynq-ml}"
export LYNQ_BACKEND_URL="${LYNQ_BACKEND_URL:-http://localhost:8082/lynq-backend-app}"

export LYNQ_FEEDERS_SYSTEM_USER_ID="${LYNQ_FEEDERS_SYSTEM_USER_ID:-00000000-0000-0000-0000-00000000feed}"

# Shared secret checked by the lynq-app-backend ingest endpoint. Empty by
# default on purpose: a deploy without it must fail loudly on the first run,
# not post unauthenticated. Never commit a real value here — load it from
# ~/.config/mendel/credentials or the cluster Secret.
export LYNQ_INTERNAL_TOKEN="${LYNQ_INTERNAL_TOKEN:-}"

export FEEDER_JOBS_PER_RUBRO="${FEEDER_JOBS_PER_RUBRO:-10}"
export FEEDER_RUBROS="${FEEDER_RUBROS:-ADMINISTRACION,TECNOLOGIA,CONTABILIDAD,RECURSOS_HUMANOS}"
export FEEDER_SOURCES="${FEEDER_SOURCES:-bumeran,computrabajo}"

export HTTP_TIMEOUT="${HTTP_TIMEOUT:-30}"
export SCRAPE_TIMEOUT="${SCRAPE_TIMEOUT:-25}"
# Sized for the LLM leg, not a normal HTTP call: a whole run is ~80 postings
# and each one is a generation on lynq-ml.
export ML_TIMEOUT="${ML_TIMEOUT:-300}"
export ML_CONCURRENCY="${ML_CONCURRENCY:-2}"

if [[ -z "$LYNQ_INTERNAL_TOKEN" ]]; then
  echo "WARNING: LYNQ_INTERNAL_TOKEN is empty; the ingest call will be rejected by lynq-app-backend." >&2
fi

echo "lynq-feeders env set: PORT=$PORT, sources=$FEEDER_SOURCES"
