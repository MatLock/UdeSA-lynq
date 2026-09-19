# lynq-agent

CV Tailor: the conversational agent that adapts a candidate's resume to a concrete
job posting. FastAPI + LangChain (ReAct loop), MySQL for the conversation and its
traces.

The service listens on **8090** (8089 belongs to `lynq-feeders`) and exposes two
prefixes: `/lynq-agent` for the health probe and `/lynq-agent/dmz` for the
conversation API, which is reachable only from `lynq-bff`.

## Endpoints

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/lynq-agent/health` | Liveness/readiness probe. Exempt from the request-uuid header |

The conversation API (`POST /conversation`, `POST /conversation/{id}/turn`,
`GET /conversation/{id}`, `PATCH /conversation/{id}/applied`) is not implemented yet.

Every route but the health probe requires the `lynq-request-uuid` and `user-id`
headers, and answers with the platform-wide `GlobalRestResponse` envelope.

## Running it

```bash
source ./set_env.sh
python src/main.py
```

or, from the repository root:

```bash
docker compose up lynq-agent
curl -s http://localhost:8090/lynq-agent/health
```

## Tests

```bash
coverage run -m unittest discover -s tests -t .
coverage report -m
```

## Environment

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8090` | HTTP port |
| `LLM_PROVIDER` | `ollama` | `ollama` for local development, `bedrock` in the cloud |
| `LLM_TIMEOUT` | `300` | Seconds allowed for a single LLM call |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Local Ollama endpoint |
| `OLLAMA_MODEL` | `qwen2.5:7b` | Model pulled into Ollama |
| `BEDROCK_MODEL_ID` | — | Any model id the Converse API accepts, e.g. `amazon.nova-pro-v1:0` |
| `BEDROCK_REGION` | `us-east-1` | Bedrock region |
| `BEDROCK_MAX_TOKENS` | `4096` | Cap on a single completion |
| `BEDROCK_TEMPERATURE` | `0` | Sampling temperature |

## Dependencies

`requirements.txt` is a full lock generated from `requirements.in`:

```bash
pip-compile --generate-hashes requirements.in
```
