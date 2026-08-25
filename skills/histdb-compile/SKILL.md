---
name: histdb-compile
description: Write a research answer, topic summary or article from the hist-db corpus and file it back as an article — the rules for structure, citation, contradiction-hunting and plain language. Use whenever compiling prose out of the database, so answers exhaust the filter, cite every paragraph and surface disagreements between sources.
---

# Compiling prose from hist-db

The rules live in one place: **[`docs/writing-guide.md`](../../docs/writing-guide.md)**. Read it
before writing. This skill is the workflow around it, plus the compressed rules.

Querying comes first: the **`histdb-query`** skill.

## Workflow

1. **Check whether the article already exists.**
   `list_articles {query:"<topic>"}` — 38 articles are already compiled. If the topic has one,
   you are **patching**, not creating: `get_article {slug:…}` and read it end to end first.
2. **Query and exhaust the filter.** `facets` → `search` → note the total → `get_source` on every
   hit. Do not start writing until every citekey has a destination (guide rule 1).
3. **Hunt disagreement in the fixed order** (guide rule 2): status search → `neighbors` →
   `followups --since` on the foundational sources → the papers' own wording. Record which steps
   you ran; the `_Method_` note has to name them.
4. **Draft against the right section set** (guide rule 6):

   | output | sections |
   |---|---|
   | terminal answer | Question · What the sources say · Disagreements · Unresolved · Not used · **Sources cited** |
   | `report` article | Question · What the sources say · Disagreements · Unresolved · Not used |
   | `concept`/`culture`/`region`/`myth`/`timeline` article | Summary · What the sources say · Disagreements · Open questions · Sources |

   *Disagreements* and *Unresolved* / *Open questions* are **not optional**. When nothing conflicts,
   say so **and list what you checked** — `neighbors` on N, `followups` on M (guide rule 10).
5. **Tick the self-check** in the guide. Every box.
6. **File it into the database.** There is **no file-based fallback** — a report written to a
   markdown file on disk is not filed.

## Filing: `put_article` and `patch_article`

| you are… | call | note |
|---|---|---|
| creating a report | `put_article {slug, article:{title, kind:"report", body, summary, tags, regions, periodStart, periodEnd}}` | `kind` must be one of `concept\|myth\|culture\|region\|timeline\|report\|index` |
| rewriting one deliberately | `put_article` again, same slug | **destructive** — replaces the whole body |
| extending one | `patch_article {slug, patch:{append:"…"}}` | appends **at the very end of the body** |
| correcting one | `patch_article {slug, patch:{body:"…", ifUpdatedAt:"<what you read>"}}` | `ifUpdatedAt` returns 412 rather than clobbering a concurrent edit |

CLI equivalents: `histdb article put <file.md> [--kind report]`, `histdb article patch <slug>
--append-file <f>` / `--body-file <f>` / `--summary` / `--tags-add` / `--period -1300,-1100`
/ `--if-updated-at`.

> **`append` lands at the end of the body — nowhere else.** The guide requires new material to go
> **under the matching existing section**, and in a finished article the last section is *Sources*
> or *Open questions*. So `append` is correct only when you are genuinely adding the last section.
> To extend a *middle* section: `export_article {slug}` (or `get_article`), splice your paragraphs
> under the existing heading in the text you got back, and send the result as
> `patch_article {patch:{body:…, ifUpdatedAt:…}}`. Never add a second *Disagreements* heading.
> The code stream is adding section-targeted appends (`docs/plan.md` §6).

**Never write a `## Sources cited` section into an article body.** The page generates it from the
`[[citekey]]` links — `get_article` on any article shows it rendered as `## Sources cited (N)` under
the backlinks, with title · year · status per source. Write that list **only** in a terminal answer.

Slugs are lowercase with underscores, like the existing ones: `bronze_age_collapse`,
`indo_european_phylogenetics`, `conquest_vs_influence`.

## The rules, compressed

1. **State the filter and its total** (`search {…} → N`); every one of the N citekeys ends in
   *Sources cited* or *Not used (reason)*. N > 40 → narrow with `facets`, state both filters.
