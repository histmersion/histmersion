---
name: import-file
description: Import a book/paper FILE the user downloaded themselves (an unauthorised book mirror, an author site, interlibrary loan, a scan, a colleague) into the corpus. Use when the user says they've downloaded or saved a PDF/EPUB and want it in the research corpus. Uploads it to hist-db, OCRs it, and pushes the original into Calibre.
allowed-tools: Bash(histdb:*), mcp__hist-db__*
---

# Import a manually-downloaded file

For sources the automated pipeline cannot fetch but the user obtained by hand. The usual channel is
**an unauthorised book mirror** (browser-only, login + CAPTCHA, a few downloads/day) for the gated monographs in
`histdb queue acquire`. The user downloads in the browser; this brings the file in.

## 1. Upload + OCR

```bash
histdb ingest "/Users/me/Downloads/west2007_indoeuropean_poetry.pdf" \
  --citekey west2007_poetry \
  --title "Indo-European Poetry and Myth" --author "West, M. L." --year 2007 \
  [--doi 10.1093/acprof:oso/9780199280759.001.0001] [--quality] [--no-wait]
```

- `--citekey` is **required** for a file. Reuse an existing citekey to re-OCR: only the body and
  figures are replaced, enrichment survives.
- `--quality` selects the slow, accurate OCR path (docling) — use it for image-only scans, skip it
  for normal text PDFs and EPUBs.
- `--title/--author/--year/--doi` are used **only when the source is being created**.
- `--force` accepts a file already stored under another citekey.
- KFX/MOBI cannot be read: have the user convert to EPUB/PDF in the Calibre GUI first, then use the
  **import-calibre** skill instead — it carries richer metadata.

MCP equivalent: `mcp__hist-db__ingest` with `citekey` plus either `path` (a path the *server* can
see) or `base64` + `filename` for smaller files. It returns a job id immediately; poll
`mcp__hist-db__job_status` (CLI: `histdb jobs <id>`) until `done|failed`.

## 2. Enrich

Exactly as in the **ingest-paper** skill: `histdb get <citekey> --full` to read the OCR'd text, then
`mcp__hist-db__patch_source` for `summary / topics / regions / period / tags / findings / notes /
metadata.status`. Books are long — capture the **thesis and load-bearing claims**, not an exhaustive
pass. Signed years, BCE negative; findings carry lat/lon.

## 3. Close the acquisition item

```bash
histdb queue acquire            # find the item's id
histdb queue done <id>
```

(`mcp__hist-db__queue` with `action: "done"`, `id`.)

## 4. Push the original into Calibre

```bash
histdb calibre push <citekey>   # Calibre GUI must be closed
```

Or leave it for the **sync-calibre** skill / the `histdb calibre gaps --quiet` hook.

## Done means

`histdb get <citekey>` shows the OCR'd body and real enrichment, the acquire item is closed, and
`histdb calibre gaps` no longer lists the citekey.

## Note on provenance

An unauthorised book mirror is a shadow library (copyrighted books, legally contested). This skill only imports a file
the user already has on disk; it never downloads from or scrapes an unauthorised book mirror. hist-db records uploaded
files as `provenance: ocr` — there is currently no flag to tag the actual channel
(`external`/`ill`/`author`), so say it in the source `notes` instead.
