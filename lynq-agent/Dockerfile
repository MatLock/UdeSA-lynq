# syntax=docker/dockerfile:1

FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PYTHONPATH=/app/src \
    PORT=8090

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir --require-hashes --only-binary :all: --no-binary langdetect -r requirements.txt

COPY src/ ./src/
COPY resources/ ./resources/
COPY VERSION ./

RUN useradd --create-home --uid 1000 appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8090

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8090/lynq-agent/health')"

CMD uvicorn src.main:app --host 0.0.0.0 --port ${PORT:-8090} --log-config resources/log_config.json
