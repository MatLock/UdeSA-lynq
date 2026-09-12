# syntax=docker/dockerfile:1

FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=8089

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir --require-hashes --only-binary :all: -r requirements.txt

COPY src/ ./src/
COPY resources/ ./resources/
COPY VERSION ./

RUN useradd --create-home --uid 1000 appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8089

CMD ["python", "src/main.py"]
