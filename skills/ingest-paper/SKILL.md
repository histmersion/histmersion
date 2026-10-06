---
name: ingest-paper
description: Ingest one academic paper into the research corpus by DOI/arXiv/title — Histmersion fetches the legal OA PDF, OCRs it and records its reference edges — then ENRICH the source (topics, regions, deep-time period, geo-located findings, status, notes). Use when the user wants to add a specific paper, or after trailhead/find-followups surfaces a DOI to bring in.
allowed-tools: Bash(histmersion:*), {{tool:*}}, Agent
---

# Ingest one paper

The Histmersion database is the source of truth. Nothing here touches files: no `tools/*.py`, no
`sync_db.py`, no editing `wiki/sources/*.md` by hand. `wiki/` is an export mirror.

## 1. Mechanical ingest (no LLM tokens)

```bash
histmersion ingest 10.1038/nature14317                 # DOI
histmersion ingest arxiv:2301.00001                    # arXiv id
histmersion ingest 10.1038/nature14317 --citekey haak2015_massive --arxiv 1502.02783
```

`{{tool:ingest_doi}}` with `id` (+ `citekey`, `arxiv`).

One command does: OpenAlex metadata → legal OA PDF (Unpaywall → CORE → OpenAlex OA → arXiv, with the
unauthorised-mirror fallback only when `acquire.unlicensed.enabled`) → OCR job keeping figures → `references` edges
from Crossref → an assessment. It is **idempotent**: re-ingesting an existing citekey refreshes
metadata and never wipes enrichment.

- Only a title? Resolve it first: `histmersion discover search "<title>" --limit 5` and pick the match.
- No OA copy anywhere? The source is still created from metadata and the report says so. Ask the
  user for the file, then use the **import-file** skill (`histmersion ingest <path> --citekey <k>`).
- OCR runs as a job. Follow it with `histmersion jobs` / `histmersion jobs <id>` (`{{tool:job_status}}`);
  a job that says `interrupted` just needs the same ingest re-run.

## 2. Enrich — the compile step, and the point of the corpus

**First check whether the server already queued it.** With embedded agents on and
`agents.auto-enrich` on (the default once agents are on), the import job itself queues an
`enrich-source` run for a new source: its progress line (`histmersion jobs <job id>`,
`{{tool:job_status}}`) reads `enrichment queued: run <id>`. Follow it with
`histmersion agent show <id>` (`{{tool:run_status}}`) and review what it wrote instead of enriching
in parallel. With no such line (agents off), or `enrichment not queued: <why>` (no model can run, the
source already had findings or notes), enrich by hand as below.

Read the OCR'd text from the database, never a PDF and never a file on disk:

```bash
histmersion get <citekey> --full          # {{tool:get_source}} { citekey, fullText: true }
```

Then write the enrichment back with **`patch_source` only** — `put_source` replaces the whole source
and renumbers every finding, so it is for creation and deliberate re-import, nothing else.

```
{{tool:patch_source}} {
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
  map. A finding with neither a date nor a place is flagged by `histmersion lint`.
- `confidence` is `high | medium | low | contested`.
- `status` is `foundational | current | contested | superseded | debunked | retracted | unassessed`.
- Findings keep stable ids `<citekey>#<n>`, so a wrong one is dropped with
  `findings: { remove: ["haak2015_massive#3"] }` rather than by re-uploading the set.
- Pass `ifUpdatedAt` (the `updatedAt` you just read) when a long enrichment might race someone else.

The CLI can do the same for small edits:

```bash
histmersion patch <citekey> --topics a,b --regions "Anatolia" --period -1300,-1100 --status current \
  --summary "..." --add-finding '{"claim":"...","region":"Ugarit","lon":35.8,"lat":35.6,"periodStart":-1200,"periodEnd":-1180,"confidence":"high"}'
```

## 3. Connect it

```bash
histmersion refs <citekey> --apply          # Crossref reference list → `references` edges
histmersion assess <citekey> --apply        # retraction/correction/impact verdict → status
histmersion neighbors <citekey>             # what the corpus already links to it
```

(`{{tool:refs}}`, `{{tool:assess}}`, `{{tool:neighbors}}`.)

If a verdict comes back `retracted`, keep the source, leave `status: retracted`, and never present
its claims as current. Then consider the **find-followups** skill to catch superseding work.

If the paper introduces a concept the corpus has no article for, create or extend one —
`{{tool:list_articles}}` first, then `put_article` (new) or `patch_article` with `append`
(existing). Cite the source as `[[citekey]]`; that is the only thing that maintains the link graph.

## 3.5. Extract events and places

Read the source's summary and findings. For each historical event mentioned (battles, migrations,
destructions, polity formations, natural disasters, treaties, etc.):

1. **Check for existing events:** `{{tool:events}} {query: "<name>"}` (CLI: `histmersion events search "<name>"`)
2. **If the event exists:** add this source as an additional dating via `PATCH $HISTMERSION_PUBLIC_URL/api/events/{id}`
   with `datings: [{eventId, citekey, timeSpan, method, note}]`. If the source's dates differ,
   the stored timeSpan should be the **intersection** (narrowest agreed range).
3. **If the event is new:** create it via `PUT $HISTMERSION_PUBLIC_URL/api/events/{id}` with:
   - `id`: descriptive slug (`battle_of_kadesh`, not `event_1`)
   - `label`: human-readable name
   - `kind`: `battle | migration | destruction | polity_formation | natural_disaster | diplomatic_era |
     war | invasion | collapse | technological_change | cultural_shift | founding`
   - `timeSpan`: 4-point `{notBefore, beginBy, endFrom, notAfter}` — use `beginBy`/`endFrom` for
     the core range and `notBefore`/`notAfter` for the outer bounds when uncertain
   - `historicity`: `attested | legendary | mythological | disputed`
   - `placeId`: gazetteer id if available (`{{tool:places}} {name: "<place>"}`)
   - `citekey`: the source that attests this event
   - `parentId`: parent event if hierarchical (e.g., `destruction_of_pylos` → `mycenaean_palace_destructions`)
   - `description`: one sentence from the source's own words

**Deduplication rules:**
- Same event with different names → one entry with the most common name, alternative names in the
  description. Example: "Battle of Djahy" = "Battle of the Delta" → use the commoner name.
- Same place with different names → one gazetteer entry, record alternatives via `place_names`.
- Multiple sources with different dates → `timeSpan` stores the intersection; individual source
  dates go in `datings[]`.

## 4. Put the original in Calibre

Calibre is the canonical store for originals. After a fetch:

```bash
histmersion calibre push <citekey>          # Calibre GUI must be closed
```

or run the **sync-calibre** skill in bulk. The `histmersion calibre gaps --quiet` hook reminds you.

## Done means

`histmersion get <citekey>` shows a real summary, ≥1 topic, ≥1 region, a period, ≥1 located finding and a
non-`unassessed` status; `histmersion lint --rule no-findings --rule no-period --rule unassessed-status`
does not list it; `histmersion calibre gaps` does not list it; and any historical events mentioned in
the source have been created or updated in the events database with this source as a dating.

## Writing rules

The `notes` body and any article you touch follow `docs/writing-guide.md` (mirrored from
`<histmersion repo>/docs/writing-guide.md`): every paragraph cites a `[[citekey]]`, specialist
terms defined on first use, disagreements stated with years, zero filler. If you extended an
article, finish with `/review-article <slug> --fix` — the panel, not the writer, signs it off.
