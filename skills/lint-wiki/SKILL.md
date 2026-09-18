---
name: lint-wiki
description: Run health checks over the corpus — dangling citation edges, un-enriched sources, missing periods/regions/findings, empty summaries, duplicate DOIs, orphans, undated or unlocated findings — plus the judgement checks (contradictions, outdated statuses, missing articles). Use to clean up data integrity and surface follow-up questions after a batch of ingestion.
allowed-tools: Bash(histdb:*), mcp__hist-db__*
---

# Lint the corpus

There is nothing to grep any more: the checks are server-side rules over the database. `wiki/` on
disk is an export mirror and is never the thing being linted.

## 1. Mechanical rules

```bash
histdb lint                                    # all 13 rules, grouped Markdown report
histdb lint --rule no-findings --rule no-period --rule no-regions
histdb lint --fix                              # apply the two SAFE repairs first, then report
```

`mcp__hist-db__lint` with `rules: [...]` and/or `fix: true`.

The rules: `dangling-edges`, `unresolved-short-citekeys`, `no-findings`, `no-period`, `no-regions`,
`empty-summary`, `unassessed-status`, `duplicate-doi`, `duplicate-sha256`, `orphan-sources`,
`stale-chunks`, `undated-findings`, `unlocated-findings`.

`--fix` only ever does two things — resolve unambiguous short citekey edge targets, and rebuild
missing full-text chunks. Everything else needs judgement, below.

## 2. Judgement checks

Read across the flagged material and look for what a rule cannot see:

- **Contradictions.** Two sources making incompatible claims about the same region and period.
  `histdb geo-when --place "<region>" --from <a> --to <b>` (`mcp__hist-db__geo_when`) puts the
  findings side by side; `histdb search --group region,time --depth 2` shows where they pile up.
  Surface the conflict explicitly — in the sources' `notes` and in the article that covers it —
  rather than silently picking a winner.
- **Outdated statuses.** `histdb search --status current --limit 200`, then **find-followups** on the
  load-bearing ones; anything with a `refutes`/`supersedes` edge pointing at it
  (`histdb neighbors <citekey>`) but still `status: current` is wrong.
- **Missing enrichment.** Sources the rules flagged: read them (`histdb get <ck> --full`) and patch
  in `topics/regions/period/findings` (`mcp__hist-db__patch_source`). Findings with a named place but
  no lat/lon (`unlocated-findings`) can usually be fixed from `histdb places search "<name>"`.
- **Orphan articles and missing MOCs.** `histdb article list` and `histdb article get <slug>` show
  backlinks and cited sources; a cluster of sources sharing tags but with no article
  (`histdb facets tags` against `histdb article list --tag <tag>`) is a new-article candidate.
  Propose it, do not invent it silently.

## 3. Fix, queue, report

- Safe fixes: `histdb lint --fix`, `mcp__hist-db__patch_source`, `mcp__hist-db__patch_article`
  (with `append`) — never `put_source` / `put_article` on something that already exists.
- Anything needing the user's call, or an acquisition: `histdb queue tasks add "…"` /
  `histdb queue acquire add "…"`.
- Then re-run `histdb lint` and give the user a short report: what was fixed, what needs their call,
  and the follow-up questions the lint surfaced.
- Log it: `histdb queue log add "lint sweep: N rules clean, M sources enriched, K queued"`.

## Done means

`histdb lint` returns nothing you have not either fixed or queued with a reason.
