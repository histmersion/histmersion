"""Converter contract tests (plan §1.8): text AND embedded images come back from /convert.

A tiny PDF is generated with pymupdf (already a dependency, so no reportlab needed): one page of
text plus one embedded PNG. The assertions are the contract the Scala server depends on —
`markdown`, `pages`, and an `images` list whose links appear in the Markdown as `assets/<name>`.
"""
from __future__ import annotations

import base64
import io
import zlib

import pytest
from fastapi.testclient import TestClient

import app as converter

client = TestClient(converter.app)

TEXT = "Sea Peoples and the Bronze Age collapse"


def _png(width: int = 64, height: int = 48, colour: tuple[int, int, int] = (200, 40, 40)) -> bytes:
    """A minimal, valid RGB PNG — written by hand so the test needs no image library."""

    def chunk(kind: bytes, data: bytes) -> bytes:
        return (
            len(data).to_bytes(4, "big") + kind + data + zlib.crc32(kind + data).to_bytes(4, "big")
        )

    ihdr = width.to_bytes(4, "big") + height.to_bytes(4, "big") + bytes([8, 2, 0, 0, 0])
    raw = b"".join(b"\x00" + bytes(colour) * width for _ in range(height))
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw))
        + chunk(b"IEND", b"")
    )


@pytest.fixture(scope="module")
def pdf_bytes() -> bytes:
    pymupdf = pytest.importorskip("pymupdf")
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_text((72, 96), TEXT, fontsize=14)
    page.insert_image(pymupdf.Rect(72, 140, 272, 290), stream=_png())
    out = io.BytesIO()
    doc.save(out)
    doc.close()
    return out.getvalue()


def _convert(pdf_bytes: bytes, **form) -> dict:
    res = client.post(
        "/convert",
        files={"file": ("tiny.pdf", pdf_bytes, "application/pdf")},
        data={k: str(v) for k, v in form.items()},
    )
    assert res.status_code == 200, res.text
    return res.json()


def test_health_reports_docling_availability():
    body = client.get("/health").json()
    assert body["status"] == "ok"
    assert isinstance(body["docling"], bool)
    assert body["dpi"] > 0 and body["maxImagePx"] > 0


def test_text_and_one_embedded_image_come_back(pdf_bytes):
    body = _convert(pdf_bytes)
    assert body["pages"] == 1
    assert "Sea Peoples" in body["markdown"]

    assert len(body["images"]) >= 1, body["markdown"]
    img = body["images"][0]
    assert img["mime"].startswith("image/")
    assert img["width"] > 0 and img["height"] > 0
    assert base64.b64decode(img["base64"])[:4] == b"\x89PNG"


def test_markdown_links_are_relative_assets_paths(pdf_bytes):
    body = _convert(pdf_bytes)
    for img in body["images"]:
        assert f"assets/{img['name']}" in body["markdown"], body["markdown"]
    assert "](assets/" in body["markdown"]


def test_max_image_px_downscales(pdf_bytes):
    small = _convert(pdf_bytes, max_image_px=32)
    for img in small["images"]:
        assert max(img["width"], img["height"]) <= 32, img


def test_images_are_deduplicated_by_sha256(pdf_bytes):
    body = _convert(pdf_bytes)
    shas = [i["sha256"] for i in body["images"]]
    assert len(shas) == len(set(shas))
    names = [i["name"] for i in body["images"]]
    assert len(names) == len(set(names))


# ── the pure helpers ──────────────────────────────────────────────────────────────────────────────
def test_rewrite_only_touches_known_images():
    md = "![a](/tmp/x/fig-1.png)\n![b](other.png)\n[link](/tmp/x/fig-1.png)"
    out = converter._rewrite(md, {"fig-1.png": "fig-1.png"})
    assert "![a](assets/fig-1.png)" in out
    assert "![b](other.png)" in out
    assert "[link](/tmp/x/fig-1.png)" in out  # a plain link is not an image link


def test_asset_names_drop_the_temporary_input_file_prefix(pdf_bytes):
    # pymupdf4llm names images after the input file, which here is a temp copy — the public URL must
    # not carry that name.
    body = _convert(pdf_bytes)
    for img in body["images"]:
        assert not img["name"].startswith("upload"), img["name"]
        assert img["name"].startswith("fig-"), img["name"]


