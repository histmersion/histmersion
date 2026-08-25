---
name: histdb-query
description: Answer a research question against the hist-db corpus with the MCP tools or the histdb CLI (search → facets → get_source → neighbors → followups), citing citekeys. Use whenever a question touches comparative mythology, folklore, Bronze-Age history or Indo-European origins/migrations — instead of grepping markdown files or answering from memory.
---

# Querying hist-db

hist-db holds the OCR'd scholarly corpus in Postgres/PostGIS: 209 sources with geo-located,
deep-time-scoped **findings**, a 16,779-place **gazetteer**, 38 compiled **articles**, and typed
citation **edges**. **Never grep the markdown corpus and never answer from memory** — the point of
the database is that place, period, status and provenance are structured data.

Two surfaces over the same handlers, same Markdown output:

| surface | use it when |
|---|---|
| **MCP tools** `mcp__hist-db__*` | you are an agent with the plugin/MCP server configured — the default |
| **CLI** `histdb …` | in a shell, a script, or when MCP is unavailable |

Server down? `docker compose up -d` in the hist-db repo, then `histdb health` / MCP `ping`.

Deep time is a **signed integer year**: 3300 BCE → `-3300`, 1200 BCE → `-1200`, 1500 CE → `1500`.
No dates, no eras, no strings.

## The read tools (29 tools exist; these are the ones for answering questions)

| MCP tool | CLI | returns | when to use |
|---|---|---|---|
| `ping` | `histdb health` | server + schema version | first call when anything looks wrong |
| `search` | `histdb search "<q>"` | matching sources, each with the findings that matched and why | **the entry point.** Also `groupBy` for a tree of counts |
| `facets` | `histdb facets <field>` | the vocabulary of `tags`\|`authors`\|`places`\|`topics`\|`status` inside the current filter, with distinct-source counts | **before guessing a tag** — an unknown tag returns nothing |
| `get_source` | `histdb get <ck> [--full]` | metadata, summary, findings, optionally the whole body | after search, to read what a source says |
| `neighbors` | `histdb neighbors <ck>` | typed edges to/from a source | before relying on a claim |
| `geo_when` | `histdb geo-when …` | individual findings by place / bbox / deep time | "what happened *here*, *then*" |
| `histogram` | `histdb histogram …` | sources per adaptive time bucket, table + bars | "when is the evidence" |
| `places` | `histdb places search\|show\|children` | the gazetteer: a name → a place **id**, and what it contains | **before filtering by a region** |
| `list_articles` / `get_article` | `histdb article list\|get` | the compiled wiki | before compiling anything — the topic may already have an article |
| `followups` | `histdb followups <ck> --since Y` | citing papers with intents and a proposed edge kind | "has this been challenged?" — a **live network call** |
| `assess` | `histdb assess <ck>` | retraction / correction / impact verdict | before citing anything unchecked |
| `discover` | `histdb discover search\|meta\|wikirefs` | literature we do **not** have | filling a gap the corpus cannot answer |
| `query` | `histdb query "<SQL>"` | one read-only SQL statement | questions the other tools cannot express |
| `lint` | `histdb lint` | 13 corpus-integrity rules | after ingestion, or to explain odd edges |
| `queue` | `histdb queue <list>` | the acquire / tasks / log bookkeeping | recording a gap instead of writing a file |

Write tools (`put_source`, `patch_source`, `put_article`, `patch_article`, `ingest`, `ingest_doi`,
`archive`, `refs`, `backup`, …) all exist too — see **`histdb-ingest`** and **`histdb-compile`**.
Nothing in the tool list is "coming in a later wave"; the live `tools/list` is the truth.

## Parameter names: MCP vs CLI (as they are today)

The two surfaces do **not** use the same names for deep time, and `geo_when`/`histogram` differ from
`search`. Read this table rather than guessing:

| concept | MCP `search` / `facets` | MCP `geo_when` / `histogram` | CLI (all of them) |
|---|---|---|---|
| deep-time window | `from`, `to` | `periodStart`, `periodEnd` | `--from`, `--to` |
| publication years | `yearFrom`, `yearTo` | *not available* | `--year-from`, `--year-to` |
| place | `places: [id]` | `region: "<name substring>"` | `--place <id>`, `--region <name>` |
| tags / topics / status / authors | `tags`, `topics`, `status`, `authors` | *not available* | `--tag`, `--topic`, `--status`, `--author` |
| bbox | `minLon/minLat/maxLon/maxLat` | same | `--bbox minLon,minLat,maxLon,maxLat` |

