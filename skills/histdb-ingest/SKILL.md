---
name: histdb-ingest
description: Get sources into the hist-db corpus and keep them enriched — seed a markdown directory, then correct and extend individual sources. Use when adding papers or books to the database, after acquiring a PDF, or when enrichment (findings, tags, period, regions) needs to be recorded.
---

# Ingesting into hist-db

The database is the **source of truth** for source content and enrichment. Markdown is the wire
format to the model and an **export mirror** — never the authority, never grepped.

## What exists today

### `seed` — import a directory of markdown source notes

```bash
histdb seed                      # import the configured corpus dir (Docker: the /corpus mount)
histdb seed /path/to/wiki/sources
histdb seed --force              # also overwrite rows that are newer than the files
```

The **path is resolved by the server, not by your shell.** In the Docker setup the host directory
`HISTDB_CORPUS_DIR` is bind-mounted read-only at `/corpus`, so plain `histdb seed` is what you want;
an absolute host path that the container cannot see will fail with "not a directory on the server".

`seed` is idempotent: it upserts by citekey and re-resolves the typed edges between sources
afterwards. It reports `imported`, `failed` (with reasons) and `edgesResolved`.

Each file it reads is a markdown note whose **frontmatter is the record**:

```yaml
---
citekey: cline2014
doi: 10.1515/9781400874880
title: "1177 B.C.: The Year Civilization Collapsed"
authors: [Eric H. Cline]
year: 2014
venue: Princeton University Press
status: current            # foundational | current | superseded | debunked | retracted | unassessed
superseded_by:             # citekey, when status: superseded
topics: [bronze-age-collapse, systems-collapse]
regions: [Levant, Anatolia, Aegean]
period_start: -1250        # signed years: BCE negative
period_end: -1100
tags: [sea-peoples, trade-networks]
summary: >
  ...
findings:
  - claim: "Ugarit destroyed by fire; last tablets mention ships"
    region: Ugarit
    lat: 35.6
    lon: 35.78
    period_start: -1195
    period_end: -1185
    period_name: "Late Bronze IIB"
    tags: [destruction-layer]
    confidence: high
---
The OCR'd body markdown follows the frontmatter.
```

A finding without `lat`/`lon` is still stored — it just does not appear on the map. A source without
`period_start`/`period_end` does not appear on the timeline. Both are worth filling in.

### Verify what landed

```bash
histdb get <citekey>          # metadata + findings
histdb get <citekey> --full   # + the whole OCR'd body
histdb neighbors <citekey>    # the typed edges seed resolved
histdb search "<title words>"
```

## Roadmap — not yet available

Do not call these until the MCP tool list / `histdb --help` shows them. See `docs/plan.md`.

| coming | what it does | wave |
|---|---|---|
| `put_source` | create or **replace** a whole source (metadata, body, findings, edges, tags). Idempotent. What `seed` and `ingest` call internally. | 1.3 |
| `patch_source` | **selective** enrichment: each of `metadata`/`summary`/`body`/`tags`/`topics`/`period`/`findings`/`edges`/`notes` is independently optional and replaces only itself. `tags`/`findings`/`edges` take `add`/`remove` lists. Carries an optional `ifUpdatedAt` precondition (412 on mismatch) so two agents cannot clobber each other. | 1.3 |
| `export <dir>` | write the DB back out as frontmatter + body markdown — the git-friendly annotation mirror. | 1.3 |
| `ingest <doi\|arxiv\|file>` | fetch the legal OA PDF (Unpaywall → CORE → arXiv), OCR it through the converter sidecar, store it, and pull `references` edges from Crossref. | 2.6 |
| `assess`, `followups`, `discover`, `archive`, `calibre …` | retraction/citation verdicts, citing-paper intents, OpenAlex discovery, archive.org fetch, host-side Calibre integration. | 2.6 |

**When they land, the rule is: `patch_source` to add or correct findings, tags, edges or notes —
never re-upload the whole source.** `put_source` is for creation and deliberate re-import only.

## Enrichment workflow (the "compile" step)

1. `seed` (or later `ingest`) writes a **skeleton**: frontmatter from the file/APIs, `summary` from
   the abstract, usually empty `topics` / `regions` / `period` / `findings`.
2. Read the body (`histdb get <ck> --full`) and fill in the structure: topics, regions,
   `period_start`/`period_end`, and one `findings` entry per geo-located, time-scoped claim.
3. Set `status`, and `superseded_by` when something newer replaces it.
4. Re-check with `histdb get` and `histdb neighbors`.

A finding is a **claim you could put a pin in on a map and a bracket around on a timeline**. "Trade
declined in the 12th century" is a summary sentence; "Ugarit's last tablets record grain shipments to
Hatti, c. 1190 BCE" is a finding.

## Rules

- **Signed integer years.** 3300 BCE → `-3300`. Never a date type, never a string, never "c. 1200 BCE".
- **Citekeys are stable identifiers**: lowercase, `[a-z][a-z0-9_]+`, `authorYEAR_word` by convention.
  Changing one breaks every edge pointing at it.
- **Never hand-edit the database.** Everything goes through the CLI/MCP so the edges stay resolved.
- **Enrich, don't replace.** Once `patch_source` exists, re-seeding a source you have enriched will
  refuse to overwrite it unless you pass `--force`. That guard exists for a reason; do not reach for
  `--force` to make an error go away.
- **Record provenance.** Where the file came from matters for the acquisition workflow downstream.

Reading the corpus back: the **`histdb-query`** skill. Writing prose from it: **`histdb-compile`**.
