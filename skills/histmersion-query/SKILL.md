---
name: histmersion-query
description: Answer a research question against the Histmersion corpus with the MCP tools or the histmersion CLI (search → facets → get_source → neighbors → followups), citing citekeys. Use whenever a question touches comparative mythology, folklore, Bronze-Age history or Indo-European origins/migrations — instead of grepping markdown files or answering from memory.
---

# Querying Histmersion

Histmersion holds the OCR'd scholarly corpus in Postgres/PostGIS: 209 sources with geo-located,
deep-time-scoped **findings**, a ~55,000-place **gazetteer**, 38 compiled **articles**, and typed
citation **edges**. **Never grep the markdown corpus and never answer from memory** — the point of
the database is that place, period, status and provenance are structured data.

Two surfaces over the same handlers, same Markdown output:

| surface | use it when |
|---|---|
| **MCP tools** `{{tool:*}}` | you are an agent with the plugin/MCP server configured — the default |
| **CLI** `histmersion …` | in a shell, a script, or when MCP is unavailable |

Server down? `just restart` in the Histmersion repo (macOS: `launchctl kickstart -k gui/$(id -u)/com.histmersion.server`), then `histmersion health` / MCP `ping`.

Deep time is a **signed integer year**: 3300 BCE → `-3300`, 1200 BCE → `-1200`, 1500 CE → `1500`.
No dates, no eras, no strings.

## The read tools (29 tools exist; these are the ones for answering questions)

| MCP tool | CLI | returns | when to use |
|---|---|---|---|
| `ping` | `histmersion health` | server + schema version | first call when anything looks wrong |
| `search` | `histmersion search "<q>"` | matching sources, each with the findings that matched and why | **the entry point.** Also `groupBy` for a tree of counts; `verbose: false` for compact listing |
| `facets` | `histmersion facets <field>` | the vocabulary of `tags`\|`authors`\|`places`\|`topics`\|`status` inside the current filter, with distinct-source counts | **before guessing a tag** — an unknown tag returns nothing |
| `get_source` | `histmersion get <ck> [--section toc\|body\|findings\|notes\|all] [--heading "…"] [--offset N --chars M]` | metadata, summary, findings, optionally a body section/slice | after search, to read what a source says; use `section` to avoid loading the whole body |
| `neighbors` | `histmersion neighbors <ck> [--kind challenges]` | typed edges to/from a source, split into Sources and Topics | before relying on a claim |
| `contradictions` | `histmersion contradictions [ck] [--since YEAR]` | disagreement edges (challenges/refutes/updates/superseded_by) and contested statuses | "what is disputed?" — focused view of the sparse disagreement graph |
| `geo_when` | `histmersion geo-when …` | individual findings by place / bbox / deep time | "what happened *here*, *then*" |
| `histogram` | `histmersion histogram …` | sources per adaptive time bucket, table + bars | "when is the evidence" |
| `places` | `histmersion places search\|show\|children` | the gazetteer: a name → a place **id**, and what it contains | **before filtering by a region** |
| `list_articles` / `get_article` | `histmersion article list\|get` | the compiled wiki | before compiling anything — the topic may already have an article |
| `followups` | `histmersion followups <ck> --since Y` | citing papers with intents and a proposed edge kind | "has this been challenged?" — a **live network call** |
| `assess` | `histmersion assess <ck>` | retraction / correction / impact verdict | before citing anything unchecked |
| `discover` | `histmersion discover search\|meta\|wikirefs` | literature we do **not** have | filling a gap the corpus cannot answer |
| `query` | `histmersion query "<SQL>"` | one read-only SQL statement | questions the other tools cannot express |
| `lint` | `histmersion lint` | 13 corpus-integrity rules | after ingestion, or to explain odd edges |
| `queue` | `histmersion queue <list>` | the acquire / tasks / log bookkeeping | recording a gap instead of writing a file |
| `merges` | `histmersion merge list\|show\|ledger` | merges from other collections; one merge's report; a record's ledger (which record over there it came from) | "where did this source come from?" after a merge |

Write tools (`put_source`, `patch_source`, `put_article`, `patch_article`, `ingest`, `ingest_doi`,
`archive`, `refs`, `backup`, …) all exist too — see **`histmersion-ingest`** and **`histmersion-compile`**.
Nothing in the tool list is "coming in a later wave"; the live `tools/list` is the truth.

## Parameter names: MCP vs CLI

All three filter-based tools (`search`, `geo_when`, `histogram`) now accept the full set of
parameters. `periodStart`/`periodEnd` and `region` are deprecated aliases on `geo_when`/`histogram`;
use `from`/`to` and `places` instead.

| concept | MCP (all filter tools) | CLI (all of them) |
|---|---|---|
| deep-time window | `from`, `to` | `--from`, `--to` |
| publication years | `yearFrom`, `yearTo` | `--year-from`, `--year-to` |
| place | `places: [id]` | `--place <id>`, `--region <name>` |
| tags / topics / status / authors | `tags`, `topics`, `status`, `authors` | `--tag`, `--topic`, `--status`, `--author` |
| bbox | `minLon/minLat/maxLon/maxLat` | `--bbox minLon,minLat,maxLon,maxLat` |
| sort | `sort` | `--sort` |
| paging | `limit`, `offset` | `--limit`, `--offset` |

