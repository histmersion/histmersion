---
name: histdb-compile
description: Write a research answer, topic summary or article from the hist-db corpus and file it back as an article — five phases (extraction → evidence model → draft → self-check → review), with WP:SYNTH, ALCE, AttrScore and GRADE controls. Use whenever compiling prose out of the database.
---

# Compiling prose from hist-db

The rules live in one place: **[`docs/writing-guide.md`](../../docs/writing-guide.md)**. Read it
before writing. This skill is the workflow around it, plus the compressed rules.

Querying comes first: the **`histdb-query`** skill.

## Five-phase workflow

### Phase 1 — Extraction

1. **Check whether the article already exists.**
   `list_articles {query:"<topic>"}`. If the topic has one, you are **patching**, not creating:
   `get_article {slug:…}` and read it end to end first.
2. **Run the filter and exhaust it.** `facets` → `search` → note the total as `search {…} → N`.
   `get_source` on every hit. Do not start phase 2 until every citekey has a destination.
3. **Build the claim table.** For each source, extract atomic claims (one fact per line) with:
   claim, citekey, date, place, confidence, source-location (summary/finding/body). From the
   record only — never memory.

### Phase 2 — Evidence model

4. **Hunt disagreement in order** (guide rule 2): status search → `neighbors` → `followups --since`
   on foundational sources → the papers' own wording. Record which steps you ran.
5. **Classify claim relations** using AttrScore: for each claim-pair, is the relationship
   **attributable**, **extrapolatory**, or **contradictory**?
6. **Compute due weight** (WP:WEIGHT): which position dominates the corpus? The Disagreements
   section will be sized to match.
7. **Decide the grouping axis** (period→region / hypothesis / pub year).
8. **Build the Not-used list** — every filter hit not cited, with a reason.

### Phase 3 — Draft

9. **Write with the evidence-sandwich pattern**: claim → cited evidence with `[[citekey]]` and
   status → what it settles or fails to settle.
10. **WP:SYNTH constraint**: no comparative or causal sentence unless one source makes that
    comparison. Two sources that each make half do not license the comparison.
11. **GRADE certainty phrasing** from the confidence field: "shows" / "probably" / "may" /
    "disputed: A argues… B argues…". Never `(confidence medium)` in text.
12. **Section set** for the article kind (guide rule 4). Disagreements and Unresolved are not
    optional. When nothing conflicts, list what was checked.

### Phase 4 — Self-check

13. Tick every box in the guide's self-check.
14. Run `lint {rules:["article-filler","article-uncited-paragraph"]}`.

### Phase 5 — Independent review

15. **File it into the database** (`put_article` or `patch_article`), then hand it to
    `/review-article <slug> --fix`. A MAJOR finding means the article is not done.

## Filing: `put_article` and `patch_article`

| you are… | call | note |
|---|---|---|
| creating | `put_article {slug, article:{title, kind, body, summary, tags, regions, periodStart, periodEnd}}` | `kind`: `concept\|myth\|culture\|region\|timeline\|report\|index` |
| rewriting | `put_article` again, same slug | **destructive** — replaces the whole body |
| extending | `patch_article {slug, patch:{append:"…"}}` | appends at the very end of the body |
| correcting | `patch_article {slug, patch:{body:"…", ifUpdatedAt:"<updatedAt>"}}` | `ifUpdatedAt` returns 412 on a concurrent edit |

> **`append` lands at the end of the body — nowhere else.** To extend a *middle* section: export,
> splice, and send the result as `patch_article {patch:{body:…, ifUpdatedAt:…}}`.

**Never write a `## Sources cited` section into an article body.** The page generates it from the
`[[citekey]]` links. Write it **only** in a terminal answer.

## Model guidance

| phase | recommended model | why |
|---|---|---|
| Extraction (claim table) | haiku-4-5 | mechanical comprehension, structured output |
| Evidence model | sonnet | classification, not prose |
| Draft | opus-4-6[1m] | prose quality is the deliverable; 1M context holds claim table + guide |
| Rules reviewer | sonnet | tool use + rule-following |
| Cold reader | haiku-4-5 | the point is a smaller model's perspective |
| Skeptic | opus-4-6[1m] | adversarial reasoning is the hardest task |
| Fixer | opus-4-6[1m] | needs both prose quality and careful verification |

Use stronger models only where the improvement is noticeable. Cut cost mercilessly where it is not.

## The rules, compressed

1. **Trace every claim to one source** — no WP:SYNTH bridge claims, no decorative citations.
2. **Hunt contradictions in order** — status search, `neighbors`, `followups --since`, the papers'
   own wording — and say which steps you ran.
3. **Cite as `[[citekey]]`, every paragraph**, in full. First citation of a non-current source
   carries its status inline. Statuses come from the DB, never the writer.
4. **Overturned hypotheses get years**: proposer + year, overturner + year, evidence.
5. **Group by the question's axis**; say which.
6. **One paragraph per claim**, not per source. Evidence sandwich: claim → evidence → what it settles.
7. **Plain language**: numbers over adjectives; definitions ≤20 words (Annual Reviews cap);
   no topic-swappable sentences; no banned filler; no editorial voice; no workflow chatter;
   no coined terms. GRADE certainty phrasing.
8. **Never invent.** Corpus gaps → *Unresolved*. `_Method_` note at the end.
9. **Due weight** — Disagreements sized by standing, not 50/50.
10. **Independent review** — the writer's self-check is a belief; the panel is what a reader finds.

## Status taxonomy

`foundational` · `current` · **`contested`** · `superseded` · `debunked` · `retracted` · `unassessed`

## Banned filler

rich tapestry · delve · nuanced interplay · sheds light on · it is important to note · multifaceted ·
a testament to · navigate the complexities · underscores the importance · paints a picture · weave
together · at its core · the intersection of · speaks volumes · plays a crucial role · a fascinating
glimpse · complex and dynamic · serves as a reminder · in the realm of · tapestry of · profound
implications

## Self-check

- [ ] Filter stated; every citekey placed in Sources or Not used
- [ ] Disagreement steps run and reported
- [ ] Every paragraph cites a full `[[citekey]]`; no escaped brackets
- [ ] No WP:SYNTH bridge claims; no decorative citations
- [ ] Non-current statuses inline; no retracted as evidence; superseded names successor
- [ ] Overturned hypotheses: proposer + year, overturner + year, evidence
- [ ] Disagreements sized by due weight
- [ ] Correct section set; no Sources cited in article body
- [ ] Zero banned filler; terms defined ≤20 words; Glossary if > 5
- [ ] GRADE certainty phrasing; no editorial voice; no workflow chatter; no invented anything
- [ ] `_Method_` at the end; "no conflict" lists what was checked
- [ ] Independent review run; no MAJOR finding open

Full rules: **[`docs/writing-guide.md`](../../docs/writing-guide.md)**.
