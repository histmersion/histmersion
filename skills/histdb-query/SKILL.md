---
name: histdb-query
description: Answer a research question against the hist-db corpus using the MCP tools (search → facets → neighbors → get_source), citing citekeys. Use whenever a question touches comparative mythology, folklore, Bronze-Age history or Indo-European origins/migrations — instead of grepping markdown files or answering from memory.
---

# Querying hist-db

hist-db holds the OCR'd scholarly corpus in Postgres/PostGIS with geo-located, deep-time-scoped
findings and typed citation edges. **Never grep the markdown corpus and never answer from memory** —
the whole point of the database is that place, period and provenance are structured data.

Two equivalent surfaces, same handlers, same Markdown output:

| surface | use it when |
|---|---|
| **MCP tools** `mcp__hist-db__*` | you are an agent with the plugin/MCP server configured — the default |
| **CLI** `histdb …` | in a shell, in a script, or when MCP is unavailable |

If neither works, the server is down: `docker compose up -d` in the hist-db repo, then `histdb health`.

## The tools

| MCP tool | CLI | returns | when to use |
|---|---|---|---|
| `ping` | `histdb health` | server + schema version | first call if anything looks wrong |
| `search` | `histdb search "<q>"` | matching sources | the entry point for any topic question |
| `get_source` | `histdb get <citekey> [--full]` | one source: metadata, summary, findings, optionally the whole body | after search, to read what a source actually says |
| `geo_when` | `histdb geo-when --region --from --to --bbox` | findings at a place and/or in a deep-time window | "what happened in Anatolia 1300–1100 BCE" questions |
| `neighbors` | `histdb neighbors <citekey>` | typed edges to/from a source | **always**, before you rely on a claim |

Deep time is a **signed integer year**: 3300 BCE → `-3300`, 1200 BCE → `-1200`, 1500 CE → `1500`.
There are no dates, no eras, no strings — just signed years.

> Arriving in wave 1 (do not assume they exist yet; `ping` and the tool list are the truth):
> `facets` (counts per tag/region/period for the current filter), richer `search` filters
> (`--tag`, `--region`, `--from/--to`, `--status`), `aggregate`/`histogram`, and the write tools
> (`put_source`, `patch_source`). Check the MCP tool list rather than guessing.

## The loop

1. **Search broad.** `search` with the plainest phrasing of the question. Note the total.
2. **Narrow with facets** (when available) or with `geo_when` bounded by region and period. The goal
   is a filter whose result set you can enumerate completely — see `docs/writing-guide.md` rule 1.
3. **Read the sources.** `get_source` for each hit. Use `full_text` only when the summary and
   findings are not enough; bodies run to hundreds of KB.
4. **Check the neighbourhood.** `neighbors` on every source you are about to lean on. Edges are typed
   `cites` / `updates` / `challenges` / `refutes` / `supersedes`. A `refutes` or `supersedes` edge
   pointing at your source changes the answer.
5. **Check status.** Every source carries `status`: `foundational` · `current` · `superseded` ·
   `debunked` · `retracted` · `unassessed`. Say so in the text when it is not `current`/`foundational`.
6. **Answer with citations.** Every paragraph cites a citekey. Follow `docs/writing-guide.md`.

## Worked example

> "Did the Sea Peoples cause the Late Bronze Age collapse?"

```
search "sea peoples late bronze age collapse"        → 14 sources
geo_when --region "Levant" --from -1250 --to -1100   → 31 findings, 9 sources
get_source cline2014                                  → the multi-causal argument
neighbors cline2014                                   → `updates` → knapp2020; `challenges` → drews1993
get_source drews1993 / get_source knapp2020           → the two competing positions
```

The answer names the disagreement (Drews's military-technology explanation vs. Cline's systems
collapse vs. Knapp & Manning's climate reassessment), gives the years, and lists all 14 sources —
including the five not used, with reasons.

## Rules

- **Cite everything.** `[[citekey]]` in markdown; bare citekey in terminal output. A paragraph with
  no citekey does not ship.
- **Never invent a citekey.** If `get_source` returns "No source", it is not in the corpus.
- **Never infer geography or dates.** Coordinates and periods come from findings, not from you.
- **Report the gaps.** If the corpus cannot answer part of the question, say which part and what kind
  of source would settle it. Do not fill it from background knowledge.
- **Prefer the atlas for shape questions.** `open $HISTDB_PUBLIC_URL` gives the map × timeline view;
  a link to it is often a better answer than a paragraph.

Writing the answer up: **`docs/writing-guide.md`** (structure, banned filler, contradiction hunting).
Adding sources to the corpus: the **`histdb-ingest`** skill.
