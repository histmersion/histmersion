---
name: import-file
description: Import a book/paper FILE the user obtained themselves (a purchase, an author's site, interlibrary loan, a scan, a colleague) into the corpus. Use when the user says they've downloaded or saved a PDF/EPUB and want it in the research corpus. Uploads it to Histmersion, OCRs it, and pushes the original into Calibre.
allowed-tools: Bash(histmersion:*), {{tool:*}}
---

# Import a manually-downloaded file

For sources the automated pipeline cannot fetch but the user obtained by hand — bought, borrowed
through a library or interlibrary loan, sent by the author, scanned — usually the gated monographs in
`histmersion queue acquire`. This brings the file in.

## 1. Upload + OCR

Find the file's item in the acquisition queue first (`histmersion queue acquire`) and pass its id:
the OCR job then ticks the item off when it succeeds, and leaves it open when it fails.

```bash
histmersion ingest "/Users/me/Downloads/west2007_indoeuropean_poetry.pdf" \
  --citekey west2007_poetry \
  --title "Indo-European Poetry and Myth" --author "West, M. L." --year 2007 \
  [--acquire-item <id>] [--doi 10.1093/acprof:oso/9780199280759.001.0001] [--quality] [--no-wait]
```

- `--citekey` is **required** for a file. Reuse an existing citekey to re-OCR: only the body and
  figures are replaced, enrichment survives.
- `--quality` selects the slow, accurate OCR path (docling) — use it for image-only scans, skip it
  for normal text PDFs and EPUBs.
- `--title/--author/--year/--doi` are used **only when the source is being created**.
- `--force` accepts a file already stored under another citekey.
- KFX/MOBI cannot be read: have the user convert to EPUB/PDF in the Calibre GUI first, then use the
  **import-calibre** skill instead — it carries richer metadata.

MCP equivalent: `{{tool:ingest}}` with `citekey` plus either `path` (a path the *server* can
see) or `base64` + `filename` for smaller files, and `acquireItem` for the item it fulfils. It
returns a job id immediately; poll `{{tool:job_status}}` (CLI: `histmersion jobs <id>`)
until `done|failed`.

## 2. Enrich

**First check whether the server already queued it.** With embedded agents on and
`agents.auto-enrich` on (the default once agents are on), the import job itself queues an
`enrich-source` run for a new source: its progress line (`histmersion jobs <job id>`,
`{{tool:job_status}}`) reads `enrichment queued: run <id>`. Since the process managers' phase 2 it usually reads `enrichment queued: item #<n> of the enrich manager` instead:
the `enrich` manager runs it (`histmersion agent manager enrich items`, `{{tool:manager}}` `show` with id `enrich`); while that
line also says `paused`, nothing runs until a person resumes the manager — enrich by hand meanwhile, or ask.
Follow a run with
`histmersion agent show <id>` (`{{tool:run_status}}`) and review what it wrote instead of enriching
in parallel. With no such line (agents off), or `enrichment not queued: <why>` (no model can run, the
source already had findings or notes), enrich by hand as below.

Exactly as in the **ingest-paper** skill: `histmersion get <citekey> --full` to read the OCR'd text, then
`{{tool:patch_source}}` for `summary / topics / regions / period / tags / findings / notes /
metadata.status`. Books are long — capture the **thesis and load-bearing claims**, not an exhaustive
pass. Signed years, BCE negative; findings carry lat/lon.

## 3. Check the acquisition item is closed

With `--acquire-item` (or `acquireItem`) the job already ticked it off as `ingested as [[citekey]]`
— `histmersion jobs <job id>` says so on its progress line. Without it, the job ticked off any open
item that names the source's DOI (pass `--doi` when the source is new, so it can), and listed items
with a similar title as `possible acquisition item #N …` — those stay open. Close the right one by
hand:

```bash
histmersion queue acquire            # find the item's id
histmersion queue done <id>
```

(`{{tool:queue}}` with `action: "done"`, `id`.)

## 4. Push the original into Calibre

```bash
histmersion calibre push <citekey>   # Calibre GUI must be closed
```

Or leave it for the **sync-calibre** skill / the `histmersion calibre gaps --quiet` hook.

## Done means

`histmersion get <citekey>` shows the OCR'd body and real enrichment, the acquire item is closed, and
`histmersion calibre gaps` no longer lists the citekey.

## Note on provenance

This skill only imports a file the user already has on disk, obtained legally; it never downloads
anything. Histmersion records uploaded files as `provenance: ocr` — there is currently no flag to tag
the actual channel (`ill`/`author`), so say it in the source `notes` instead.
