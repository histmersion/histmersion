# uv-based image (Astral). Reproducible install from the committed uv.lock.
FROM ghcr.io/astral-sh/uv:python3.12-bookworm-slim

WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy

# Install deps first (cached layer). --extra ocr pulls docling for high-quality OCR.
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --extra ocr

COPY app.py .

ENV HISTDB_CONVERTER_PORT=8100
EXPOSE 8100
CMD ["sh", "-c", "uv run uvicorn app:app --host 0.0.0.0 --port ${HISTDB_CONVERTER_PORT}"]
