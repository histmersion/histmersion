---
name: ingest-paper
description: Ingest one academic paper into the research corpus by DOI/arXiv/title — hist-db fetches the legal OA PDF, OCRs it and records its reference edges — then ENRICH the source (topics, regions, deep-time period, geo-located findings, status, notes). Use when the user wants to add a specific paper, or after trailhead/find-followups surfaces a DOI to bring in.
allowed-tools: Bash(histdb:*), mcp__hist-db__*, Agent
---

# Ingest one paper

The hist-db database is the source of truth. Nothing here touches files: no `tools/*.py`, no
`sync_db.py`, no editing `wiki/sources/*.md` by hand. `wiki/` is an export mirror.

## 1. Mechanical ingest (no LLM tokens)

```bash
histdb ingest 10.1038/nature14317                 # DOI
histdb ingest arxiv:2301.00001                    # arXiv id
histdb ingest 10.1038/nature14317 --citekey haak2015_massive --arxiv 1502.02783
```

`mcp__hist-db__ingest_doi` with `id` (+ `citekey`, `arxiv`).

One command does: OpenAlex metadata → legal OA PDF (Unpaywall → CORE → OpenAlex OA → arXiv, with the
unauthorised-mirror fallback only when `acquire.unlicensed.enabled`) → OCR job keeping figures → `references` edges
from Crossref → an assessment. It is **idempotent**: re-ingesting an existing citekey refreshes
metadata and never wipes enrichment.

- Only a title? Resolve it first: `histdb discover search "<title>" --limit 5` and pick the match.
- No OA copy anywhere? The source is still created from metadata and the report says so. Ask the
  user for the file, then use the **import-file** skill (`histdb ingest <path> --citekey <k>`).
- OCR runs as a job. Follow it with `histdb jobs` / `histdb jobs <id>` (`mcp__hist-db__job_status`);
  a job that says `interrupted` just needs the same ingest re-run.

## 2. Enrich — the compile step, and the point of the corpus

Read the OCR'd text from the database, never a PDF and never a file on disk:

```bash
histdb get <citekey> --full          # mcp__hist-db__get_source { citekey, fullText: true }
```

Then write the enrichment back with **`patch_source` only** — `put_source` replaces the whole source
and renumbers every finding, so it is for creation and deliberate re-import, nothing else.

```
mcp__hist-db__patch_source {
  citekey: "haak2015_massive",
  patch: {
    summary: "2-4 sentences in your own words, replacing the raw abstract.",
    topics:  ["proto_indo_european", "steppe_migration"],
    regions: ["Pontic-Caspian steppe", "Central Europe"],
    period:  { start: -4500, end: -2000 },
    tags:    { add: ["adna", "yamnaya", "corded-ware"] },
    metadata:{ status: "foundational" },
    findings:{ add: [ {
      claim: "Corded Ware individuals derive ~75% of their ancestry from Yamnaya-related steppe populations.",
      region: "Central Europe", lon: 11.6, lat: 51.0,
      periodStart: -2900, periodEnd: -2350, periodName: "Corded Ware",
      tags: ["adna", "migration"], confidence: "high"
    } ] },
    notes: "# Notes\n\nYour synthesis, citing [[other_citekey]] and linking [[concept_slug]] articles."
  }
}
```

Rules that matter:

- **Deep time is signed integer years, BCE negative.** 3300 BCE → `-3300`. Never a date string.
- **Findings carry lat/lon** wherever the paper names a place — that is what puts the claim on the
  atlas map. A finding with neither a date nor a place is flagged by `histdb lint`.
- `confidence` is `high | medium | low | contested`.
- `status` is `foundational | current | contested | superseded | debunked | retracted | unassessed`.
- Findings keep stable ids `<citekey>#<n>`, so a wrong one is dropped with
  `findings: { remove: ["haak2015_massive#3"] }` rather than by re-uploading the set.
- Pass `ifUpdatedAt` (the `updatedAt` you just read) when a long enrichment might race someone else.

The CLI can do the same for small edits:

```bash
histdb patch <citekey> --topics a,b --regions "Anatolia" --period -1300,-1100 --status current \
  --summary "..." --add-finding '{"claim":"...","region":"Ugarit","lon":35.8,"lat":35.6,"periodStart":-1200,"periodEnd":-1180,"confidence":"high"}'
```

## 3. Connect it

```bash
histdb refs <citekey> --apply          # Crossref reference list → `references` edges
histdb assess <citekey> --apply        # retraction/correction/impact verdict → status
histdb neighbors <citekey>             # what the corpus already links to it
```

(`mcp__hist-db__refs`, `mcp__hist-db__assess`, `mcp__hist-db__neighbors`.)

If a verdict comes back `retracted`, keep the source, leave `status: retracted`, and never present
its claims as current. Then consider the **find-followups** skill to catch superseding work.

If the paper introduces a concept the corpus has no article for, create or extend one —
`mcp__hist-db__list_articles` first, then `put_article` (new) or `patch_article` with `append`
(existing). Cite the source as `[[citekey]]`; that is the only thing that maintains the link graph.

## 4. Put the original in Calibre

Calibre is the canonical store for originals. After a fetch:

```bash
histdb calibre push <citekey>          # Calibre GUI must be closed
```

or run the **sync-calibre** skill in bulk. The `histdb calibre gaps --quiet` hook reminds you.

## Done means

`histdb get <citekey>` shows a real summary, ≥1 topic, ≥1 region, a period, ≥1 located finding and a
non-`unassessed` status; `histdb lint --rule no-findings --rule no-period --rule unassessed-status`
does not list it; and `histdb calibre gaps` does not either.

## Writing rules

The `notes` body and any article you touch follow `docs/writing-guide.md` (mirrored from
`/Users/me/Workspaces/Github/hist-db/docs/writing-guide.md`): every paragraph cites a `[[citekey]]`, specialist
terms defined on first use, disagreements stated with years, zero filler. If you extended an
article, finish with `/review-article <slug> --fix` — the panel, not the writer, signs it off.
