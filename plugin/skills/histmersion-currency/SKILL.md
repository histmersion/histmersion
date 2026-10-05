---
name: histmersion-currency
description: Keep the Histmersion corpus honest about what is still accepted — refresh the Retraction Watch mirror, run assess and followups over a topic, move statuses to contested/superseded/debunked/retracted, and queue the papers that would fill the gap. Use for "is this still accepted?", "what has changed since YEAR?", "anything new on X?", or a periodic corpus review.
---

# Keeping Histmersion current

A corpus rots quietly. A paper cited as settled in 2015 may have been refuted in 2025 and the row
still says `foundational`. This skill is the maintenance pass that finds those and fixes the
`status` field — which every compiled answer then reports (`docs/writing-guide.md` rule 3).

Reading the corpus: **`histmersion-query`**. Writing the answer up: **`histmersion-compile`**.

## Prerequisite: the retraction mirror is empty until you fill it

```
assess {updateRetractions: true}          # CLI: histmersion assess update-retractions
```

`retraction_watch` currently holds **0 rows** on this server, so `assess` cannot detect a retraction
until this runs (~60,000 rows). Do it **first**, and re-run it at the start of any periodic review.

## The pass

### 1. Scope it, and take a status baseline

```
search {topics:["<topic>"]}                    → N
facets {field:"status", topics:["<topic>"]}
```

Example, live:

```
facets {field:"status", topics:["steppe_hypothesis"]}
### status (3)
- foundational (6)
- contested (5)
- current (5)
```

Corpus-wide today: `foundational` 97 · `current` 91 · `contested` 17 · `debunked` 2 ·
`superseded` 2 (209 sources). Nothing is `retracted` or `unassessed`.

### 2. Read the stored edges first — they are free, but sparse

```
neighbors {citekey:"<ck>"}
```

Across all 209 sources there are exactly **6 source-to-source disagreement edges** (2 `challenges`,
1 `refutes`, 3 `updates`, 0 `superseded_by`). An empty `neighbors` result is **not** evidence that
nothing challenged the paper — it usually means nobody has recorded it yet. That is what step 3 is for.

Everything else — 1,039 of 1,048 `references` edges, and several `challenges`/`refutes` — points at
a **concept slug** (`gimbutas`, `big_gods_hypothesis`, `demic_diffusion`) rather than a citekey.
Those flag a *hypothesis*, not a source. `lint --rule dangling-edges` lists them all.

### 3. `followups` on the foundational sources

```
followups {id:"gray2003_language", since:2015, limit:10}
```

```
| ★ | title | year | intents | proposed edge | in corpus |
| --- | --- | --- | --- | --- | --- |
|  | Testing the validity and adequacy of linguistic phylogenetic analyses | 2026 |  | **cites** | `phylo2026_validity` |
|  | Applying invasion criterion to cultural evolution. | 2026 |  | **cites** | — |
```

A live Semantic Scholar call — seconds per source. Run it on
`search {topics:[…], status:["foundational"]}`, not on all N.

- **Translate the proposed edge kinds before recording.** `followups` proposes
  `confirms|challenges|refutes|supersedes|cites`; the corpus stores
  `references|challenges|refutes|updates|superseded_by`. `cites`/`confirms` → `references`;
  `supersedes` → a `superseded_by` edge on the **older** source. (Handed to the code stream,
  `docs/plan.md` §6.)
- `followups {id, apply:true}` records edges automatically, but only for papers already in the
  corpus. Check what it wrote with `neighbors`.
- Rows with `in corpus: —` are acquisition candidates, not evidence.

### 4. `assess` anything you are about to rely on

```
assess {id:"<citekey|doi>"}                # verdict: retracted|corrected|current|low-impact
assess {id:"<citekey>", apply:true}        # write the verdict into status — only over `unassessed`
                                           # (a retraction always); a status set by hand is kept and
                                           # reported unless forceStatus:true
```

Retraction Watch + Crossref amendment notices + citation metrics. Only 1 source carries a stored
assessment today, so most of the corpus is unverified in this sense even though no row says
`unassessed`.

### 5. Move the status, and say why

| what you found | new `status` | also do |
|---|---|---|
| a published critique, nothing replacing it | `contested` | add the `challenges`/`refutes` edge |
| a later work that replaces it | `superseded` | set `superseded_by` (that is the edge kind) |
| the central claim is refuted | `debunked` | add the `refutes` edge, name the refuter and year |
| withdrawn by journal or authors | `retracted` | `assess --apply` writes this; cite it only to say so |
| checked, nothing found | leave it | record what you checked in the log (step 7) |

```
patch_source {citekey:"<ck>", patch:{metadata:{status:"contested"}, ifUpdatedAt:"<what you read>"}}
patch_source {citekey:"<critic>", patch:{edges:{add:[{dst:"<ck>", kind:"challenges"}]}}}
```

The edge is stored on its source — the critic — so it is a patch of `<critic>`, not of `<ck>`.
CLI: `histmersion patch <ck> --status contested` then `histmersion patch <critic> --add-edge challenges:<ck>`.

**`contested` is the workhorse.** It means *published critiques exist; not yet superseded* — the
honest state of most live debates. Do not skip it for `debunked`.

### 6. Find what the corpus is missing

```
discover {query:"<topic>", since:2023, notInDb:true, limit:20}
```

Everything the corpus does not have yet, from OpenAlex. Ingest the ones that matter
(`ingest_doi`, see **`histmersion-ingest`**); queue the rest:

```
queue {action:"add", list:"acquire", text:"<title> — critique of <ck>", topic:"<topic>"}
```

### 7. Update the articles, and log the pass

A status change that never reaches the prose is invisible. `get_article` any article citing the
source (`get_article` lists **Sources cited**, and each source's status beside it), then patch the
*Disagreements* section per `docs/writing-guide.md` rule 6 and the **`histmersion-compile`** skill.

```
queue {action:"add", list:"log", text:"currency pass on <topic>: N sources, M statuses changed, K queued"}
```

## Recording review checkpoints

The corpus already uses a `review_checkpoint` topic for the synthesis/review papers that mark where
a field stood at a date — 18 sources carry it (`adna2018_firstdecade`, `cline2010_aegeanhandbook`, …).
When you ingest a new review or handbook, tag it `review_checkpoint`: it is the cheapest way to ask
"what did the field think in year X" (`search {topics:["review_checkpoint"], sort:"year"}`).

## Rules

- **Refresh the retraction mirror before trusting `assess`.** It is empty by default.
- **Never downgrade a status without an edge or an assessment behind it.** The reason has to be
  reconstructable from the database, not from this conversation.
- **Use the stored edge kinds** (`references`, `challenges`, `refutes`, `updates`, `superseded_by`),
  never `followups`'s proposal vocabulary.
- **`followups` and `discover` are network calls** — slow, rate-limitable, and they can return
  nothing when the API is down. Say so rather than reporting "no critiques found".
- **Queue, don't file.** Missing papers go to `queue acquire`, not to a new markdown file.
