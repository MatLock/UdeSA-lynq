# syntax=docker/dockerfile:1

FROM python:3.12-slim AS liquibase

ARG LIQUIBASE_VERSION=5.0.3
ARG LIQUIBASE_SHA256=865aab391be9cbe3fee22461392d5d29aa76652596598b14700a3f5f05c9e2b7
ARG MYSQL_CONNECTOR_VERSION=9.7.0
ARG MYSQL_CONNECTOR_SHA256=0353648eaa1c91e0f4020c959abf756bc866ffd583df22ae6b6f6e0cbd43eb44

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /opt/liquibase \
    && curl -fsSL --proto '=https' --proto-redir '=https' -o /tmp/liquibase.tar.gz \
        "https://github.com/liquibase/liquibase/releases/download/v${LIQUIBASE_VERSION}/liquibase-${LIQUIBASE_VERSION}.tar.gz" \
    && echo "${LIQUIBASE_SHA256}  /tmp/liquibase.tar.gz" | sha256sum -c - \
    && tar -xzf /tmp/liquibase.tar.gz -C /opt/liquibase \
    && curl -fsSL --proto '=https' --proto-redir '=https' -o "/opt/liquibase/lib/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar" \
        "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${MYSQL_CONNECTOR_VERSION}/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar" \
    && echo "${MYSQL_CONNECTOR_SHA256}  /opt/liquibase/lib/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar" | sha256sum -c - \
    && rm -rf /tmp/liquibase.tar.gz /opt/liquibase/examples /opt/liquibase/licenses \
    && chmod +x /opt/liquibase/liquibase


FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PYTHONPATH=/app/src \
    PORT=8090 \
    LIQUIBASE_HOME=/opt/liquibase

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends default-jre-headless \
    && rm -rf /var/lib/apt/lists/*

COPY --from=liquibase /opt/liquibase /opt/liquibase

COPY requirements.txt .
RUN pip install --no-cache-dir --require-hashes --only-binary :all: --no-binary langdetect -r requirements.txt

COPY src/ ./src/
COPY resources/ ./resources/
COPY changelog/ ./changelog/
COPY scripts/ ./scripts/
COPY VERSION ./

RUN useradd --create-home --uid 1000 appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8090

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8090/lynq-agent/health')"

CMD uvicorn src.main:app --host 0.0.0.0 --port ${PORT:-8090} --log-config resources/log_config.json
