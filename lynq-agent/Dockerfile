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
    && curl -fsSL -o /tmp/liquibase.tar.gz \
        "https://github.com/liquibase/liquibase/releases/download/v${LIQUIBASE_VERSION}/liquibase-${LIQUIBASE_VERSION}.tar.gz" \
    && echo "${LIQUIBASE_SHA256}  /tmp/liquibase.tar.gz" | sha256sum -c - \
    && tar -xzf /tmp/liquibase.tar.gz -C /opt/liquibase \
    && curl -fsSL -o "/opt/liquibase/lib/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar" \
        "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${MYSQL_CONNECTOR_VERSION}/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar" \
    && echo "${MYSQL_CONNECTOR_SHA256}  /opt/liquibase/lib/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar" | sha256sum -c - \
    && rm -rf /tmp/liquibase.tar.gz /opt/liquibase/examples /opt/liquibase/licenses \
    && chmod +x /opt/liquibase/liquibase


FROM python:3.12-slim

# Keep Python lean and logs unbuffered inside the container.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=8090 \
    LIQUIBASE_HOME=/opt/liquibase

WORKDIR /app

# Liquibase is a Java tool, so the image needs a JRE to migrate on startup.
RUN apt-get update \
    && apt-get install -y --no-install-recommends default-jre-headless \
    && rm -rf /var/lib/apt/lists/*

COPY --from=liquibase /opt/liquibase /opt/liquibase

# Install dependencies first so the layer is cached across code changes.
# requirements.txt is a full lock (pip-compile --generate-hashes from
# requirements.in); --require-hashes makes the install reproducible.
COPY requirements.txt .
RUN pip install --no-cache-dir --require-hashes --only-binary :all: -r requirements.txt

# Copy the application source. Copy only the paths the service needs at
# runtime (avoids recursively pulling in unrelated/sensitive files).
COPY src/ ./src/
COPY resources/ ./resources/
COPY changelog/ ./changelog/
COPY VERSION ./

# Run as a non-root user.
RUN useradd --create-home --uid 1000 appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8090

# src/main.py reads HOST/PORT from the environment (defaults 0.0.0.0:8090).
# Running the script adds src/ to sys.path, so the top-level imports resolve.
CMD ["python", "src/main.py"]
