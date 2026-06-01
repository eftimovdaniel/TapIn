# TapIn — Cloud-ready image (eden servis: backend + dashboard).
#
# Razlika od backend/Dockerfile:
#   - Build context = repo root (za da gi vlece i `backend/` i `web-dashboard/`)
#   - Sluša na ${PORT} (Render/Fly/Railway go postavuvaat avtomatski)
#   - Strukturata na /app gi reflektira pateki vo repoto, taka da
#     main.py go naogja `web-dashboard` so postoecki Path resolution.

# ─── Stage 1: install Python deps ──────────────────────────────
FROM python:3.12-slim AS builder

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /build

RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential libpq-dev \
    && rm -rf /var/lib/apt/lists/*

COPY backend/requirements.txt .
RUN pip install --user --no-warn-script-location -r requirements.txt


# ─── Stage 2: slim runtime ─────────────────────────────────────
FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/home/app/.local/bin:${PATH}" \
    PORT=8080

RUN groupadd -r app && useradd -r -g app -m -d /home/app app

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
        libpq5 curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder --chown=app:app /root/.local /home/app/.local

# Bundle obata folderi — main.py ocekuva /app/web-dashboard kako sibling na /app/backend
COPY --chown=app:app backend/        ./backend/
COPY --chown=app:app web-dashboard/  ./web-dashboard/

WORKDIR /app/backend

USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -fsS "http://localhost:${PORT}/actuator/health" || exit 1

# shell-form za da $PORT se ekspandira (Render postavuva PORT=10000)
CMD uvicorn main:app --host 0.0.0.0 --port ${PORT}