2. **Hunt contradictions in order** — status search, `neighbors`, `followups --since`, the papers'
   own wording — and say which steps you ran. The real edge kinds are `references`, `challenges`,
   `refutes`, `updates`, `superseded_by`; only the last four count, and only when the target is a
   citekey. There is no `cites` edge and no `supersedes` edge.
3. **Every paragraph cites at least one `[[citekey]]`**, spelled in full (`cline2014_year`, not
   `cline2014`; never "Cline 2014"). First citation of a non-current source carries its status
   inline — `(contested — refuted by [[kassian2025_critique]], 2025)`. `retracted` is cited only to
   say so; `superseded` is cited with its successor.
4. **Every overturned hypothesis gets years**: who proposed (year), who overturned (year), on what
   evidence. Publication years from metadata (`n.d.` if absent — queue a task); event dates from
   findings.
5. **Group section 2 on one named axis**: place/time → period→region; causes → by hypothesis;
   "what changed since" → by publication year. Say which.
6. **One paragraph per claim, not per source.** Terminal answers ≤ 600 words plus lists. Stop when
   every source is placed.
7. **Plain language**: numbers over adjectives; no sentence that survives a topic swap; zero banned
   filler; define any term a newspaper would gloss (culture names, aDNA, Bayesian phylogeny, LH IIIC,
   reconstructed forms — not century, empire, drought) in one clause on first use, reusing the
   article's existing definition when appending.
8. **Never invent** a term, a citekey, a date or a place. Corpus gaps go under *Unresolved*, not
   filled from memory.
9. **The filter line is a trailing `_Method_` note**, not the opening.
10. **Before returning**, tick the self-check and run the article lint rules once they exist:
    `lint {rules:["article-filler","article-uncited-paragraph"]}`. They do not exist yet — the 13
    live rules are corpus-integrity rules (`docs/plan.md` §6 hands the two prose rules to the code
    stream).

## Status taxonomy

`foundational` · `current` · **`contested`** · `superseded` (set `superseded_by`) · `debunked` ·
`retracted` · `unassessed`.

**`contested` = published critiques exist; not yet superseded.** It is the largest non-mainstream
bucket in the live corpus (17 of 209) and it is where the interesting writing is — a `contested`
source is not a source to avoid, it is a source to cite *with its critique attached*.

## Banned filler (rewrite the sentence)

rich tapestry · delve / delve into · nuanced interplay · sheds light on · it is important to note ·
multifaceted · a testament to · navigate the complexities · underscores the importance · paints a
picture · weave together · at its core · the intersection of · speaks volumes · plays a crucial role ·
a fascinating glimpse · complex and dynamic · serves as a reminder · in the realm of · tapestry of ·
profound implications

Also: no "In conclusion" openers, no hedging stacks ("may perhaps possibly suggest"), and no sentence
that survives a topic swap. **Prefer a number to an adjective** — "a ~300-year drought from 1200 BCE"
beats "a severe and prolonged drought".

## Self-check before returning

- [ ] Filter and total stated as `search {…} → N`; N ≤ 40 or narrowed with both filters stated
- [ ] Every one of the N citekeys is in *Sources cited* or *Not used*, with a reason
- [ ] Status search, `neighbors`, and `followups --since` on the foundational sources — run and reported
- [ ] Only real edge kinds; conflicts counted only when the edge target is a citekey
- [ ] Every paragraph cites a full `[[citekey]]`
- [ ] Non-current statuses stated at first citation; no `retracted` source used as evidence
- [ ] Overturned hypotheses carry proposer + year, overturner + year, evidence
- [ ] Section 2 grouped on one named axis
- [ ] Correct section set for the output kind; no duplicated heading in a patched article
- [ ] No `Sources cited` section written into an article body
- [ ] One paragraph per claim; ≤ 600 words of prose in a terminal answer
- [ ] Zero banned filler; numbers not adjectives; every specialist term defined once on first use
- [ ] No invented term, citekey, date or place
- [ ] `_Method_` note at the end, not the opening
- [ ] Any "no conflict" claim lists what was checked
- [ ] Filed with `put_article` / `patch_article` — not written to a file

Full rules and the reasoning: **[`docs/writing-guide.md`](../../docs/writing-guide.md)**.
