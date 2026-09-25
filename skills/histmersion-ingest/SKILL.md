---
name: histmersion-ingest
description: Get sources into the Histmersion corpus and keep them enriched — ingest by DOI/arXiv or from a file, seed a markdown directory, then correct and extend individual sources with patch_source. Use when adding papers or books to the database, after acquiring a PDF, or when enrichment (findings, tags, period, regions, status, edges) needs to be recorded.
---

# Ingesting into Histmersion

The database is the **source of truth** for source content and enrichment. Markdown is the wire
format to the model and an **export mirror** (`histmersion export <dir>`) — never the authority, never
grepped.

## Getting a source in

| you have | MCP | CLI |
|---|---|---|
| a DOI or arXiv id | `ingest_doi {id:"10.1038/nature14317", citekey:"haak2015_massive", arxiv:"…"}` | `histmersion ingest 10.1038/nature14317 --citekey haak2015_massive` |
| a PDF/EPUB the **server** can read | `ingest {citekey, path, quality, force, title, authors, year, doi}` | `histmersion ingest /path/file.pdf --citekey k --title … --author … --year …` |
| a PDF/EPUB only **you** can read | `ingest {citekey, base64, filename, …}` (≤ `limits.inline-bytes`) | `histmersion ingest ./file.pdf --citekey k` (the CLI uploads it) |
| a directory of note files | — | `histmersion seed [<dir>] [--force]` |
| a purchased book in Calibre | — (host-local) | `histmersion calibre search "<q>"` → `histmersion calibre import <id> --citekey k` |
| a public archive.org text | `archive {query\|id, import:true, citekey}` | `histmersion archive search "<q>"` · `info <id>` · `import <id> --citekey k` |

> The CLI folds identifier-ingest and file-ingest into one `histmersion ingest`; **MCP splits them into
> two tools** — `ingest_doi` for an identifier, `ingest` for a file. The code stream is aligning
> them (`docs/plan.md` §6).

`ingest_doi` runs metadata → legal OA PDF (Unpaywall → CORE → arXiv) → the OCR job → Crossref
`references` edges → assessment. It is idempotent and **never wipes enrichment**.

OCR is asynchronous: `ingest` returns a **job id**. Follow it with `job_status {id}` /
`histmersion jobs <id>` (`queued|running|done|failed`). `--quality` (MCP `quality: true`) picks the slow,
accurate path (docling) for image-only scans. `reocr <citekey>` re-converts a stored original.
`assets {citekey}` lists the figures the OCR kept and the URLs they are served at.

## `seed` — import a directory of markdown notes

```bash
histmersion seed                      # the configured corpus dir (Docker: the /corpus mount)
histmersion seed /path/to/wiki        # sources AND the articles beside them
histmersion seed --force              # also overwrite rows newer than the files
```

The path is resolved **on the host when it exists there, otherwise asked of the server**. In the
Docker setup the host dir `HISTMERSION_CORPUS_DIR` is bind-mounted read-only at `/corpus`, so plain
`histmersion seed` is what you want. `seed` upserts by citekey and re-resolves the typed edges afterwards;
it reports `imported`, `failed` (with reasons) and `edgesResolved`.

Each file's **frontmatter is the record**:

```yaml
---
citekey: cline2014_year
doi: 10.1515/9781400874880
title: "1177 B.C.: The Year Civilization Collapsed"
authors: [Eric H. Cline]
year: 2014
venue: Princeton University Press
status: foundational       # foundational|current|contested|superseded|debunked|retracted|unassessed
superseded_by:             # citekey, when status: superseded — stored as a `superseded_by` edge
topics: [bronze_age_collapse, systems_collapse]
regions: [Levant, Anatolia, Aegean]
period_start: -1250        # signed years: BCE negative
period_end: -1100
tags: [sea_peoples, trade_networks]
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
    tags: [destruction_layer]
    confidence: high
---
The Markdown body becomes the source's `notes` (what YOU write about it), not the OCR'd text.
```

A finding without `lat`/`lon` is stored but never appears on the map; a source without
`period_start`/`period_end` never appears on the timeline. `lint` flags both
(`unlocated-findings`, `no-period`).

## Enrich, don't replace

`ingest` writes a **skeleton**: metadata from the APIs, `summary` from the abstract, usually empty
`topics` / `regions` / `period` / `findings`. Enrichment is the compile step, and it is
**`patch_source`, never `put_source`**.

