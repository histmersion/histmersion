---
name: trailhead
description: Start a new research topic from a search of the research databases (recent reviews first, then the citation graph) — harvest the primary literature, build a prioritized ingest queue, and (optionally under /loop) autonomously ingest+enrich each paper into the corpus. Use when the user wants to learn a NEW topic or open a new line of inquiry (e.g. "learn about Bronze Age collapse", "start on Proto-Indo-European migrations").
allowed-tools: Bash(histmersion:*), mcp__histmersion__*, Agent
---

# Trailhead — open a new topic

Wikipedia is at most a supplementary place to FIND papers; never summarize it as the answer. Everything here
runs through `histmersion` / `mcp__histmersion__*`. There are no `tools/*.py` scripts any more.

## 1. Gather candidate DOIs — research databases first

Start from what the research databases say, not from what an encyclopedia article happens to cite.
In order of weight:

```bash
# a) recent reviews and syntheses: the field's own summary of where consensus stands
histmersion discover search "<topic query> review" --since 2015 --sort cited --limit 25 --not-in-db

# b) the most-cited and the most relevant primary work
histmersion discover search "<topic query>" --sort cited --limit 25 --not-in-db
histmersion discover search "<topic query>" --limit 25 --not-in-db

# c) the citation graph of the best hits: what they build on, and what answered them since
histmersion refs <citekey>                 # backward, once a review is ingested
histmersion followups <citekey|doi>        # forward: confirms / challenges / refutes / supersedes

# d) one DOI you already have a lead on
histmersion discover meta 10.1371/journal.pone.0071004

# e) supplementary only: the DOIs a Wikipedia article cites. Use it to catch a gap, never as the
#    starting point and never above a, b or c — its reference list reflects what its editors chose.
histmersion discover wikirefs "https://en.wikipedia.org/wiki/Late_Bronze_Age_collapse"
```

MCP equivalent: `mcp__histmersion__discover` with `query` (+ `sort: "cited"`, `since`, `notInDb: true`), or `doi`,
or — supplementary only — `wikipediaUrl`.

Every result carries the **citekey it would get** and whether the corpus already holds it.
`--not-in-db` is what replaces the old "list `wiki/sources/` and diff by hand" step — use it, do not
dedup manually.

## 2. Triage before queueing

- Check what the corpus already covers so the new topic connects to it rather than duplicating it:
  `histmersion search "<topic query>" --limit 20` and `histmersion facets topics --prefix <stem>`
  (`mcp__histmersion__search`, `mcp__histmersion__facets`).
- Vet anything dubious: `histmersion assess <doi>` (`mcp__histmersion__assess`). Drop `retracted` outright and
  say why; drop `low-impact` unless it is topically pivotal.
- Rank the survivors by relevance × citations, then split them:

```bash
# things the pipeline can fetch itself → the agent backlog
histmersion queue tasks add "10.1371/journal.pone.0071004 — Kaniewski 2013, climate roots of the LBA collapse" --topic "Queue: bronze_age_collapse"

# things only the user can obtain (in-copyright monographs, paywalled books) → the user's queue
histmersion queue acquire add "Cline, *1177 B.C.* (2014) — buy/borrow → Calibre" --topic "bronze_age_collapse"
```

`mcp__histmersion__queue` with `action: "add"`, `list: "tasks"|"acquire"`, `text`, `topic`.
Never write a queue into a markdown file — see the **bookkeeping** skill.

## 3. Work the queue

`histmersion queue tasks` lists open items with their ids. For each one, invoke the **ingest-paper**
skill (`histmersion ingest <doi>` → enrich with `patch_source` → `refs`/`assess`), then close it:

```bash
histmersion queue done <id>
```

## 4. Give the topic an article

A topic is not opened until it has a concept article that the sources hang off. Check first, then
create or extend — never re-`put` an article that already exists:

```bash
histmersion article list --kind concept "<topic>"      # or mcp__histmersion__list_articles
histmersion article get <slug>                          # mcp__histmersion__get_article
```

- Missing → `mcp__histmersion__put_article` with `kind: "concept"`, a `# Title` heading, and a body that
  cites every source as `[[citekey]]` and links sibling articles as `[[other_slug]]`.
- Exists → `mcp__histmersion__patch_article` with `append` (add the new section) — never `put_article`,
  which erases everything you did not resend.

Follow `docs/writing-guide.md`: exhaust the filter, cite every paragraph, state disagreements with
years, no filler.

## 5. Autonomy

- Record the objective as a Histmersion **goal** so intent survives the session: `put_goal` (MCP) with
  a kebab-case slug, title, `status: seeding` and the right `parentSlug` (one of the standing interests:
  `comparative-religion`, `pie-origins`, `bronze-age`, `historiography`, `theory-currency`; see
  `histmersion goals list`). Link the topic's article and its queue items with `goal_links` /
  `histmersion goals link <slug> --article <a> | --item <id>`, and move the status on
  (seeding → compiled → reviewed) as the work progresses.
- `/loop ingest the next open item in "histmersion queue tasks" for <topic>, enrich it, then close it`
  works the queue one paper per iteration.
- When the queue drains, run **find-followups** on the foundational papers to pull the next
  generation of citing work back into `histmersion queue tasks`. That is how a topic keeps growing.

## Done means

`histmersion queue tasks --topic`-grouped section is empty for the topic, every ingested source has
findings/period/regions (`histmersion lint --rule no-findings --rule no-period`), and the concept article
exists, cites them, and **`/review-article <slug> --fix` returned no MAJOR finding**.
