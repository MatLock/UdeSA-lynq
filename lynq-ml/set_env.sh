#!/usr/bin/env bash
#
# Exports the environment variables required by the lynq-ml service.
#
# Usage (must be *sourced* so the vars land in your current shell):
#
#   source ./set_env.sh
#
# Override any value beforehand and it is respected, e.g.:
#
#   LLM_PROVIDER=openai OPENAI_API_KEY=sk-... source ./set_env.sh

# ----------------------------------------------------------------------------
# LLM provider selection: "ollama" (default) or "openai".
# ----------------------------------------------------------------------------
export LLM_PROVIDER="${LLM_PROVIDER:-ollama}"

# Shared request timeout for LLM calls, in seconds.
export LLM_TIMEOUT="${LLM_TIMEOUT:-60}"

# ----------------------------------------------------------------------------
# Ollama settings (used when LLM_PROVIDER=ollama).
# ----------------------------------------------------------------------------
export OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
export OLLAMA_MODEL="${OLLAMA_MODEL:-llama3.1}"

# ----------------------------------------------------------------------------
# OpenAI settings (used when LLM_PROVIDER=openai).
# OPENAI_API_KEY has no default and MUST be provided when using OpenAI.
# ----------------------------------------------------------------------------
export OPENAI_API_KEY="${OPENAI_API_KEY:-}"
export OPENAI_MODEL="${OPENAI_MODEL:-gpt-4o-mini}"
export OPENAI_BASE_URL="${OPENAI_BASE_URL:-https://api.openai.com/v1}"

# Warn early if OpenAI is selected without an API key.
if [ "$LLM_PROVIDER" = "openai" ] && [ -z "$OPENAI_API_KEY" ]; then
  echo "WARNING: LLM_PROVIDER=openai but OPENAI_API_KEY is empty." >&2
fi

# ----------------------------------------------------------------------------
# Course lookup for POST /upskilling_suggestion.
#
# No API key is needed: a keyless web-search provider returns real Udemy course
# links (with a search-link fallback), capped per topic. The Udemy Affiliate
# API is deprecated and is not used.
# ----------------------------------------------------------------------------
export UDEMY_MAX_COURSES="${UDEMY_MAX_COURSES:-2}"
export UDEMY_BASE_URL="${UDEMY_BASE_URL:-https://www.udemy.com}"
export COURSE_SEARCH_TIMEOUT="${COURSE_SEARCH_TIMEOUT:-15}"

echo "lynq-ml env set: LLM_PROVIDER=$LLM_PROVIDER"