# syntax=docker/dockerfile:1

FROM python:3.12-slim

# Keep Python lean and logs unbuffered inside the container.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=8084

WORKDIR /app

# Native libraries required by WeasyPrint (Pango/Cairo/GDK-PixBuf) plus base
# fonts so PDFs render text (including accents) correctly.
RUN apt-get update && apt-get install -y --no-install-recommends \
      libpango-1.0-0 \
      libpangocairo-1.0-0 \
      libgdk-pixbuf-2.0-0 \
      libcairo2 \
      libffi8 \
      fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

# Install dependencies first so the layer is cached across code changes.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the application source.
COPY . .

# Run as a non-root user.
RUN useradd --create-home --uid 1000 appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8084

# src/main.py reads HOST/PORT from the environment (defaults 0.0.0.0:8084).
# Running the script adds src/ to sys.path, so the top-level imports resolve.
CMD ["python", "src/main.py"]