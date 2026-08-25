"""hist-db OCR/convert sidecar — the one piece kept in Python (docling gives the best OCR).

POST /convert  (multipart: file=<pdf|epub>, force_quality, dpi, max_image_px)
  -> {"markdown": "...", "pages": N, "images": [{"name", "mime", "base64", "width", "height"}]}
GET  /health   -> {"status", "docling": bool, "dpi", "maxImagePx"}

Fast path: pymupdf4llm (text-bearing PDFs/EPUBs) with `write_images=True`, so figures survive the
conversion instead of being dropped. When `force_quality` is set (image-only scans) docling runs
instead, exporting with `ImageRefMode.REFERENCED` so its pictures land on disk as files too.

Either way the image links in the returned Markdown are rewritten to the RELATIVE form
`assets/<name>`, which is exactly how the Scala server stores them (table `assets`) and serves them
(`GET /source/{citekey}/assets/{name}`) — the same Markdown therefore works on disk and on the web.
Images are deduplicated by sha256 (scans repeat the same logo on every page) and downscaled so that
neither side is larger than `max_image_px`.

The converter is STATELESS: nothing is written outside a per-request temporary directory.
"""
from __future__ import annotations

import base64
import hashlib
import io
import os
import re
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import JSONResponse

# Defaults; the Scala server sends its own (config `histdb.converter.dpi` / `max-image-px`) per call.
DEFAULT_DPI = int(os.environ.get("HISTDB_CONVERTER_DPI", "150"))
DEFAULT_MAX_IMAGE_PX = int(os.environ.get("HISTDB_CONVERTER_MAX_IMAGE_PX", "2000"))

IMAGE_SUFFIXES = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".webp": "image/webp"}

# The upload is streamed to the temporary file in blocks of this size — a scanned book runs to
# hundreds of MB, and holding a whole one in memory per concurrent request is what OOMs the sidecar.
UPLOAD_CHUNK_BYTES = 1024 * 1024

app = FastAPI(title="hist-db converter")


def _docling_available() -> bool:
    try:
        import docling.document_converter  # type: ignore  # noqa: F401

        return True
    except Exception:  # noqa: BLE001 — any import-time failure means "not usable here"
        return False


@app.get("/health")
def health():
    return {
        "status": "ok",
        "docling": _docling_available(),
        "dpi": DEFAULT_DPI,
        "maxImagePx": DEFAULT_MAX_IMAGE_PX,
    }


@app.post("/convert")
async def convert(
    file: UploadFile = File(...),
    force_quality: bool = Form(False),
    dpi: int | None = Form(None),
    max_image_px: int | None = Form(None),
):
    suffix = os.path.splitext(file.filename or "upload.pdf")[1] or ".pdf"
    use_dpi = dpi if dpi and dpi > 0 else DEFAULT_DPI
    use_max = max_image_px if max_image_px and max_image_px > 0 else DEFAULT_MAX_IMAGE_PX
    with tempfile.TemporaryDirectory() as work:
        src = Path(work) / f"upload{suffix}"
        with src.open("wb") as out:
            while chunk := await file.read(UPLOAD_CHUNK_BYTES):
                out.write(chunk)
        try:
            markdown, pages, images = _to_markdown(str(src), Path(work), force_quality, use_dpi, use_max)
        except Exception as e:  # noqa: BLE001 — report conversion failure to the caller
            return JSONResponse(status_code=422, content={"error": f"conversion failed: {e}"})
    return {"markdown": markdown, "pages": pages, "images": images, "filename": file.filename}


# ── conversion ────────────────────────────────────────────────────────────────────────────────────
def _to_markdown(
    path: str, work: Path, force_quality: bool, dpi: int, max_px: int
) -> tuple[str, int, list[dict]]:
    if force_quality:
        quality = _docling(path, work, dpi, max_px)
        if quality is not None:
            return quality
        # docling not installed in this image → fall through to the fast path
    return _pymupdf(path, work, dpi, max_px)


def _pymupdf(path: str, work: Path, dpi: int, max_px: int) -> tuple[str, int, list[dict]]:
    import pymupdf  # type: ignore
    import pymupdf4llm  # type: ignore

    imgdir = work / "images"
    imgdir.mkdir(exist_ok=True)
    md = pymupdf4llm.to_markdown(
        path, write_images=True, image_path=str(imgdir), image_format="png", dpi=dpi
    )
    # `with` so the document (and its mmap of the file) is released before the temp dir is removed
    with pymupdf.open(path) as doc:
        pages = doc.page_count
    return _finish(md, imgdir, max_px, pages, stem=_safe_name(Path(path).name))