> Two traps, both real today: MCP `geo_when` and `histogram` accept **only** region/bbox/period —
> they **silently ignore** a `tags` or `status` key rather than erroring, so a filtered `geo_when`
> over MCP quietly returns unfiltered results. Use the **CLI** `geo-when`/`histogram` when you need
> those filters, or filter with `search` and read findings from the hits. The code stream is
> unifying both (`docs/plan.md` §6).

## The loop

1. **`facets` first** to learn the vocabulary — `field: "topics"` or `"tags"` — then `search`.
2. **State the filter and its total** (`search {…} → N`). N > 40: narrow with `facets`, state both.
3. **Read the sources.** `get_source` per hit — summary + findings, not `fullText` (see below).
4. **Hunt disagreement** in the order `docs/writing-guide.md` rule 2 fixes: status search →
   `neighbors` → `followups --since` on the foundational ones → the papers' own wording.
5. **Answer with citations**, every paragraph, per `docs/writing-guide.md`.

## When NOT to use `get_source(fullText: true)`

Bodies are **large**: 209 sources, mean 450 KB, **max 5.7 MB** (`beekes2010_etymological`), and 55
sources are over 500 KB. One `fullText` call can flood a whole context window.

- **Default: don't.** The `summary` plus the `findings` list is what enrichment produced *for* this
  purpose, and it is what `get_source` returns without `fullText`.
- Need a specific passage? `search "<exact phrase>"` — hits carry a `>` snippet of the matching
  passage with its heading, which is usually the sentence you wanted.
- Need numbers? They are in the findings (`_@ eurasian_steppe (47.0, 40.0) · 6500 BCE–3500 BCE_`).
- Only reach for `fullText` on a **short** source when a claim genuinely turns on the exact wording,
  and check the size first: `query "select length(ocr_markdown) from sources where citekey = '<ck>'"`.
- The code stream is adding section/page slicing to `get_source` (`docs/plan.md` §6); until then
  there is no way to ask for part of a body except `search` snippets or `query` with `substring()`.

## Recipe A — a topic question, with its disagreements

> "How settled is the steppe homeland for Proto-Indo-European?"

```
facets  {field:"topics", prefix:"steppe"}     → steppe_hypothesis (16), steppe_ancestry (9)
search  {topics:["steppe_hypothesis"]}        → 16 sources
facets  {field:"status", topics:["steppe_hypothesis"]}
```

```
### status (3)

- foundational (6)
- contested (5)
- current (5)
```

Five contested sources in sixteen: the disagreement is where the answer is. Pull them and walk the
edges:

```
search    {topics:["steppe_hypothesis"], status:["contested"]}
neighbors {citekey:"heggarty2023_language"}
```

```
### References · heggarty2023_language

**Outgoing (this → other)**
- _references_ → **anatolian_hypothesis**
- _references_ → **steppe_hypothesis**

**Incoming (other → this)**
- _challenges_ → [[iecor2025_dataset]] — The Indo-European Cognate Relationships dataset
- _challenges_ → [[phylo2026_validity]] — Testing the validity and adequacy of linguistic phylogenetic analyses
- _refutes_ → [[kassian2025_critique]] — Do 'language trees with sampled ancestors' really support a 'hybrid model'…
```

Note the shape: the **outgoing** `references` edges point at topic slugs and mean nothing; the
**incoming** `challenges`/`refutes` edges point at citekeys and are the answer. Finish with
`get_source` on each side, then write it up per `docs/writing-guide.md`.

## Recipe B — a place and a period, with citations

> "What do we have on Ugarit in the last century of the Late Bronze Age?"

Resolve the name to a gazetteer **id** first. A bare name only matches region **text**; an id
matches the place's polygon **and everything nested under it** — and the difference is real:
`search {places:["anatolia"]}` (the id) returns **115** sources, `places:["Anatolia"]` (the name)
returns **94**, and `places:["Anatolian"]` returns none. The filter takes both without saying which
path it took, so resolve the id and use it.

```
places {query:"Ugarit"}
```

```
### 1 place(s)

- **Ugarit** — `ugarit_lba`
  _polity · 1450 BCE–1190 BCE · 16 finding(s) · traced by hand after Bryce, The Routledge Handbook…_
```

```
search {places:["ugarit_lba"], from:-1250, to:-1150}   → 12 sources
```

Each hit lists the findings that matched, with why — `place: ugarit_lba` marks a spatial match,
`period` a temporal one:

```
- **kaniewski2011_peoples** — The Sea Peoples, from Cuneiform Tablets to Carbon Dating
  _2011 · current · Northern Levant, Syria, Ugarit, … · 1215 BCE–1190 BCE_
  matches:
    - Radiocarbon dating of the destruction layer (Level 7A) at Gibala-Tell Tweini … establishes
      1192-1190 BC as the absolute date of the Sea Peoples' terminal destruction…
      _Northern Levant, Syria (35.3717, 35.9368) · 1192 BCE–1190 BCE · period, place: ugarit_lba_
```

