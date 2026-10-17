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
| a purchased book in Calibre | — (host-local) | `histmersion calibre search "<q>"` (or `--unlinked`: entries not in the corpus yet) → `histmersion calibre import <id> --citekey k` |
| a public archive.org text | `archive {query\|id, import:true, citekey}` | `histmersion archive search "<q>"` · `info <id>` · `import <id> --citekey k` |

> The CLI folds identifier-ingest and file-ingest into one `histmersion ingest`; **MCP splits them into
> two tools** — `ingest_doi` for an identifier, `ingest` for a file. The code stream is aligning
> them (`docs/plan.md` §6).

`ingest_doi` runs metadata → legal OA PDF (Unpaywall → CORE → OpenAlex OA → Europe PMC/PMC → arXiv → a
repository's landing page resolved one step — Zenodo, or an institutional repository's `citation_pdf_url` —
else PMC's JATS full text) → the OCR job → Crossref
`references` edges → assessment. It is idempotent and **never wipes enrichment**.

**Acquisition items close themselves.** A successful import job ticks off the item it names
(`--acquire-item` / `acquireItem`), or — when it names none — every open acquisition item that
contains the source's DOI (any case, prefix or trailing punctuation). Items that only resemble the
title are listed as `possible acquisition item #N (similarity …)` in the job's progress and the
command's output, never closed: check them and `histmersion queue done <id>` the right one.

**Deleting a source** is `histmersion delete <citekey>` — a dry run listing everything that would go
(findings, edges both ways, figures, attestations, old citekeys, the stored original) and what refuses
it (an article citing it, an event sourced to it, a running job); `--apply` does it in one
transaction and logs it. There is no MCP tool and no override: fix the citing articles first. Other
sources' edges to it are parked, not lost; Calibre is never touched.

OCR is asynchronous: `ingest` returns a **job id**. Follow it with `job_status {id}` /
`histmersion jobs <id>` (`queued|running|done|failed`). `--quality` (MCP `quality: true`) picks the slow,
accurate path (docling) for image-only scans. `reocr <citekey>` re-converts a stored original.
`assets {citekey}` lists the figures the OCR kept and the URLs they are served at.

## `seed` — import a directory of markdown notes

```bash
histmersion seed                      # the configured corpus dir (HISTMERSION_CORPUS_DIR)
histmersion seed /path/to/wiki        # sources AND the articles beside them
histmersion seed --force              # also overwrite rows newer than the files
```

The path is resolved **on the host when it exists there, otherwise asked of the server**. With no
path the server reads its configured corpus dir (`HISTMERSION_CORPUS_DIR`, set by `just install`). `seed` upserts by citekey and re-resolves the typed edges afterwards;
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

**Enrichment may already be under way.** With embedded agents on and `agents.auto-enrich` on, every
successful import job (DOI, file, archive.org, Calibre, a re-OCR) queues one `enrich-source` run for
its source, unless the source already has findings or notes, or an earlier enrich run of it is
pending or done. The job's progress line says which: `enrichment queued: run <id>` or
`enrichment not queued: <why>`. The run is an ordinary agent run (budgets, plan floors, approvals),
so follow it with `histmersion agent show <id>` (`{{tool:run_status}}`) rather than enriching the
same source by hand at the same time. Since the process managers' phase 2 the line usually reads
`enrichment queued: item #<n> of the enrich manager` (the `enrich` manager runs it; while the line says `paused`,
nothing runs until a person resumes it — `histmersion agent manager enrich resume`).

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
  edges:    {add:[{dst:"heggarty2023_language", kind:"refutes"}]},   // FROM this source
  ifUpdatedAt: "<the updatedAt you last read>"
}}
```

- Every top-level part is **independently optional**; a present part replaces only itself.
- `tags`/`findings` take `add`/`remove`/`replace`; `edges` takes `add`/`remove`.
- `findings.remove` carries finding **ids** (`<citekey>#<n>`); `add`/`replace` carry values.
- `edges` are stored on their **source**: an edge `a --refutes--> b` is a patch of `a`. `src` may be
  omitted; one naming another citekey is refused. CLI: `--add-edge <kind>:<citekey>` /
  `--remove-edge <kind>:<citekey>`.
- In `metadata`, an omitted field is left alone and `null` **clears** it — `year`, `venue`, `doi`,
  `provenance`, `calibreId` (`{"metadata":{"doi":null}}`); `null` on `title`/`authors`/`status` is
  refused. `calibreId` re-links the source to another Calibre entry (`histmersion calibre link <ck> <id>`
  checks the entry exists first).
