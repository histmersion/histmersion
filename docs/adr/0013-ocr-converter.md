# 0013. The OCR converter is a stateless Python sidecar

- Status: accepted
- Date: 2026-11-21

## Context

OCR and PDF/EPUB conversion are the one piece kept in Python, because docling gives the best OCR. The server is a native
image with no image codec that works there. Conversions of scanned books run to hundreds of megabytes and many minutes.

## Decision

- **Contract.** `POST /convert` (a PDF or EPUB, `force_quality`, `dpi`, `max_image_px`) returns the Markdown, the page
  count and the images; `POST /thumbnail` returns a small rendition of one image; `POST /inspect` counts pages and
  scanned pages; `GET /health` reports whether docling is ready. The server sends its own `dpi` and `max-image-px` per
  call. The converter is stateless: nothing is written outside a per-request temporary directory. Its document (and
  pymupdf's mmap of the file) is closed before that directory is removed.
- **Two paths.** The fast path is pymupdf4llm with `write_images=True`, so figures survive the conversion. With
  `force_quality` (image-only scans) docling runs instead, exporting with `ImageRefMode.REFERENCED`, so its pictures are
  written as files too. docling reads the bundle's models from `DOCLING_ARTIFACTS_PATH`; without it, it would download
  them into `~/.cache/huggingface` on first use and fail offline. docling scales relative to 72 dpi.
- **Images.** Every image link in the returned Markdown is rewritten to the relative form `assets/<name>`, which is how
  the server stores them (table `assets`, keyed by citekey and name) and serves them, so the same Markdown works on disk
  and on the web. Images are deduplicated by SHA-256 (scans repeat the same logo on every page) and downscaled so that
  neither side exceeds `max_image_px`. pymupdf4llm names an extracted image after the input file, which is a temporary
  copy, so images are renamed (`upload.pdf-0001-05.png` becomes `fig-0001-05.png`) through the same mapping the links
  use.
- **OCR only where there is no text.** With an OCR engine installed, pymupdf4llm OCRs every page its model flags, and a
  page that is a full-page scan with the recognised text laid over it (what ABBYY FineReader writes) is flagged; the OCR
  text is then added to the text layer already there and every line comes out twice. The fast path therefore OCRs only
  pages without a text layer (fewer extractable characters than one line). The tests fake the OCR engine, so they need
  neither RapidOCR nor Tesseract and are deterministic.
- **Patience scales with the work.** `/inspect` counts pages and scans cheaply, with no conversion, so the server waits
  longer for a 600-page book or for scanned pages than for a 10-page article.
- **Memory.** An upload is streamed to its temporary file in 1 MiB blocks: holding a whole scanned book in memory per
  concurrent request ran the sidecar out of memory.
- **Start-up.** Importing docling (torch, transformers) takes about 30 s cold. `/health` answers at once whether docling
  is installed, the import is warmed in the background (which also makes the first OCR job start fast), and its result
  is reported once known. A converter that imported docling on every health call answered too late and was reported
  degraded.
- **Thumbnails** are made here: JPEG for photographs, PNG when the picture has transparency, never upscaled.
- **Figure fixture.** `converter/tests/test_figures.py` converts a paper laid out as journals print them and checks the
  answer carries what the server's illustration choice (`FigurePick`) needs. The same answer is checked in as
  `modules/core/src/test/resources/figures/paper.json` for `FigurePickSuite`; regenerate it with
  `FIGURES_FIXTURE=<path> pytest`.

## Consequences

- The server stays free of Python and image codecs; the sidecar can be restarted at any time without losing state.

## Code

`converter/app.py`, `converter/tests/`, `scripts/bundle/converter.sh` (ADR 0005), the server's converter client.