Those dates and coordinates are the citable numbers — do not re-derive them. For the shape of the
evidence before reading it: `histogram {region:"Anatolia", periodStart:-2000, periodEnd:-1000}`, or
`search {…, groupBy:"time,region", depth:2}` for a nested tree. Both count **non-additively** — a
source lands in every bucket its period overlaps — so children never sum to their parent.

## Recipe C — "what has been challenged since YEAR"

Three steps, cheapest first.

**1. What does the corpus already know is disputed?**

```
facets {field:"status"}
```

```
- foundational (97)
- current (91)
- contested (17)
- debunked (2)
- superseded (2)
```

```
search {status:["contested","superseded","debunked"], yearFrom:2023}   → 5 sources
```

→ `heggarty2023_language`, `iecor2025_dataset`, `kassian2025_critique`, `phylo2026_validity`,
`sciadv2024_hg_admixture`.

**2. Prefer the stored edges.** `neighbors` is a database read — instant, offline, and already
curated. But it is **sparse**: across all 209 sources there are exactly 6 source-to-source
disagreement edges (2 `challenges`, 1 `refutes`, 3 `updates`, 0 `superseded_by`). Empty `neighbors`
output is *not* evidence that nothing challenged the paper.

**3. Only then `followups`** — a live Semantic Scholar call, seconds per source, and it reaches work
that is not in the corpus at all:

```
followups {id:"gray2003_language", since:2015, limit:10}
```

```
| ★ | title | year | intents | proposed edge | in corpus |
| --- | --- | --- | --- | --- | --- |
|  | Testing the validity and adequacy of linguistic phylogenetic analyses | 2026 |  | **cites** | `phylo2026_validity` |
|  | Applying invasion criterion to cultural evolution. | 2026 |  | **cites** | — |
…
_Re-run with `--apply` to record these as typed edges (only papers already in the corpus get one)._
```

Run it on the **foundational** sources only (`search {topics:[…], status:["foundational"]}`), not on
all N. Two cautions on the output:

- The **proposed** edge kinds are `confirms|challenges|refutes|supersedes|cites` — a *different*
  vocabulary from the kinds actually stored (`references, challenges, refutes, updates,
  superseded_by`). `cites` and `confirms` and `supersedes` are not storable kinds. Never write
  `cites` or `supersedes` in prose; the code stream is reconciling the two lists (`docs/plan.md` §6).
- Rows with `in corpus: —` are acquisition candidates, not evidence. Record them:
  `queue add acquire "<title> — critique of <ck>"`.

## `queue` — the CLI and MCP shapes differ

Same three lists (`acquire` = what the **user** must obtain · `tasks` = the agent's backlog ·
`log` = the dated worklog), different call shapes and different verb coverage.

| | CLI | MCP |
|---|---|---|
| read | `histdb queue tasks [--all]` | `queue {action:"list", list:"tasks", all:true}` |
| add | `histdb queue tasks add "<text>" --topic "<t>"` | `queue {action:"add", list:"tasks", text:"…", topic:"…"}` |
| done | `histdb queue done <id>` | `queue {action:"done", id:<n>}` |
| remove | `histdb queue rm <id>` | — |
| export / import | `histdb queue export <dir>` · `import <dir>` | — |

The CLI's read verb is the **bare list name** — `histdb queue tasks`, not `histdb queue show tasks`
(that errors). MCP's is `action: "list"` — `"show"` errors with
`_unknown action 'show' — use list, add or done_`. There is no MCP `rm`/`export`/`import`; the code
stream is aligning them (`docs/plan.md` §6).

## Rules

- **Cite everything.** `[[citekey]]`, the full stored key. `cline2014_year`, not `cline2014`.
- **Never invent a citekey.** `get_source` returning `_No source '<key>'_` means it is not there.
- **Never infer geography or dates.** Coordinates and periods come from findings.
- **Report the gaps** under *Unresolved*, and `queue add acquire` what would fill them.
- **`lint --rule dangling-edges` explains weird edges** — 1,051 edge targets in the corpus are
  concept slugs or unresolved short citekeys rather than sources. That is expected, not a bug.
- **Prefer the atlas for shape questions.** `open $HISTDB_PUBLIC_URL` gives the map × timeline view;
  a link to it is often a better answer than a paragraph.

Writing the answer up: **[`docs/writing-guide.md`](../../docs/writing-guide.md)** and the
**`histdb-compile`** skill. Adding sources: **`histdb-ingest`**. Keeping statuses honest:
**`histdb-currency`**.
