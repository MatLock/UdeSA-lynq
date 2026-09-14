# syntax=docker/dockerfile:1

FROM python:3.12-slim

# Keep Python lean and logs unbuffered inside the container.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=8090

WORKDIR /app

# Install dependencies first so the layer is cached across code changes.
# requirements.txt is a full lock (pip-compile --generate-hashes from
# requirements.in); --require-hashes makes the install reproducible.
COPY requirements.txt .
RUN pip install --no-cache-dir --require-hashes --only-binary :all: -r requirements.txt

# Copy the application source. Copy only the paths the service needs at
# runtime (avoids recursively pulling in unrelated/sensitive files).
COPY src/ ./src/
COPY resources/ ./resources/
COPY migrations/ ./migrations/
COPY alembic.ini VERSION ./

# Run as a non-root user.
RUN useradd --create-home --uid 1000 appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8090

# src/main.py reads HOST/PORT from the environment (defaults 0.0.0.0:8090).
# Running the script adds src/ to sys.path, so the top-level imports resolve.
CMD ["python", "src/main.py"]
