# Converter upgrade: docling 2.142 · docling-ibm-models 4 · pymupdf4llm 2.0 · Pillow 13 · torch 2.14 (#3712, 2026-10-24)

**Decision: not adopted.** The upgraded stack OCRs image-only scans worse on one of three test scans
(a clean line read as noise), and OCR of scans is what docling is in the converter for. Everything else was
equal or better. The regression is docling 2.142 / docling-ibm-models 4: the same comparison with docling held
at 2.121 (and so Pillow at 12.3) OCRs exactly as today.

## How it was compared

Two copies of `converter/` in a scratch directory, each with its own `uv sync --frozen --extra ocr` and its own
model cache (`HF_HOME`): today's lock, and `uv lock --upgrade-package` of the five packages (resolving to docling
2.142.1, docling-ibm-models 4.0.4, pymupdf4llm 2.0, Pillow 13.0.0, torch 2.14.1, torchvision 0.29.1). The
repository holds no sample PDF, so four were generated with known text (two pages: a title, headings, paragraphs
with ASCII, `Ḫattuša` and `Ἀχαιοί`, a ruled 4 × 3 table, a numbered list): `text` (a text layer), `figure` (the
same with a picture and its caption), `scan` (both pages as 200-dpi images, no text layer), `mixed` (page 1 a
scan, page 2 text), and the scan again at 150 and 300 dpi. Each went through the converter's own `_to_markdown`
— the fast path (pymupdf4llm) and the quality path (docling) — and was scored by the share of the ground-truth
words found in the Markdown. OCR runs were repeated: both stacks are deterministic.

| | today | upgraded | docling held at 2.121 |
|---|---|---|---|
| converter tests (`pytest`) | 19 passed, 35.6 s | 19 passed, 13.5 s (after the change below) | 19 passed, 37.5 s |
| fast path, all four samples | — | byte-identical Markdown | — |
| docling, text layer (`text`, `figure`) | 0.988 (`Ḫ attuša`, `Ἀ χαιοί` split) | 1.000 | 1.000 |
| docling OCR, scan at 150 / 200 / 300 dpi | 0.920 / 0.920 / 0.877 | 0.920 / **0.877** / 0.877 | 0.920 / 0.920 / 0.877 |
| docling OCR, `mixed` | 0.920 | **0.877** | 0.920 |
| peak RSS of one process converting all samples | 4.25 GB | 4.09 GB | — |

At 200 dpi the upgraded docling turns "…and the city of Ugarit were destroyed or abandoned within a few
decades." (clean in the image) into "…awne c i es", and drops the "1." of a numbered heading. Both stacks garble
the same other line; that is not a difference.

## Not a drop-in: pymupdf4llm 2.0

pymupdf4llm 2.0 is an EMPTY package (904 bytes, no dependencies): "pymupdf-2.x now contains the 4llm code".
Upgrading it alone removes pymupdf from the environment and the fast path with it (10 of 19 tests skip). The
upgrade needs `pymupdf>=2.0` in place of `pymupdf4llm>=0.0.17` in `converter/pyproject.toml`; `import pymupdf4llm`
(and `pymupdf4llm.helpers.document_layout`) then resolve from pymupdf 2.0 unchanged, and pymupdf 2.0 brings
`pymupdf-layout` and `onnxruntime`. With that change the fast path's output is byte-identical.

## If it is taken up later

- pymupdf 2.0 and torch 2.14 with docling held at 2.121 measured as today on OCR and better on text layers;
  Pillow then stays at 12.3 (docling 2.121 caps it). That partial step needs its own bundle run
  (`scripts/bundle/converter.sh`, the models' download) before it is adopted.
- docling ≥ 2.142 should be measured again on real scans of the corpus (this comparison had one OCR document at
  three resolutions) before it is ruled in or out for good.
