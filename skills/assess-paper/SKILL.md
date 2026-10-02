---
name: assess-paper
description: Judge whether a paper is safe to rely on and whether it is retracted, corrected, or low-impact — using the Retraction Watch mirror, Crossref update notices, and citation metrics. Use to vet a DOI before ingesting, to prune junk from a queue, or to periodically re-check the corpus for newly-retracted sources.
allowed-tools: Bash(histmersion:*), mcp__histmersion__*
---

# Assess a paper's integrity and worth

## One paper

```bash
histmersion assess 10.1371/journal.pone.0071004      # a DOI, before ingesting
histmersion assess kaniewski2013_environmental       # a citekey, already in the corpus
histmersion assess <citekey> --apply                 # write the verdict into `status` (only over unassessed;
                                                     # a retraction always; --force-status overrides a set one)
```

`mcp__histmersion__assess` with `id` (+ `apply: true`).

The verdict is one of `retracted | corrected | current | low-impact | unassessed`, backed by the
local Retraction Watch mirror, Crossref retraction/correction/expression-of-concern notices, and the
citation counts. Act on it:

- `retracted` → never ingest as current. If it is already in the corpus, `--apply` sets
  `status: retracted`; keep the source and say so wherever it is cited.
- `corrected` → keep, but state the correction in the source `notes`
  (`mcp__histmersion__patch_source` → `notes`) so it surfaces when the source is used.
- `low-impact` and old → ask whether it is worth the tokens; keep only if topically pivotal, and say
  why in the notes.
- `current` → ingest/keep.

**Always assess before citing a source you have not checked.**

## Refresh the ground truth (periodically, not per paper)

```bash
histmersion assess update-retractions
```

`mcp__histmersion__assess` with `updateRetractions: true`. It refreshes the ~60 000-row Retraction Watch
mirror **in the background** — call `mcp__histmersion__assess { retractionsStatus: true }` (or run
`histmersion assess update-retractions` again) to see whether it finished.

## Sweep the whole corpus

After a refresh, re-check everything that has a DOI and is not already flagged:

```bash
histmersion query "select citekey, doi from sources where doi is not null and status not in ('retracted','debunked') order by citekey"
```

(`mcp__histmersion__query` — one read-only SELECT; writes are refused by Postgres itself.)

Then `histmersion assess <citekey> --apply` for each, and report only the ones whose verdict changed.
`histmersion search --status unassessed --limit 200` (`mcp__histmersion__search`) and
`histmersion lint --rule unassessed-status` find the ones never assessed at all.

## Follow the consequence

A `retracted` or newly-`corrected` source usually needs the **find-followups** skill next, to find
what replaced it and record a `supersedes` edge — and the articles that cite it need a line saying so
(`mcp__histmersion__patch_article` with `append`).

## Done means

Every affected source carries the right `status`, the change is stated where the source is cited, and
`histmersion lint --rule unassessed-status` is clean for the sources you touched.
