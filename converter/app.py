"""hist-db OCR/convert sidecar — the one piece kept in Python (docling gives the best OCR).

POST /convert  (multipart: file=<pdf|epub>, force_quality=<bool>)  -> {"markdown": "...", "pages": N}
GET  /health

Fast path: pymupdf4llm (text-bearing PDFs/EPUBs). When force_quality is set (image-only scans),
falls back to docling OCR if installed. The Scala server calls this and stores the returned markdown
in Postgres — the converter itself is stateless.
"""
from __future__ import annotations

import os
import tempfile

from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import JSONResponse

app = FastAPI(title="hist-db converter")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/convert")
async def convert(file: UploadFile = File(...), force_quality: bool = Form(False)):
    suffix = os.path.splitext(file.filename or "upload.pdf")[1] or ".pdf"
    data = await file.read()
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=True) as tmp:
        tmp.write(data)
        tmp.flush()
        try:
            markdown, pages = _to_markdown(tmp.name, force_quality)
        except Exception as e:  # noqa: BLE001 — report conversion failure to the caller
            return JSONResponse(status_code=422, content={"error": f"conversion failed: {e}"})
    return {"markdown": markdown, "pages": pages, "filename": file.filename}


def _to_markdown(path: str, force_quality: bool) -> tuple[str, int]:
    if force_quality:
        try:
            from docling.document_converter import DocumentConverter  # type: ignore

            doc = DocumentConverter().convert(path).document
            return doc.export_to_markdown(), len(getattr(doc, "pages", []) or [])
        except ImportError:
            pass  # docling not installed in this image → fall through to fast path
    import pymupdf4llm  # type: ignore
    import pymupdf  # type: ignore

    md = pymupdf4llm.to_markdown(path)
    pages = pymupdf.open(path).page_count
    return md, pages
