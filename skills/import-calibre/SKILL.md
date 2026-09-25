---
name: import-calibre
description: Import a purchased book or paper from the local Calibre library into the research corpus. Use when a needed foundational work (especially the books the OA pipeline can't fetch — Anthony, West, Dumézil, Burkert, Cline, Eliade…) is in the user's Calibre library. Histmersion copies the file out; the Calibre original is never touched.
allowed-tools: Bash(histmersion:*), mcp__histmersion__*
---

# Import from the Calibre library

Calibre holds the **originals** and is canonical. `histmersion calibre …` is the only way this workspace
touches it — never run `calibredb` (or any other Calibre command) directly, and never a Calibre
*write* command against the library.

`histmersion calibre` runs on the **host**, because Calibre lives here; it reads the library and uploads
the file to the server through the normal ingest path.

## 1. Find the book (read-only)

```bash
histmersion calibre search "<title / author / topic>" --limit 20
```

Returns matching `id`, title, authors, year and available formats. PDF and EPUB import directly;
**KFX/MOBI are DRM/Kindle formats** and must be converted to EPUB or PDF in the Calibre GUI first
(that adds a new format to the same book — then search again).

## 2. Import

```bash
histmersion calibre import <calibre-id> [--citekey <key>] [--format pdf|epub]
```

The file is copied out, OCR'd (figures kept) and stored as a source with `provenance: calibre` and
its `calibreId`, so the original can always be identified. The library itself is untouched. Follow
the OCR job with `histmersion jobs <id>` (`mcp__histmersion__job_status`).

There is no MCP tool for Calibre — it is host-local by design. Use the CLI.

## 3. Enrich

As in **ingest-paper**: `histmersion get <citekey> --full` to read, then `mcp__histmersion__patch_source` for
`summary / topics / regions / period / tags / findings / notes / metadata.status`. Signed years, BCE
negative; findings carry lat/lon. For a 500-page book, focus on the thesis and the load-bearing
claims — an exhaustive pass is not the goal.

## 4. Close the acquisition item

Importing from Calibre satisfies an acquire-queue entry:

```bash
histmersion queue acquire
histmersion queue done <id>
```

## Do NOT push it back

A `provenance: calibre` source is already in the canonical store. `histmersion calibre gaps` skips it.
Running `histmersion calibre push` on it would duplicate the book.

## Done means

`histmersion get <citekey>` shows the OCR'd body plus real enrichment, the acquire item is closed, and
`histmersion calibre gaps` does not list the citekey.
