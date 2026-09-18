---
name: report
description: Answer a complex research question against the corpus and file the answer back as a `report` article rendered at /article/{slug} — with atlas deep links instead of static images. Use when the user asks a research question, wants a comparison across cultures/regions/periods, or wants a map/timeline view rather than terminal text.
allowed-tools: Bash(histdb:*), mcp__hist-db__*, Read, Write, Agent
---

# Report — answer a question, then file it

The answer is compiled **out of the database** and filed back **into** it as an article. No DuckDB
SQL, no matplotlib, no Marp: the map and the timeline are the live atlas, linked by URL.

Read `docs/writing-guide.md` before writing. It is the contract, not a suggestion.

## 1. Learn the vocabulary, then exhaust the filter

Guessing a tag returns nothing. Start from the facets:

```bash
histdb facets tags --prefix sea
histdb facets places --prefix anat
histdb facets topics
```

(`mcp__hist-db__facets` with `field: "tags"|"authors"|"places"|"topics"|"status"`.)

Then run the filter and **page through the whole result set** — an answer built from three sources
when the filter returned forty is wrong even if every sentence is true:

```bash
histdb search "sea peoples -egypt" --tag lba_collapse --from -1300 --to -1100 --limit 50
histdb search --tag lba_collapse --from -1300 --to -1100 --limit 50 --offset 50
```

## 2. See where and when the evidence sits, before reading anything

```bash
histdb search --tag lba_collapse --group time,region --depth 2 --samples 3
histdb search --tag lba_collapse --group region,time --depth 2 --as-of -1200
histdb histogram --tag lba_collapse --from -1400 --to -1000
histdb geo-when --place "Anatolia" --from -1300 --to -1100
```

(`mcp__hist-db__search` with `groupBy`/`depth`/`asOf`, `mcp__hist-db__histogram`,
`mcp__hist-db__geo_when`.) Counts in a tree are **non-additive** — a source is counted in every
bucket its period overlaps — so read the double-counting line the tree prints.

`histdb query "<one read-only SELECT>"` (`mcp__hist-db__query`) covers cross-tabulations the filters
cannot express; `histdb schema` lists what it can read. Deep time is an `int8range` of signed years:
the inclusive upper bound is `upper(period) - 1`.

## 3. Read the sources and hunt the contradictions

```bash
histdb get <citekey>            # frontmatter + findings
histdb get <citekey> --full     # the OCR'd text
histdb neighbors <citekey>      # typed edges: cites / challenges / refutes / supersedes / updates
```

Run `neighbors` on every source you lean on and report what comes back. A `superseded`, `debunked`
or `retracted` status must be stated **in the text** where the source is cited — never silently
dropped and never silently used.

Only search the open web to fill a gap the corpus cannot answer — and if you do, ingest the source
(**ingest-paper**) so the answer adds up into the corpus instead of evaporating.

## 4. Draft, then file it as an article

Write the draft to a local file (this is the one place this skill writes to disk), then upload it:

```bash
histdb article list --kind report "<topic>"    # does it already exist?
histdb article get <slug>                       # read before you touch it
histdb article put --kind report /tmp/<slug>.md
histdb article patch <slug> --append-file /tmp/<slug>-addendum.md
```

MCP: `mcp__hist-db__list_articles`, `get_article`, `put_article` (create only),
`patch_article` with `append` (extend) or `body` (deliberate rewrite). **Never `put_article` on an
existing slug** — it erases everything you did not resend.

The six sections, in order: the question (with the filter you ran) · what the sources say, grouped by
period then region · disagreements · what is unresolved · sources used · not used (with reasons).
Sections 3 and 4 are not optional.

Links are Obsidian wikilinks in the body and are the **only** thing that maintains the graph:
`[[citekey]]` cites a source (it becomes a link and appears in the "Sources cited" footer),
`[[other_slug]]` points at another article (and shows up in its backlinks). Cross-link the report
from the concept article it belongs to with `patch_article --append`.

## 5. Link the views instead of rendering them

The atlas is a live map × timeline whose whole state lives in the URL hash, so a link *is* the
figure. Keys mirror the filter one-to-one and repeat for arrays:
`q`, `tag`, `author`, `place`, `placeId`, `topic`, `status`, `yearFrom`, `yearTo`, `from`, `to`,
`bbox` (`minLon,minLat,maxLon,maxLat`), `inView`, `sort`.

```markdown
[The evidence, 1300–1100 BCE](http://localhost:8000/#tag=lba_collapse&from=-1300&to=-1100)
[Anatolia in view](http://localhost:8000/#place=Anatolia&from=-1300&to=-1100&inView=true)
[The report itself](http://localhost:8000/article/lba_collapse_causes)
```

Paste the deep link next to the claim it supports. `histdb histogram` already returns a Markdown
table plus a bar chart if the report needs the numbers inline. Use the server URL from
`histdb health` / `/api/config` if it is not the default.

## Done means

Every source the filter returned is used or listed under "Not used" with a reason; every paragraph
cites a `[[citekey]]`; disagreements and unresolved questions each have a section; the article is
filed and linked from its concept article; **`/review-article <slug> --fix` has run and returned no
MAJOR finding** (the panel, not the writer, decides that the rules were followed); and
`histdb queue log add "report: <slug> — <one line>"` records it.
