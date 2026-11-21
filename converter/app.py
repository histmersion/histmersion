from __future__ import annotations

import base64
import hashlib
import importlib.util
import io
import os
import re
import tempfile
import threading
from pathlib import Path

from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import JSONResponse

DEFAULT_DPI = int(os.environ.get("HISTMERSION_CONVERTER_DPI", "150"))
DEFAULT_MAX_IMAGE_PX = int(os.environ.get("HISTMERSION_CONVERTER_MAX_IMAGE_PX", "2000"))

IMAGE_SUFFIXES = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".webp": "image/webp"}

UPLOAD_CHUNK_BYTES = 1024 * 1024

app = FastAPI(title="Histmersion converter")


def _docling_available() -> bool:
    try:
        import docling.document_converter  # type: ignore  # noqa: F401

        return True
    except Exception:  # noqa: BLE001 — any import-time failure means "not usable here"
        return False


_DOCLING_INSTALLED = importlib.util.find_spec("docling") is not None
_docling_ok: bool | None = None


def _warm_docling() -> None:
    global _docling_ok
    _docling_ok = _docling_available()


if _DOCLING_INSTALLED:
    threading.Thread(target=_warm_docling, name="docling-warmup", daemon=True).start()


@app.get("/health")
def health():
    return {
        "status": "ok",
        "docling": _docling_ok if _docling_ok is not None else _DOCLING_INSTALLED,
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


SCANNED_TEXT_CHARS = 20


@app.post("/inspect")
async def inspect(file: UploadFile = File(...)):
    suffix = os.path.splitext(file.filename or "upload.pdf")[1] or ".pdf"
    with tempfile.TemporaryDirectory() as work:
        src = Path(work) / f"upload{suffix}"
        with src.open("wb") as out:
            while chunk := await file.read(UPLOAD_CHUNK_BYTES):
                out.write(chunk)
        try:
            import pymupdf  # type: ignore

            with pymupdf.open(str(src)) as doc:
                pages = doc.page_count
                scanned = sum(1 for page in doc if len(page.get_text().strip()) < SCANNED_TEXT_CHARS)
        except Exception as e:  # noqa: BLE001 — an unreadable file is the caller's answer, not a crash
            return JSONResponse(status_code=422, content={"error": f"cannot read the document: {e}"})
    return {"pages": pages, "scanned": scanned}


THUMB_MAX_UPLOAD = 32 * 1024 * 1024


@app.post("/thumbnail")
async def thumbnail(file: UploadFile = File(...), max_px: int = Form(330)):
    raw = await file.read(THUMB_MAX_UPLOAD + 1)
    if not raw or len(raw) > THUMB_MAX_UPLOAD:
        return JSONResponse(status_code=422, content={"error": "empty or too large"})
    try:
        data, mime, width, height = _thumbnail(raw, max(16, max_px))
    except Exception as e:  # noqa: BLE001 — not an image Pillow reads: the caller shows the original
        return JSONResponse(status_code=422, content={"error": f"not a readable image: {e}"})
    return {"mime": mime, "base64": base64.b64encode(data).decode("ascii"), "width": width, "height": height}


def _thumbnail(raw: bytes, max_px: int) -> tuple[bytes, str, int, int]:
    from PIL import Image  # type: ignore

    with Image.open(io.BytesIO(raw)) as im:
        im.load()
        alpha = im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info)
        im = im.convert("RGBA" if alpha else "RGB")
        im.thumbnail((max_px, max_px), Image.LANCZOS)
        buf = io.BytesIO()
        if alpha:
            im.save(buf, format="PNG", optimize=True)
            mime = "image/png"
        else:
            im.save(buf, format="JPEG", quality=82, optimize=True)
            mime = "image/jpeg"
        return buf.getvalue(), mime, im.size[0], im.size[1]


def _to_markdown(
    path: str, work: Path, force_quality: bool, dpi: int, max_px: int
) -> tuple[str, int, list[dict]]:
    if force_quality:
        quality = _docling(path, work, dpi, max_px)
        if quality is not None:
            return quality
    return _pymupdf(path, work, dpi, max_px)


def _pymupdf(path: str, work: Path, dpi: int, max_px: int) -> tuple[str, int, list[dict]]:
    import pymupdf  # type: ignore
    import pymupdf4llm  # type: ignore

    imgdir = work / "images"
    imgdir.mkdir(exist_ok=True)
    md = pymupdf4llm.to_markdown(
        path, write_images=True, image_path=str(imgdir), image_format="png", dpi=dpi,
        **_ocr_kwargs(),
    )
    with pymupdf.open(path) as doc:
        pages = doc.page_count
    return _finish(md, imgdir, max_px, pages, stem=_safe_name(Path(path).name))


def _ocr_kwargs() -> dict:
    try:
        from pymupdf4llm.helpers import document_layout  # type: ignore
    except Exception:  # noqa: BLE001 — the legacy (non-layout) path has no OCR to restrict
        return {}
    engine = document_layout.select_ocr_function()
    if not callable(engine):
        return {}
    return {"ocr_function": _ocr_textless_pages_only(engine)}


def _ocr_textless_pages_only(engine):
    def ocr(page, **kwargs):
        if _has_text_layer(page):
            return
        engine(page, **kwargs)

    return ocr


def _has_text_layer(page) -> bool:
    return len(page.get_text().strip()) >= SCANNED_TEXT_CHARS


def _docling(path: str, work: Path, dpi: int, max_px: int) -> tuple[str, int, list[dict]] | None:
    try:
        from docling.datamodel.base_models import InputFormat  # type: ignore
        from docling.datamodel.pipeline_options import PdfPipelineOptions  # type: ignore
        from docling.document_converter import DocumentConverter, PdfFormatOption  # type: ignore
        from docling_core.types.doc import ImageRefMode  # type: ignore
    except ImportError:
        return None

    opts = PdfPipelineOptions()
    artifacts = os.environ.get("DOCLING_ARTIFACTS_PATH")
    if artifacts:
        opts.artifacts_path = artifacts
    opts.generate_picture_images = True
    opts.images_scale = max(1.0, dpi / 72.0)
    conv = DocumentConverter(format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=opts)})
    doc = conv.convert(path).document
    out = work / "docling"
    out.mkdir(exist_ok=True)
    target = out / "doc.md"
    doc.save_as_markdown(target, image_mode=ImageRefMode.REFERENCED)
    md = target.read_text(encoding="utf-8")
    pages = len(getattr(doc, "pages", []) or [])
    return _finish(md, out, max_px, pages)


IMG_LINK = re.compile(r"!\[([^\]]*)\]\(\s*<?([^)>\s]+)>?(\s+\"[^\"]*\")?\s*\)")


def _finish(md: str, imgdir: Path, max_px: int, pages: int, stem: str = "") -> tuple[str, int, list[dict]]:
    images, by_basename = _collect(imgdir, max_px, stem)
    return _rewrite(md, by_basename), pages, images


def _asset_name(name: str, stem: str) -> str:
    safe = _safe_name(name)
    if stem and safe.startswith(stem + "-"):
        return "fig-" + safe[len(stem) + 1 :]
    return safe


def _collect(imgdir: Path, max_px: int, stem: str = "") -> tuple[list[dict], dict[str, str]]:
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
        if sha in by_sha:
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

    def sub(m: re.Match[str]) -> str:
        alt, target = m.group(1), m.group(2)
        name = by_basename.get(Path(target).name)
        return f"![{alt}](assets/{name})" if name else m.group(0)

    return IMG_LINK.sub(sub, md)