def _docling(path: str, work: Path, dpi: int, max_px: int) -> tuple[str, int, list[dict]] | None:
    """docling with REFERENCED images: pictures are written next to the Markdown, not inlined."""
    try:
        from docling.datamodel.base_models import InputFormat  # type: ignore
        from docling.datamodel.pipeline_options import PdfPipelineOptions  # type: ignore
        from docling.document_converter import DocumentConverter, PdfFormatOption  # type: ignore
        from docling_core.types.doc import ImageRefMode  # type: ignore
    except ImportError:
        return None

    opts = PdfPipelineOptions()
    opts.generate_picture_images = True
    opts.images_scale = max(1.0, dpi / 72.0)  # docling scales relative to 72 dpi
    conv = DocumentConverter(format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=opts)})
    doc = conv.convert(path).document
    out = work / "docling"
    out.mkdir(exist_ok=True)
    target = out / "doc.md"
    doc.save_as_markdown(target, image_mode=ImageRefMode.REFERENCED)
    md = target.read_text(encoding="utf-8")
    pages = len(getattr(doc, "pages", []) or [])
    return _finish(md, out, max_px, pages)


# ── images ────────────────────────────────────────────────────────────────────────────────────────
IMG_LINK = re.compile(r"!\[([^\]]*)\]\(\s*<?([^)>\s]+)>?(\s+\"[^\"]*\")?\s*\)")


def _finish(md: str, imgdir: Path, max_px: int, pages: int, stem: str = "") -> tuple[str, int, list[dict]]:
    images, by_basename = _collect(imgdir, max_px, stem)
    return _rewrite(md, by_basename), pages, images


def _asset_name(name: str, stem: str) -> str:
    """`upload.pdf-0001-05.png` -> `fig-0001-05.png`.

    pymupdf4llm names an extracted image after the INPUT FILE (`<input>-<page>-<n>.<ext>`), and the
    input file here is a temporary copy — so without this the stored asset (and its public URL)
    would carry a meaningless `upload.pdf-` prefix forever. The Markdown links are rewritten through
    the same mapping, so renaming is safe.
    """
    safe = _safe_name(name)
    if stem and safe.startswith(stem + "-"):
        return "fig-" + safe[len(stem) + 1 :]
    return safe


def _collect(imgdir: Path, max_px: int, stem: str = "") -> tuple[list[dict], dict[str, str]]:
    """Every image file under `imgdir`, deduplicated by sha256 and downscaled to `max_px`.

    Returns the asset list plus a map from each ORIGINAL file's basename to the asset name it ended
    up as, which is what the Markdown links are rewritten through.
    """
    images: list[dict] = []
    by_sha: dict[str, str] = {}
    by_basename: dict[str, str] = {}
    taken: set[str] = set()
    if not imgdir.is_dir():
        return images, by_basename
    for f in sorted(p for p in imgdir.rglob("*") if p.is_file() and p.suffix.lower() in IMAGE_SUFFIXES):
        raw = f.read_bytes()
        if not raw:
            continue
        raw, width, height, mime = _normalise(raw, f.suffix.lower(), max_px)
        sha = hashlib.sha256(raw).hexdigest()
        if sha in by_sha:  # the same picture on twenty pages is stored once
            by_basename[f.name] = by_sha[sha]
            continue
        name = _unique(_asset_name(f.name, stem), taken)
        taken.add(name)
        by_sha[sha] = name
        by_basename[f.name] = name
        images.append(
            {
                "name": name,
                "mime": mime,
                "base64": base64.b64encode(raw).decode("ascii"),
                "width": width,
                "height": height,
                "sha256": sha,
            }
        )
    return images, by_basename


def _normalise(raw: bytes, suffix: str, max_px: int) -> tuple[bytes, int, int, str]:
    """Downscale to `max_px` on the longest side; report the final dimensions and mime type."""
    mime = IMAGE_SUFFIXES.get(suffix, "application/octet-stream")
    try:
        from PIL import Image  # type: ignore

        with Image.open(io.BytesIO(raw)) as im:
            width, height = im.size
            if max_px > 0 and max(width, height) > max_px:
                im = im.copy()
                im.thumbnail((max_px, max_px), Image.LANCZOS)
                buf = io.BytesIO()
                fmt = "PNG" if suffix == ".png" else im.format or "PNG"
                im.save(buf, format=fmt)
                return buf.getvalue(), im.size[0], im.size[1], "image/png" if fmt == "PNG" else mime
            return raw, width, height, mime
    except Exception:  # noqa: BLE001 — Pillow missing or the file is not decodable: pass it through
        return raw, 0, 0, mime


def _safe_name(name: str) -> str:
    """A link-safe, path-free asset name (the DB primary key is `(citekey, name)`)."""
    cleaned = re.sub(r"[^A-Za-z0-9._-]", "-", Path(name).name).strip("-")
    return cleaned or "image.png"


def _unique(name: str, taken: set[str]) -> str:
    if name not in taken:
        return name
    stem, dot, ext = name.rpartition(".")
    base = stem if dot else name
    n = 2
    while f"{base}-{n}{dot}{ext}" in taken:
        n += 1
    return f"{base}-{n}{dot}{ext}"


def _rewrite(md: str, by_basename: dict[str, str]) -> str:
    """Point every image link at `assets/<name>` — the form the server stores and serves."""

    def sub(m: re.Match[str]) -> str:
        alt, target = m.group(1), m.group(2)
        name = by_basename.get(Path(target).name)
        return f"![{alt}](assets/{name})" if name else m.group(0)

    return IMG_LINK.sub(sub, md)
