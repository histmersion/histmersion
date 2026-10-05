---
name: import-archive
description: Acquire a book from the Internet Archive (archive.org) into the corpus. Use for out-of-print / older scholarly books the OA pipeline can't fetch (Nilsson, Burkert, Watkins, Anthony, Gimbutas, Dumézil, etc.). Imports only PUBLICLY-available items; lending-only scans are detected and refused (no DRM circumvention).
allowed-tools: Bash(histmersion:*), {{tool:*}}
---

# Import from the Internet Archive

Histmersion fetches only **public** archive.org items. A lending-library item is reported and refused —
borrowing it on the archive.org website is the only legitimate way to read it, and nothing here
circumvents that.

## 1. Find it and check availability

```bash
histmersion archive search "<title> <author>" --rows 10
histmersion archive info <identifier>
```

`{{tool:archive}}` with `query` (search) or `id` (one item's files + lending restriction).

Verify the matched title is actually the book — distinctive titles match reliably, generic ones
mis-hit. Prefer an item carrying an **EPUB** or a text-layer PDF over a raw image scan.

## 2. Import

```bash
histmersion archive import <identifier> [--citekey <key>] [--format epub|pdf]
```

`{{tool:archive}}` with `id` and `import: true` (+ `citekey`, `format`).

The server downloads the public file, runs the same OCR job as any ingest (keeping figures), and
creates the source with `provenance: archive_org`. Follow the job with `histmersion jobs <id>`
(`{{tool:job_status}}`).

## 3. Enrich

Same as **ingest-paper**, section-aware for a big book: `histmersion get <citekey> --full`, read the front
matter and TOC for the thesis and scope, then `{{tool:patch_source}}` with `summary`, `topics`,
`regions`, `period` (signed years, BCE negative), `tags`, located `findings`, `notes` with
`[[citekey]]` / `[[slug]]` links, and usually `metadata.status: "foundational"`.

## 4. Push into Calibre

```bash
histmersion calibre push <citekey>       # GUI closed
```

An archive.org book is not in the canonical store until this runs; `histmersion calibre gaps` and the
reminder hook will keep saying so.

## 5. Close the acquisition item

```bash
histmersion queue acquire
histmersion queue done <id>
```

A lending-only book stays open on the acquire queue — it still needs a purchase, a library copy or
an IA borrow. Add that as the reason:
`histmersion queue acquire add "<Book> — archive.org is lending-only; buy/borrow" --topic "<topic>"`.

## Done means

The source exists with an OCR'd body and real enrichment, `histmersion calibre gaps` does not list it,
and the acquire item is either closed or annotated as lending-only.
