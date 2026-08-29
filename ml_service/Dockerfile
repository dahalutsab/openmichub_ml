# ---------------------------------------------------------------------------
# Stage 1 — dependencies
#
# Wheels are built once here so the runtime image needs no compiler toolchain.
# ---------------------------------------------------------------------------
FROM python:3.12-slim AS deps

RUN apt-get update \
 && apt-get install -y --no-install-recommends build-essential libgomp1 \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /wheels
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip \
 && pip wheel --no-cache-dir --wheel-dir /wheels -r requirements.txt


# ---------------------------------------------------------------------------
# Stage 2 — runtime
# ---------------------------------------------------------------------------
FROM python:3.12-slim AS runtime

# libgomp is LightGBM's OpenMP runtime; curl is for the healthcheck.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libgomp1 curl \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 1001 mluser \
 && useradd --system --uid 1001 --gid mluser --home /app mluser

WORKDIR /app

COPY --from=deps /wheels /wheels
COPY requirements.txt .
RUN pip install --no-cache-dir --no-index --find-links=/wheels -r requirements.txt \
 && rm -rf /wheels

COPY app ./app
COPY training ./training
COPY tests ./tests

# Pre-download the embedding model so the first request does not stall on a
# network fetch, and so the container works with no outbound access.
ENV FASTEMBED_CACHE_PATH=/app/.fastembed
RUN mkdir -p /app/.fastembed /app/models \
 && python -c "from fastembed import TextEmbedding; TextEmbedding('BAAI/bge-small-en-v1.5')" \
 && chown -R 1001:1001 /app

USER mluser

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    MODEL_DIR=/app/models

VOLUME ["/app/models"]

EXPOSE 8000

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
  CMD curl -fsS http://localhost:8000/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
