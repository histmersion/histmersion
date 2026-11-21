from __future__ import annotations

import base64
import io
import json
import os
import zlib

import pytest
from fastapi.testclient import TestClient

import app as converter

client = TestClient(converter.app)


def _png(width: int, height: int, colour: tuple[int, int, int]) -> bytes:
    def chunk(kind: bytes, data: bytes) -> bytes:
        return len(data).to_bytes(4, "big") + kind + data + zlib.crc32(kind + data).to_bytes(4, "big")

    ihdr = width.to_bytes(4, "big") + height.to_bytes(4, "big") + bytes([8, 2, 0, 0, 0])
    rows = []
    for y in range(height):
        rows.append(b"\x00" + b"".join(bytes(((colour[0] + x) % 256, (colour[1] + y) % 256, colour[2])) for x in range(width)))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b"")


@pytest.fixture(scope="module")
def paper_pdf() -> bytes:
    pymupdf = pytest.importorskip("pymupdf")
    masthead = _png(600, 60, (10, 20, 30))
    logo = _png(40, 40, (200, 0, 0))
    figure = _png(500, 380, (30, 120, 60))
    icon = _png(60, 60, (0, 0, 200))
    doc = pymupdf.open()
    for n in range(4):
        page = doc.new_page()
        page.insert_text((72, 60), f"Journal of Aegean Studies · page {n + 1}", fontsize=10)
        page.insert_image(pymupdf.Rect(500, 20, 540, 60), stream=logo)
        if n == 0:
            page.insert_image(pymupdf.Rect(40, 80, 560, 132), stream=masthead)
            page.insert_text((72, 170), "The Mycenaean palaces and their territories", fontsize=16)
        if n == 1:
            page.insert_text((72, 100), "Figure 1. The Argolid with the sites named in the text.", fontsize=10)
            page.insert_image(pymupdf.Rect(72, 120, 522, 462), stream=figure)
        if n == 2:
            page.insert_image(pymupdf.Rect(72, 120, 132, 180), stream=icon)
        page.insert_text((72, 700), "Body text " * 20, fontsize=9)
    out = io.BytesIO()
    doc.save(out)
    doc.close()
    return out.getvalue()


def test_conversion_reports_what_the_illustration_choice_needs(paper_pdf):
    res = client.post("/convert", files={"file": ("paper.pdf", paper_pdf, "application/pdf")})
    assert res.status_code == 200, res.text
    body = res.json()
    md = body["markdown"]
    figures = []
    for img in body["images"]:
        name = img["name"]
        assert name.startswith("fig-"), name
        links = md.count(f"assets/{name}")
        figures.append({"name": name, "width": img["width"], "height": img["height"], "refs": links,
                        "firstAt": (md.find(f"assets/{name}") / len(md)) if links else None})
    largest = max(figures, key=lambda f: f["width"] * f["height"])
    assert largest["name"].startswith("fig-0002-"), figures
    assert abs(largest["width"] / largest["height"] - 500 / 380) < 0.02, largest
    logo = [f for f in figures if f["refs"] >= 3]
    assert len(logo) == 1 and logo[0]["width"] == logo[0]["height"] < 150, figures
    assert any(f["width"] >= 8 * f["height"] for f in figures), figures
    path = os.environ.get("FIGURES_FIXTURE")
    if path:
        with open(path, "w", encoding="utf-8") as out:
            json.dump({"bodyChars": len(md), "figures": figures}, out, indent=2)


def test_thumbnail_is_small_and_keeps_the_aspect():
    raw = _png(900, 600, (120, 80, 40))
    res = client.post("/thumbnail", files={"file": ("cover.png", raw, "image/png")}, data={"max_px": "300"})
    assert res.status_code == 200, res.text
    t = res.json()
    assert (t["width"], t["height"]) == (300, 200)
    assert t["mime"] == "image/jpeg"
    assert base64.b64decode(t["base64"])[:2] == b"\xff\xd8"


def test_thumbnail_never_upscales():
    res = client.post("/thumbnail", files={"file": ("x.png", _png(50, 40, (1, 2, 3)), "image/png")}, data={"max_px": "300"})
    assert res.status_code == 200, res.text
    assert (res.json()["width"], res.json()["height"]) == (50, 40)


def test_thumbnail_refuses_what_is_not_an_image():
    res = client.post("/thumbnail", files={"file": ("x.png", b"not an image", "image/png")}, data={"max_px": "300"})
    assert res.status_code == 422
