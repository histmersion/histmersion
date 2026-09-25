# uv-based image (Astral). Reproducible install from the committed uv.lock.
FROM ghcr.io/astral-sh/uv:python3.12-bookworm-slim

WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy

# Native libraries the OCR stack dlopen()s. pymupdf4llm decides whether to OCR a page by importing
# rapidocr, which imports cv2 (opencv), which needs libxcb/libGL/glib even headless — without them
# `to_markdown` dies with "libxcb.so.1: cannot open shared object file" on the very first document.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libxcb1 libgl1 libglib2.0-0 \
 && rm -rf /var/lib/apt/lists/*

# Install deps first (cached layer). --extra ocr pulls docling for high-quality OCR.
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --extra ocr

COPY app.py .

ENV HISTMERSION_CONVERTER_PORT=8100
EXPOSE 8100
CMD ["sh", "-c", "uv run uvicorn app:app --host 0.0.0.0 --port ${HISTMERSION_CONVERTER_PORT}"]
