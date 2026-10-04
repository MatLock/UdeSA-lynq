#!/usr/bin/env bash
set -euo pipefail

SEED_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SEED_DIR/../.." && pwd)"
PYTHON="${PYTHON:-python3.12}"

cd "$SEED_DIR"

if [ ! -x .venv/bin/python ]; then
  echo "Creating .venv with $PYTHON"
  "$PYTHON" -m venv .venv
  .venv/bin/pip install --quiet -r requirements.txt
fi

if [ -z "${LYNQ_INTERNAL_TOKEN:-}" ]; then
  LYNQ_INTERNAL_TOKEN="$(set -a; . "$REPO_ROOT/lynq-feeders/set_env.sh" >/dev/null; printf '%s' "${LYNQ_INTERNAL_TOKEN:-}")"
  export LYNQ_INTERNAL_TOKEN
fi

if [ -z "${LYNQ_SEED_PASSWORD:-}" ]; then
  read -r -s -p "Password for the seed accounts (testuserN / testcompanyN): " LYNQ_SEED_PASSWORD
  echo
  export LYNQ_SEED_PASSWORD
fi

.venv/bin/python -m seed check
.venv/bin/python -m seed load "$@"
.venv/bin/python -m seed replay
