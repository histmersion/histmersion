---
name: bookkeeping
description: Where project bookkeeping goes — the three Histmersion queues (acquire / tasks / log) reached with `histmersion queue`, and the rule that compiled content lives in articles. Use whenever you would otherwise create a queue/tracker/changelog file, when recording what to acquire or what to do next, or when logging completed work.
allowed-tools: Bash(histmersion:*), mcp__histmersion__*
---

# Bookkeeping — three queues in the database, no scatter

Bookkeeping lives in **three lists in Histmersion**, reached only through `histmersion queue` (CLI) or
`mcp__histmersion__queue`. `meta/ACQUIRE.md`, `meta/TASKS.md` and `meta/LOG.md` still exist on disk, but
they are **exports** written by `histmersion export` / `histmersion queue export` — read them in Obsidian, never
edit them. An edit there is overwritten by the next export and never reaches the database.

| list | whose | holds |
|---|---|---|
| `acquire` | **the user** | what to buy/borrow/download → Calibre: in-copyright or unfetchable works. |
| `tasks` | **the agent** | the backlog: ingestion queues, OA leads to fetch, enrichment/compile follow-ups, currency checkpoints, maintenance. |
| `log` | shared | the dated worklog — one line per meaningful piece of work. |

Never create a fourth tracker: no `queue_*.md`, no `*_watch.md`, no `books_to_acquire`, no
`sources_to_provide`, no scratch checklist in an article. Fold it into one of the three.

## Commands

```bash
histmersion queue acquire                 # open items with their ids
histmersion queue tasks --all             # include done items and the file's prose blocks
histmersion queue log

histmersion queue tasks add "Ingest 10.1073/pnas.2108537118 — Yamnaya dairying" --topic "Queue: proto_indo_european"
histmersion queue acquire add "Burkert, *Greek Religion* — buy/borrow → Calibre" --topic "aegean_religion"
histmersion queue log add "ingested 6 sources for bronze_age_collapse; 2 superseded"

histmersion queue done <id>               # close an item
histmersion queue rm <id>                 # delete one added by mistake
```

`mcp__histmersion__queue` takes `action: "list" | "add" | "done"` with `list`, `text`, `topic`, `id`,
`all`. `topic` groups items under a heading; on `log` it defaults to today's date, so an added line
lands in today's entry.

## Routing rule (decide in one step)

- "The user must obtain X" → `histmersion queue acquire add`.
- "I should fetch / ingest / enrich / check X" → `histmersion queue tasks add`.
- "We just did X" → `histmersion queue log add`.
- "This is a finished synthesis" → it is content → an **article**
  (`mcp__histmersion__put_article` / `patch_article`), not a queue.

## Refreshing the markdown mirror

```bash
histmersion export wiki/sources --no-meta   # sources → wiki/sources/<citekey>.md
histmersion article export wiki             # articles → wiki/<kind>/<slug>.md
histmersion queue export meta               # meta/ACQUIRE.md · TASKS.md · LOG.md
```

Run these after a batch of work so Obsidian and git history see the current state. Until you do, the
files on disk are **stale** — say so if the user is reading them. `histmersion queue import <dir>` is the
reverse and is a recovery tool, not part of the normal loop.

## Conventions

- Items are plain text: reference articles by slug (`bronze_age_collapse`) and sources by citekey.
  `[[wikilinks]]` belong in article bodies, not in queue items.
- Keep them current as you work: close items the moment they are done; a stale open item is worse
  than no item.
- "What do I need to get?" → `histmersion queue acquire`. "What's left?" → `histmersion queue tasks`.
  "What did we do?" → `histmersion queue log`. One predictable answer each.

## Done means

The work is represented in exactly one of the three lists, nothing was written to a new markdown
tracker, and any finished item is closed with `histmersion queue done`.
