---
name: update-topic
description: Refresh an existing topic by fetching only the DELTA — papers published since the topic was last expanded, plus newly-retracted or superseded sources — and fold them into the corpus and its articles. Use when the user says "update what we know about X", "anything new on X?", or to keep a topic current on a schedule.
allowed-tools: Bash(histdb:*), mcp__hist-db__*, Agent
---

# Update a topic — fetch the delta, not the topic

## 1. Find the watermark

How current is the topic already?

```bash
histdb query "select max(year) as newest, count(*) as n from sources where '<topic_slug>' = any(topics)"
histdb search --topic <topic_slug> --sort year --limit 10
```

(`mcp__hist-db__query`, `mcp__hist-db__search`.) `histdb facets topics --prefix <stem>` lists the
topic vocabulary if you are unsure of the slug — a filter with an unknown topic returns nothing.

## 2. Pull only what is new

```bash
# new literature since the watermark, already deduped against the corpus
histdb discover search "<topic query>" --since <watermark> --not-in-db --limit 25
histdb discover search "<topic query>" --since <watermark> --not-in-db --sort cited --limit 25

# integrity delta
histdb assess update-retractions        # background; re-run or check retractionsStatus for completion
```

(`mcp__hist-db__discover` with `since` + `notInDb: true`; `mcp__hist-db__assess` with
`updateRetractions: true`.)

Then the **forward** delta: run the **find-followups** skill with `--since <watermark>` on the
topic's foundational sources —

```bash
histdb search --topic <topic_slug> --status foundational --limit 20
histdb followups <citekey> --since <watermark>
```

and re-assess the topic's existing sources for newly-flagged retractions/corrections
(`histdb assess <citekey> --apply`).

`--not-in-db` and `--since` are the whole point: do not re-read the topic, only the delta.

## 3. Incorporate

- Queue the genuinely new and relevant DOIs:
  `histdb queue tasks add "10.x/y — <paper>, new since <year>" --topic "Update: <topic_slug>"`.
- Ingest and enrich each (**ingest-paper** skill): `histdb ingest <doi>` → `patch_source` →
  `refs --apply` → `assess --apply`.
- Downgrade anything now superseded or debunked: `histdb patch <citekey> --status superseded` plus a
  `supersedes` edge from the newer source (see **find-followups**).

## 4. See what moved, then update the article

Before rewriting anything, look at the shape of the evidence:

```bash
histdb search --topic <topic_slug> --group time,region --depth 2 --samples 3
histdb histogram --topic <topic_slug>
histdb facets tags --topic <topic_slug>
```

(`mcp__hist-db__search` with `groupBy: "time,region"`, `mcp__hist-db__histogram`,
`mcp__hist-db__facets`.)

Then extend the topic's article — read it first, and **append**, never re-upload:

```bash
histdb article list --kind concept "<topic>"
histdb article get <slug>
```

`mcp__hist-db__patch_article { slug, patch: { append: "## What changed since <year>\n\n…" } }`.
Every paragraph cites a `[[citekey]]`; name the years; state the disagreement rather than smoothing
it. Follow `docs/writing-guide.md`.

## 5. Report the delta

Tell the user what is genuinely new, what shifted the consensus, and what got
retracted/superseded — **not** a re-summary of the whole topic. Then log it:

```bash
histdb queue log add "update-topic <topic_slug>: N new sources, M superseded, retraction sweep clean"
```

## Autonomy

The user can schedule this with **loop**/**schedule**, e.g. a weekly pass over the active topics in
`GOAL.md` that only surfaces non-empty deltas.

## Done means

The delta is ingested and enriched, statuses reflect the new evidence, the topic's article has a
dated section describing what changed, **`/review-article <slug> --fix` on every article touched
returned no MAJOR finding**, and the log entry exists.