Unknown JSON keys on MCP inputs are now **rejected** with a message listing the accepted keys.

## The loop

1. **`facets` first** to learn the vocabulary — `field: "topics"` or `"tags"` — then `search`.
2. **State the filter and its total** (`search {…} → N`). N > 40: narrow with `facets`, state both.
3. **Read the sources.** `get_source` per hit — summary + findings, not `fullText` (see below).
4. **Hunt disagreement** in the order `docs/writing-guide.md` rule 2 fixes: status search →
   `neighbors` → `followups --since` on the foundational ones → the papers' own wording.
5. **Answer with citations**, every paragraph, per `docs/writing-guide.md`.

## How to read a source body without flooding the context

Bodies are **large**: 209 sources, mean 450 KB, **max 5.7 MB** (`beekes2010_etymological`), and 55
sources are over 500 KB. One `fullText: true` call can flood a whole context window.

- **Default: don't.** The `summary` plus the `findings` list is what enrichment produced *for* this
  purpose, and it is what `get_source` returns by default.
- **Use `section`** to fetch only what you need:
  - `section: "toc"` — the table of contents: headings with byte offsets into the body
  - `section: "findings"` or `section: "notes"` — just the structured evidence or the agent's article
  - `section: "body"` with `heading: "Introduction"` — only the body under that heading
  - `section: "body"` with `offset: 0, chars: 4000` — the first 4 000 chars (a trailing hint says how to page)
- Need a specific passage? `search "<exact phrase>"` — hits carry a `>` snippet of the matching
  passage with its heading, which is usually the sentence you wanted.
- Need what ONE source says about a term? `{{tool:passages}} { citekey, query }` (`histmersion passages
  <ck> "<words>"`) is `get --full | grep` in one call: the passages around each match, with the
  heading and the `offset` that `section: "body"` reads on from.
- Need numbers? They are in the findings (`_@ eurasian_steppe (47.0, 40.0) · 6500 BCE–3500 BCE_`).
- Only reach for `fullText: true` (= `section: "body"` without offset, capped at 60 000 chars) on a
  **short** source when a claim genuinely turns on the exact wording.

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

**1. Use `contradictions` for a focused view.**

```
contradictions {since: 2023}
```

This returns every source with a `challenges`/`refutes`/`updates`/`superseded_by` edge AND every
source whose status is contested/superseded/debunked/retracted, filtered by the challenging source's
year. For one source:

```
contradictions {citekey: "heggarty2023_language"}
```

**2. Or search by status** for the broader set:

```
search {status:["contested","superseded","debunked"], yearFrom:2023, verbose:false}   → compact list
```

**3. Prefer the stored edges.** `neighbors` (now split into **Sources** and **Topics** sections) is
a database read — instant, offline, and already curated. But it is **sparse**: across all 209 sources
there are only a handful of source-to-source disagreement edges. Empty `neighbors` output is *not*
evidence that nothing challenged the paper — `contradictions` and `followups` are the answer.

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
| read | `histmersion queue tasks [--all]` | `queue {action:"list", list:"tasks", all:true}` |
| add | `histmersion queue tasks add "<text>" --topic "<t>"` | `queue {action:"add", list:"tasks", text:"…", topic:"…"}` |
| done | `histmersion queue done <id>` | `queue {action:"done", id:<n>}` |
| rewrite | `histmersion queue edit <id> "<text>"` | — |
| remove | `histmersion queue rm <id>` | — |
| export / import | `histmersion queue export <dir>` · `import <dir>` | — |

The CLI's read verb is the **bare list name** — `histmersion queue tasks`, not `histmersion queue show tasks`
(that errors). MCP's is `action: "list"` — `"show"` errors with
`_unknown action 'show' — use list, add or done_`. There is no MCP `rm`/`export`/`import`; the code
stream is aligning them (`docs/plan.md` §6).

## Rules

- **Cite everything.** `[[citekey]]`, the full stored key. `cline2014_year`, not `cline2014`.
- **Never invent a citekey.** `get_source` returning `_No source '<key>'_` means it is not there.
- **Never infer geography or dates.** Coordinates and periods come from findings.
- **Report the gaps** under *Unresolved*, and `queue add acquire` what would fill them.
- **Edges join stored sources.** `neighbors` marks an edge to a work not in the corpus yet as
  *unresolved* (and one naming a compiled article as an article); `lint --rule dangling-edges`
  lists the unresolved ones — each is a candidate to acquire.
- **Prefer the web UI for shape questions.** `open $HISTMERSION_PUBLIC_URL` gives the map × timeline view;
  a link to it is often a better answer than a paragraph.

Writing the answer up: **[`docs/writing-guide.md`](../../docs/writing-guide.md)** and the
**`histmersion-compile`** skill. Adding sources: **`histmersion-ingest`**. Keeping statuses honest:
**`histmersion-currency`**.
