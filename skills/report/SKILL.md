---
name: report
description: Answer a complex research question against the corpus and file the answer back as a `report` article rendered at /article/{slug} — with web UI deep links instead of static images. Use when the user asks a research question, wants a comparison across cultures/regions/periods, or wants a map/timeline view rather than terminal text.
allowed-tools: Bash(histmersion:*), {{tool:*}}, Read, Write, Agent
---

# Report — answer a question, then file it

The answer is compiled **out of the database** and filed back **into** it as an article. No DuckDB
SQL, no matplotlib, no Marp: the map and the timeline are the live web UI, linked by URL.

Read `docs/writing-guide.md` before writing. It is the contract, not a suggestion.

## 1. Learn the vocabulary, then exhaust the filter

Guessing a tag returns nothing. Start from the facets:

```bash
histmersion facets tags --prefix sea
histmersion facets places --prefix anat
histmersion facets topics
```

(`{{tool:facets}}` with `field: "tags"|"authors"|"places"|"topics"|"status"`.)

Then run the filter and **page through the whole result set** — an answer built from three sources
when the filter returned forty is wrong even if every sentence is true:

```bash
histmersion search "sea peoples -egypt" --tag lba_collapse --from -1300 --to -1100 --limit 50
histmersion search --tag lba_collapse --from -1300 --to -1100 --limit 50 --offset 50
```

## 2. See where and when the evidence sits, before reading anything

```bash
histmersion search --tag lba_collapse --group time,region --depth 2 --samples 3
histmersion search --tag lba_collapse --group region,time --depth 2 --as-of -1200
histmersion histogram --tag lba_collapse --from -1400 --to -1000
histmersion geo-when --place "Anatolia" --from -1300 --to -1100
```

(`{{tool:search}}` with `groupBy`/`depth`/`asOf`, `{{tool:histogram}}`,
`{{tool:geo_when}}`.) Counts in a tree are **non-additive** — a source is counted in every
bucket its period overlaps — so read the double-counting line the tree prints.

`histmersion query "<one read-only SELECT>"` (`{{tool:query}}`) covers cross-tabulations the filters
cannot express; `histmersion schema` lists what it can read. Deep time is an `int8range` of signed years:
the inclusive upper bound is `upper(period) - 1`.

## 3. Read the sources and hunt the contradictions

```bash
histmersion get <citekey>            # frontmatter + findings
histmersion get <citekey> --full     # the OCR'd text
histmersion passages <citekey> "<words>"  # where in that text it says so (offsets for get --offset)
histmersion neighbors <citekey>      # typed edges: cites / challenges / refutes / supersedes / updates
```

Run `neighbors` on every source you lean on and report what comes back. A `superseded`, `debunked`
or `retracted` status must be stated **in the text** where the source is cited — never silently
dropped and never silently used.

Only search the open web to fill a gap the corpus cannot answer — and if you do, ingest the source
(**ingest-paper**) so the answer adds up into the corpus instead of evaporating.

## 4. Draft, then file it as an article

Write the draft to a local file (this is the one place this skill writes to disk), then upload it:

```bash
histmersion article list --kind report "<topic>"    # does it already exist?
histmersion article get <slug>                       # read before you touch it
histmersion article put --kind report /tmp/<slug>.md
histmersion article patch <slug> --append-file /tmp/<slug>-addendum.md
```

MCP: `{{tool:list_articles}}`, `get_article`, `put_article` (create only),
`patch_article` with `append` (extend) or `body` (deliberate rewrite). **Never `put_article` on an
existing slug** — it erases everything you did not resend.

The sections, in order: the question (with the filter you ran) · what the sources say, grouped by
period then region · disagreements · what is unresolved. Sections 3 and 4 are not optional. Record
the filter with the article and a reason for each source it leaves out
(`histmersion article patch <slug> --filter-query … --reason <ck>=<why>`, or `patch_article` with
`filter`/`reasons`): the page generates the Not used list, its counts and the `_Method_` note from
them — never write those into the body.

Links are Obsidian wikilinks in the body and are the **only** thing that maintains the graph:
`[[citekey]]` cites a source (it becomes a link and appears in the "Sources cited" footer),
`[[other_slug]]` points at another article (and shows up in its backlinks). Cross-link the report
from the concept article it belongs to with `patch_article --append`.

## 5. Link the views instead of rendering them

The web UI is a live map × timeline whose whole state lives in the URL hash, so a link *is* the
figure. Keys mirror the filter one-to-one and repeat for arrays:
`q`, `tag`, `author`, `place`, `placeId`, `topic`, `status`, `yearFrom`, `yearTo`, `from`, `to`,
`bbox` (`minLon,minLat,maxLon,maxLat`), `inView`, `sort`.

```markdown
[The evidence, 1300–1100 BCE](http://localhost:8000/#tag=lba_collapse&from=-1300&to=-1100)
[Anatolia in view](http://localhost:8000/#place=Anatolia&from=-1300&to=-1100&inView=true)
[The report itself](http://localhost:8000/article/lba_collapse_causes)
```

Paste the deep link next to the claim it supports. `histmersion histogram` already returns a Markdown
table plus a bar chart if the report needs the numbers inline. Use the server URL from
`histmersion health` / `/api/config` if it is not the default.

## Done means

Every source the filter returned is cited or has a reason (`histmersion article coverage <slug>` lists
none "neither cited nor given a reason"); every paragraph
cites a `[[citekey]]`; disagreements and unresolved questions each have a section; the article is
filed and linked from its concept article; **`/review-article <slug> --fix` has run and returned no
MAJOR finding** (the panel, not the writer, decides that the rules were followed); and
`histmersion queue log add "report: <slug> — <one line>"` records it.
