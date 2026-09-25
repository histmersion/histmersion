---
name: lint-wiki
description: Run health checks over the corpus — dangling citation edges, un-enriched sources, missing periods/regions/findings, empty summaries, duplicate DOIs, orphans, undated or unlocated findings — plus the judgement checks (contradictions, outdated statuses, missing articles). Use to clean up data integrity and surface follow-up questions after a batch of ingestion.
allowed-tools: Bash(histmersion:*), mcp__histmersion__*
---

# Lint the corpus

There is nothing to grep any more: the checks are server-side rules over the database. `wiki/` on
disk is an export mirror and is never the thing being linted.

## 1. Mechanical rules

```bash
histmersion lint                                    # all 13 rules, grouped Markdown report
histmersion lint --rule no-findings --rule no-period --rule no-regions
histmersion lint --fix                              # apply the two SAFE repairs first, then report
```

`mcp__histmersion__lint` with `rules: [...]` and/or `fix: true`.

The rules: `dangling-edges`, `unresolved-short-citekeys`, `no-findings`, `no-period`, `no-regions`,
`empty-summary`, `unassessed-status`, `duplicate-doi`, `duplicate-sha256`, `orphan-sources`,
`stale-chunks`, `undated-findings`, `unlocated-findings`.

`--fix` only ever does two things — resolve unambiguous short citekey edge targets, and rebuild
missing full-text chunks. Everything else needs judgement, below.

## 2. Judgement checks

Read across the flagged material and look for what a rule cannot see:

- **Contradictions.** Two sources making incompatible claims about the same region and period.
  `histmersion geo-when --place "<region>" --from <a> --to <b>` (`mcp__histmersion__geo_when`) puts the
  findings side by side; `histmersion search --group region,time --depth 2` shows where they pile up.
  Surface the conflict explicitly — in the sources' `notes` and in the article that covers it —
  rather than silently picking a winner.
- **Outdated statuses.** `histmersion search --status current --limit 200`, then **find-followups** on the
  load-bearing ones; anything with a `refutes`/`supersedes` edge pointing at it
  (`histmersion neighbors <citekey>`) but still `status: current` is wrong.
- **Missing enrichment.** Sources the rules flagged: read them (`histmersion get <ck> --full`) and patch
  in `topics/regions/period/findings` (`mcp__histmersion__patch_source`). Findings with a named place but
  no lat/lon (`unlocated-findings`) can usually be fixed from `histmersion places search "<name>"`.
- **Orphan articles and missing MOCs.** `histmersion article list` and `histmersion article get <slug>` show
  backlinks and cited sources; a cluster of sources sharing tags but with no article
  (`histmersion facets tags` against `histmersion article list --tag <tag>`) is a new-article candidate.
  Propose it, do not invent it silently.

## 3. Fix, queue, report

- Safe fixes: `histmersion lint --fix`, `mcp__histmersion__patch_source`, `mcp__histmersion__patch_article`
  (with `append`) — never `put_source` / `put_article` on something that already exists.
- Anything needing the user's call, or an acquisition: `histmersion queue tasks add "…"` /
  `histmersion queue acquire add "…"`.
- Then re-run `histmersion lint` and give the user a short report: what was fixed, what needs their call,
  and the follow-up questions the lint surfaced.
- Log it: `histmersion queue log add "lint sweep: N rules clean, M sources enriched, K queued"`.

## Done means

`histmersion lint` returns nothing you have not either fixed or queued with a reason.