- `ifUpdatedAt` is an optimistic-concurrency precondition — HTTP **412** instead of a clobber.
- `put_source` is for **creation and deliberate re-import only**. It replaces everything.

CLI: `histmersion patch <citekey> --topics … --regions … --period -4500,-2000 --status contested
--tags-add … --add-finding '<FindingInput JSON>' --remove-finding 'ck#2' --notes-file f.md
--if-updated-at …`, or `--json '<whole SourcePatch>'`.

## A wrong citekey — rename it, never re-create it

A citekey whose year is not the publication year (`page2024_history` for a 1959 book) misleads every
reader. `rename_source {citekey:"page2024_history", newCitekey:"page1959_history"}` (CLI:
`histmersion rename page2024_history page1959_history`) moves it in one transaction: findings become
`page1959_history#<n>`, every `[[page2024_history]]` in articles and notes is rewritten (each article
keeps its previous text as a version), and the old citekey stays an alias: every read and every write
naming it (put, patch, delete, rename) goes to the source under its new citekey, and the answer names it. Correct the `year` itself with `patch_source` — the rename changes only the identifier. Do
not `put_source` under the new name and delete the old one: that loses the edges, links and history.

An article slug, an event id or a goal slug is fixed the same way: `rename_label {kind:"article",
from:"old_slug", to:"new_slug"}` (CLI: `histmersion article rename old_slug new_slug`, `histmersion events
rename`, `histmersion goals rename`). What points at it follows — every `[[old_slug]]` in other articles
is rewritten as a recorded version — and the old label stays an alias: reads and writes naming it go to
the record under its new label (#1799), and the answer says where it went.

## Another collection — bundles and merging

A second Histmersion (work and home) is merged with a **bundle**, never by re-ingesting its sources.
The user runs it; the agent reads it:

```bash
histmersion bundle export work.hmx                 # over there: the whole collection, identity kept
histmersion merge plan work.hmx                    # here: every match and EVERY issue, in one run (writes nothing)
histmersion merge plan work.hmx --write-template r.yaml   # the decisions, one line per issue id
histmersion merge apply work.hmx --resolutions r.yaml --plan <hash>   # a backup, then one transaction
histmersion merge list · merge show <id> · merge ledger --uid <citekey>   # what was merged, from where
```

The web UI's **`/merge`** screen is the same review with the records side by side (a field table, a
three-way article view with an editor, places and events on a map); its decisions download as the
same resolution file.

- `merge_plan {bundle: <sha256>}` (MCP, read-only) shows a plan the user uploaded; `merges` (read-only)
  lists past merges, shows one, and traces a record through the ledger. **Present a plan; never
  decide it.** Whether two records are one work, which status or edition stands, which article text
  survives — those are the user's calls. `merge apply` is deliberately not an MCP tool.
- A bundle carries sources (with OCR bodies and originals), findings, edges, articles (with versions
  and old slugs), assets, attestations, events, goals (plan, links, schedules), the queues, and the
  places and named periods this collection added itself (`places import` / `periods import`, dataset
  `local`); the bundled gazetteer and period presets are not carried.
- After an apply, every merged article is queued as `/review-article <slug> --fix` in `tasks` — a
  merged text is not reviewed. Run the gate on each.

## Edge kinds — use the real ones

Stored kinds: **`references`, `challenges`, `refutes`, `updates`, `superseded_by`**. There is no
`cites` edge and no `supersedes` edge in the corpus.

`followups` *proposes* a different vocabulary (`confirms|challenges|refutes|supersedes|cites`).
Translate before recording: `cites`/`confirms` → `references`, `supersedes` → `superseded_by` on
the older source. `refs --apply` and `followups --apply` write edges for you — check what they wrote
with `neighbors`. (The two vocabularies are handed to the code stream, `docs/plan.md` §6.)

Edges connect **stored sources** only. A topic slug linked from a source's notes
(`[[bronze_age_collapse]]`) is a topic, recorded in `topics`, not an edge. An edge to a work that is
not in the corpus yet is kept aside as *unresolved* (`neighbors` marks it so) and becomes a real
edge the moment that source is ingested; `lint --rule dangling-edges` lists the ones still waiting.

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
500 KB. Use `get` without it, or a `search` snippet, unless you truly need the wording; to find a
term inside one body, `histmersion passages <citekey> "<words>"` instead of `--full | grep`.

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
