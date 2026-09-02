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
#   LLM_PROVIDER=bedrock BEDROCK_MODEL_ID=amazon.nova-pro-v1:0 source ./set_env.sh

# ----------------------------------------------------------------------------
# LLM provider selection: "ollama" (default) or "bedrock".
# ----------------------------------------------------------------------------
export LLM_PROVIDER="${LLM_PROVIDER:-ollama}"

# Shared request timeout for LLM calls, in seconds. Sized for the longest
# generation the service runs (translating a whole resume), which on a local
# Ollama model comfortably exceeds a minute.
export LLM_TIMEOUT="${LLM_TIMEOUT:-300}"

# ----------------------------------------------------------------------------
# Ollama settings (used when LLM_PROVIDER=ollama).
# ----------------------------------------------------------------------------
export OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
# Any model pulled into Ollama works: the prompts are plain text and Ollama
# applies the chat template of the model named here.
export OLLAMA_MODEL="${OLLAMA_MODEL:-qwen2.5:7b}"

# ----------------------------------------------------------------------------
# Amazon Bedrock settings (used when LLM_PROVIDER=bedrock).
#
# BEDROCK_MODEL_ID has no default and MUST be provided: it is any model id the
# Converse API accepts, so the same code serves Claude, Nova, Llama or Mistral.
#
#   anthropic.claude-sonnet-4-5-20250929-v1:0
#   amazon.nova-pro-v1:0
#   meta.llama3-3-70b-instruct-v1:0
#
# Credentials come from the standard AWS chain (env vars, ~/.aws/credentials
# profile, or the pod's IAM role) — the service never reads a key of its own.
# ----------------------------------------------------------------------------
export BEDROCK_MODEL_ID="${BEDROCK_MODEL_ID:-}"
export BEDROCK_REGION="${BEDROCK_REGION:-${AWS_REGION:-us-east-1}}"
export BEDROCK_MAX_TOKENS="${BEDROCK_MAX_TOKENS:-4096}"
export BEDROCK_TEMPERATURE="${BEDROCK_TEMPERATURE:-0}"
export BEDROCK_MAX_ATTEMPTS="${BEDROCK_MAX_ATTEMPTS:-3}"

# Warn early if Bedrock is selected without a model id.
if [[ "$LLM_PROVIDER" == "bedrock" ]] && [[ -z "$BEDROCK_MODEL_ID" ]]; then
  echo "WARNING: LLM_PROVIDER=bedrock but BEDROCK_MODEL_ID is empty." >&2
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