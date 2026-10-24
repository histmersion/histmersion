# Deterministic new-source search (#3761, the design of #3645; 2026-10-24)

## What it replaces

A research goal's scheduled `acquisition-sweep` searched OpenAlex live with the goal's queries (its title
when it has none), dropped what the corpus or the acquisition list held, and asked the ranking model to
pick from the rest — every time. Nothing remembered what the model had already been shown, and no search
had a date floor, so a goal swept twice re-ranked the same works.

Read from the live server (read-only, `histmersion agent runs --pipeline acquisition-sweep --since 30d`,
the latest 200 of 214 runs, 146 goals):

| | figure |
|---|---|
| runs that called the ranking model | 198 of 200 (2 found nothing) |
| runs whose ranking picked nothing | 78 (39 %) |
| candidates shown to the model / picked | 2 584 / 464 (18 %) |
| input / output tokens | 1 222 887 / 30 145 |
| goals swept twice in the 30 days | 54 — the second sweep re-ranked the first one's results |
| results dropped for want of a DOI / over the sweep's 30-candidate limit | 578 / 725 |

## As built

- **Watches** (V099 `candidate_watches`): for each goal, every saved query (`query` links, else the title)
  on every index of `acquire.candidates.indexes` — OpenAlex (by publication date), Crossref (by deposit
  date, `from-created-date`), Europe PMC (`FIRST_PDATE`), arXiv (newest submissions), CORE (only with
  `acquire.core.api-key`) — plus, for up to `sources` of the goal's sources (linked, or cited by its linked
  articles), the works **citing** each and the newer works of its first and last **authors** (OpenAlex).
  Each watch keeps a **watermark**: the day it was last read. The first read looks back `lookback-days`
  (365); each later one asks only for what is newer. A failed read keeps its watermark, so nothing is
  skipped.
- **Candidates** (V099 `source_candidates`): what the watches found, one row per goal and DOI — keyed by
  the normalised DOI, an arXiv preprint by its DataCite DOI (`10.48550/arxiv.<id>`), so one work found by
  three indexes is one candidate with three `found_by` entries — left out when the corpus or the
  acquisition list (open or done) has it. A state: `new` (unseen), `seen`, `queued`, `dismissed`.
- **The surface**: `GET /api/acquire/candidates`, `POST /api/acquire/candidates/sweep`,
  `POST /api/acquire/candidates/mark`; the MCP tool `candidates` (list the unseen, `mark` + `as`, `sweep`);
  `histmersion candidates [--goal] [--state]`, `candidates sweep <goal> [--dry-run]`, `candidates mark <id>…
  --as seen|queued|dismissed`. An agent or a person only filters.
- **The schedule**: a goal watcher's acquisition-sweep item first runs the sweep (code, no model). No unseen
  candidate → the item is done with "no new candidates … — no agent run", and no run is created. Otherwise
  the run starts; its `discover` step sweeps again (from today's watermark: nearly free) and offers the
  ranking model the **unseen** candidates only; after the ranking a `seen` step marks the picks `queued`
  and every other candidate shown `seen`, so no later sweep offers them again. A server without the
  candidate search (a test's upstream) keeps the live search.
- No model is involved anywhere in the watches, the deduplication or the gate. Tests use fake index
  clients (`CandidateSearchSuite`), an in-memory candidate store (`SchedulerRunSuite`), and the pure
  parsers and rules (`CandidatesSuite`).

## What it should change (estimate; not measured live)

The live server runs the previous build, so the new search has not run against the corpus; this is what
the run records above predict, not a measurement:

- the 54 repeated sweeps would not have called the model unless their watches found works newer than the
  first sweep — at most one model call per goal per new batch, instead of one per schedule tick;
- candidates a ranking rejected are `seen` and never shown again: the 2 120 rejected candidates of these
  runs would not reach a model a second time;
- a work without a DOI from OpenAlex is often found with one by Crossref or Europe PMC, and arXiv preprints
  are kept by their DataCite DOI, so some of the 578 left out for want of a DOI become candidates; the
  30-candidate limit now applies to the unseen ones only, after deduplication.

To measure it once deployed (read-only): `histmersion candidates` for the counts per state, and
`histmersion agent runs --pipeline acquisition-sweep` for how many runs still call the model, compared with
the table above over the same 30 days.