def test_asset_name_helper():
    assert converter._asset_name("upload.pdf-0001-05.png", "upload.pdf") == "fig-0001-05.png"
    assert converter._asset_name("other.png", "upload.pdf") == "other.png"
    assert converter._asset_name("x.png", "") == "x.png"


def test_safe_name_strips_paths_and_odd_characters():
    assert converter._safe_name("a/b/../weird name!.png") == "weird-name-.png"
    assert converter._safe_name("") == "image.png"


def test_unique_suffixes_collisions():
    taken = {"a.png"}
    assert converter._unique("a.png", taken) == "a-2.png"
    assert converter._unique("b.png", taken) == "b.png"


def test_inspect_counts_pages(pdf_bytes):
    res = client.post("/inspect", files={"file": ("tiny.pdf", pdf_bytes, "application/pdf")})
    assert res.status_code == 200, res.text
    assert res.json() == {"pages": 1, "scanned": 0}


def test_inspect_refuses_what_it_cannot_read():
    res = client.post("/inspect", files={"file": ("x.pdf", b"not a pdf at all", "application/pdf")})
    assert res.status_code == 422
    assert "cannot read" in res.json()["error"]


def test_inspect_counts_pages_without_a_text_layer(pdf_bytes):
    pymupdf = pytest.importorskip("pymupdf")
    doc = pymupdf.open(stream=pdf_bytes, filetype="pdf")
    doc.new_page()  # a page with no text at all — what a scan looks like to pymupdf
    out = io.BytesIO()
    doc.save(out)
    res = client.post("/inspect", files={"file": ("two.pdf", out.getvalue(), "application/pdf")})
    assert res.status_code == 200, res.text
    assert res.json() == {"pages": 2, "scanned": 1}


# ── OCR on the fast path (#700/#740) ─────────────────────────────────────────────────────────────
# ABBYY FineReader writes a page as a full-page scan with the recognised text laid over it. With an
# OCR engine installed (the bundle has RapidOCR through docling), pymupdf4llm's decision model flags
# such a page — the image "contains text" — and ADDS the OCR text to the text layer already there:
# every line came out twice. The OCR engine is replaced by a fake here, so the test needs neither
# RapidOCR nor Tesseract and is deterministic.
SCAN_LINES = [
    "Phoenician archives are abundant in ancient sources.",
    "The library of Carthage passed to the Numidian kings.",
    "Josephus consulted the archives of Tyre.",
    "Philon of Byblos wrote a Phoenician History.",
]


def _scan_page_pdf(with_text_layer: bool) -> bytes:
    """One page: a full-page raster of some printed lines and, optionally, those lines again as a
    text layer on top — the ABBYY shape. Without the text layer it is a plain scan."""
    pymupdf = pytest.importorskip("pymupdf")
    printed = pymupdf.open()
    page = printed.new_page()
    for i, line in enumerate(SCAN_LINES):
        page.insert_text((72, 96 + 24 * i), line, fontsize=12)
    pix = page.get_pixmap(dpi=150)
    doc = pymupdf.open()
    scan = doc.new_page()
    scan.insert_image(scan.rect, pixmap=pix)
    if with_text_layer:
        for i, line in enumerate(SCAN_LINES):
            scan.insert_text((72, 96 + 24 * i), line, fontsize=12)
    out = io.BytesIO()
    doc.save(out)
    return out.getvalue()


@pytest.fixture
def fake_ocr(monkeypatch):
    """Stand-in OCR engine: 'recognises' the printed lines as invisible text, like a real one."""
    layout = pytest.importorskip("pymupdf4llm.helpers.document_layout")
    calls: list[int] = []

    def engine(page, dpi=150, language="eng", keep_ocr_text=False):
        calls.append(page.number)
        for i, line in enumerate(SCAN_LINES):
            page.insert_text((72, 96 + 24 * i), line, fontsize=12, render_mode=3)

    monkeypatch.setattr(layout, "select_ocr_function", lambda: engine)
    return calls


def test_a_scan_with_a_text_layer_is_not_ocred_again(fake_ocr):
    md = _convert(_scan_page_pdf(with_text_layer=True))["markdown"]
    for line in SCAN_LINES:
        assert md.count(line) == 1, md
    assert fake_ocr == []


def test_a_scan_without_a_text_layer_is_still_ocred(fake_ocr):
    md = _convert(_scan_page_pdf(with_text_layer=False))["markdown"]
    assert fake_ocr == [0]
    for line in SCAN_LINES:
        assert md.count(line) == 1, md