```
patch_source {citekey:"haak2015_massive", patch:{
  topics: ["steppe_hypothesis","ancient_dna"],
  regions: ["eurasian_steppe","europe"],
  period: {start:-4500, end:-2000},
  summary: "…",
  tags:     {add:["yamnaya"], remove:["General"]},
  findings: {add:[{claim:"…", region:"…", lat:47.0, lon:40.0,
                   periodStart:-3300, periodEnd:-2600, periodName:"…",
                   tags:["…"], confidence:"high"}],
             remove:["haak2015_massive#2"]},
  edges:    {add:[{src:"kassian2025_critique", dst:"heggarty2023_language", kind:"refutes"}]},
  ifUpdatedAt: "<the updatedAt you last read>"
}}
```

- Every top-level part is **independently optional**; a present part replaces only itself.
- `tags`/`findings` take `add`/`remove`/`replace`; `edges` takes `add`/`remove`.
- `findings.remove` carries finding **ids** (`<citekey>#<n>`); `add`/`replace` carry values.
- `ifUpdatedAt` is an optimistic-concurrency precondition — HTTP **412** instead of a clobber.
- `put_source` is for **creation and deliberate re-import only**. It replaces everything.

CLI: `histmersion patch <citekey> --topics … --regions … --period -4500,-2000 --status contested
--tags-add … --add-finding '<FindingInput JSON>' --remove-finding 'ck#2' --notes-file f.md
--if-updated-at …`, or `--json '<whole SourcePatch>'`.

## Edge kinds — use the real ones

Stored kinds: **`references`, `challenges`, `refutes`, `updates`, `superseded_by`**. There is no
`cites` edge and no `supersedes` edge in the corpus.

`followups` *proposes* a different vocabulary (`confirms|challenges|refutes|supersedes|cites`).
Translate before recording: `cites`/`confirms` → `references`, `supersedes` → `superseded_by` on
the older source. `refs --apply` and `followups --apply` write edges for you — check what they wrote
with `neighbors`. (The two vocabularies are handed to the code stream, `docs/plan.md` §6.)

Most edges in the corpus point at **concept slugs**, not citekeys — 1,039 of 1,048 `references` do.
That is by design (they are topic links), and `lint --rule dangling-edges` lists them.

## Status

`foundational` · `current` · **`contested`** · `superseded` (set `superseded_by`) · `debunked` ·
`retracted` · `unassessed`.

`contested` means **published critiques exist but nothing supersedes it yet** — the live corpus has
17. Set it whenever `followups` or `assess` turns up a real critique; do not jump straight to
`debunked`. `assess {id, apply:true}` writes a retraction/impact verdict into `status` directly.

## Verify what landed

```bash
histmersion get <citekey>            # metadata + findings
histmersion get <citekey> --full     # + the whole OCR'd body (see the size warning below)
histmersion neighbors <citekey>      # the typed edges
histmersion assets <citekey>         # the figures OCR kept
histmersion lint                     # 13 corpus-integrity rules
histmersion search "<title words>"
```

`--full` / `fullText: true` pulls the entire body: mean 450 KB, **max 5.7 MB**, 55 sources over
500 KB. Use `get` without it, or a `search` snippet, unless you truly need the wording.

`export_source {citekey}` returns one source as the Markdown note it would be on disk (frontmatter +
notes body) — useful for eyeballing a round-trip; `histmersion export <dir>` writes the whole mirror,
sources, articles and the three bookkeeping queues.

## Rules

- **Signed integer years.** 3300 BCE → `-3300`. Never a date type, never a string, never "c. 1200 BCE".
- **Citekeys are stable identifiers**: lowercase, `[a-z][a-z0-9_]+`, `author<YEAR>_word` by
  convention (`cline2014_year`, `haak2015_massive`). Changing one breaks every edge pointing at it.
- **Never hand-edit the database.** Everything goes through the CLI/MCP so the edges stay resolved.
- **Enrich, don't replace.** Re-seeding an enriched source refuses to overwrite without `--force`.
  That guard exists for a reason; do not reach for `--force` to make an error go away.
- **A finding is a claim you could pin on a map and bracket on a timeline.** "Trade declined in the
  12th century" is a summary sentence; "Ugarit's last tablets record grain shipments to Hatti,
  c. 1190 BCE" is a finding.
- **Record what is missing** with `queue add acquire "<what>"` rather than a note file.

Reading the corpus back: **`histmersion-query`**. Writing prose from it: **`histmersion-compile`**. Keeping
statuses honest as new work appears: **`histmersion-currency`**.
