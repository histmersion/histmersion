# Writing guide — how to compile an answer from Histmersion

<!-- only:writer -->
These are the rules for every piece of prose an agent compiles out of Histmersion: a terminal research
answer, a `report` article, a concept/culture/region/myth/timeline article. They exist once, here.
The `histmersion-compile` skill and the research-workspace skills link to this file instead of restating it.
<!-- /only:writer -->

The reader is a **curious general audience**, not a specialist (cf. Stanford Encyclopedia of
Philosophy: "an advanced undergraduate with no special knowledge of the topic"). A compiled answer
succeeds when someone who has not read the sources ends up knowing what the sources actually say —
**including where they disagree** — and can trace every claim back to one source.

---

## Phases

<!-- only:writer -->
Compilation has five phases. Each produces an artifact the next phase consumes and reviewers can
audit. Do not jump to prose before the evidence model exists — an article drafted straight from
sources is the primary source of synthesis errors, decorative citations, and overcertainty.
<!-- /only:writer -->

### Phase 1 — Extraction

<!-- only:writer -->
Run the filter, page through the whole result set, and for each source extract **atomic claims**
(one fact per line, FActScore granularity) into a claim table:

```
claim | citekey | date | place | confidence | source-location (summary/finding/body)
```

Source: `get_source` (summary, findings) or `get_source --full` (body text). **Never memory.**
State the filter and its total in the form `search {…} → N`. If N > 40, narrow with `facets` and
state both filters. Every one of those N citekeys will end in *Sources cited* or *Not used*.
<!-- /only:writer -->

<!-- only:writer -->
**An article records its filter** (`patch_article` `filter`, `histmersion article patch <slug>
--filter-query … --filter-topics …`; `compile-article` records its own). From it, code generates the
article's **Not used** list, the counts above it and the `_Method_` note every time the article is
read — never write those by hand. What only a reader of the sources can supply is the **reason** each
source is left out: give it per source (`reasons`, `--reason <citekey>=<why>`); a source with none is
listed as *not yet assessed*, and the review reports it. `histmersion article coverage <slug>`
(`article_coverage`) shows the filter's sources, which are cited, the reasons and what is missing.
<!-- /only:writer -->

**"Covered in a sister article" is not a reason to exclude a source.** If a source's findings bear
on *this* article's question, use them here — even if the source is also cited elsewhere. The reader
of this article should not have to read three other articles to encounter the arguments. A source
belongs in Not-used only when its findings genuinely do not address the question at hand (wrong
region, wrong period, a different sub-problem). The test: does the source contain a finding that
would change, qualify, or support a claim this article makes? If yes, cite it.

**Exhaust, don't summarize.** An article that cites 15 of 32 filter hits and delegates 17 to sister
articles is a summary, not the article the corpus can support. Every finding in every cited source
that bears on the question earns a sentence — not a mention, a sentence that states the claim, names
the evidence type, and says what it settles. The article's length is set by its evidence, not by a
target word count.

### Phase 2 — Evidence model

<!-- only:writer -->
Run the four disagreement steps (below), classify each claim-pair relation using the AttrScore
vocabulary (**attributable** / **extrapolatory** / **contradictory**), and compute due weight
(WP:WEIGHT: space in the article tracks standing in the corpus, not 50/50 per side). Decide the
grouping axis (rule 5). Output: the claim graph, the weight notes, and a reason for every source the
article will leave out (the Not used list itself is generated from the recorded filter).
<!-- /only:writer -->

**The four disagreement steps** (run in order; report which you ran and what each returned — the
generated `_Method_` note reports the status breakdown and the disagreement edges of the cited
sources; say in the prose what steps 3 and 4 found):

1. **Status search** on the same filter — `search status:[contested,superseded,debunked,retracted]`.
2. **`neighbors`** on each source you lean on. Only `challenges`, `refutes`, `updates` and
   `superseded_by` edges count, **and only when the target is a citekey** (not a topic slug).
3. **`followups --since <year>`** on each `foundational` source. Today the whole corpus holds only
   ~10 source-to-source disagreement edges — step 2 returning nothing is not evidence of agreement.
4. **The papers' own wording.** Summaries and findings state rebuttals no edge captures.

The edge kinds that exist: `references`, `challenges`, `refutes`, `updates`, `superseded_by`.
There is no `cites` edge and no `supersedes` edge.

### Phase 3 — Draft

Write the article from the evidence model. Every paragraph follows the **evidence-sandwich**
pattern: **claim → cited evidence with `[[citekey]]` and status → what it settles or fails to
settle** (Graff & Birkenstein). A citation whose contribution the reader cannot restate is
unsandwiched — rewrite it.

**Certainty** uses GRADE-style controlled phrasing, mapped from the `confidence` field:

| confidence | phrasing |
|---|---|
| `high` | "X shows / establishes / dates to" (stated as fact) |
| `medium` | "X probably / the evidence points to / the best current reading is" |
| `low` | "X may / one reading is / tentative evidence suggests" |
| `contested` | "X is disputed: A argues… B argues…" (both sides, years, evidence) |

Never `(confidence medium)` in the text — that is bookkeeping, not prose.

<!-- only:writer -->
### Phase 4 — Self-check

Tick the self-check (below). Run lint. This is the writer's belief; phase 5 is what a reader finds.

### Phase 5 — Independent review

The writer does not review their own work. The research workspace runs `/review-article <slug>`:
three adversaries in parallel (rules compliance, cold read for comprehension, specialist
refutation attempt), then a fixer, up to three rounds. A MAJOR finding blocks the article.
<!-- /only:writer -->

---

## The rules

### 1. Every claim is traceable to one source

**Every paragraph cites at least one `[[citekey]]`** — the full stored key, exactly. Never
author-year alone, never a shortened key, never escaped `\[\[…\]\]` (renders as text).

**WP:SYNTH** — the bridge-claim rule: do not join source A's claim with source B's claim to
imply a conclusion C that neither source states. Every comparative or causal sentence must be
traceable to **one** source that makes that comparison. Two sources that each make half of a
comparison do not license the comparison.

**Decorative citation** (ALCE precision): a `[[citekey]]` whose record does not actually support
the sentence it is attached to is a MAJOR finding. The citation passes the "every paragraph cites"
rule while contributing nothing — that is worse than no citation, because it implies verification
that did not happen.

### 2. Statuses and years

The **first** citation of a non-`current`, non-`foundational` source carries its status inline:

- `contested` → `[[x]] (contested — challenged by [[y]], 2025)`
- `superseded` → `[[x]] (superseded by [[y]], 2019)`
- `debunked` → say what was debunked and by whom
- `retracted` → cited **only** to note the retraction; never as evidence
- `unassessed` → say the status is unassessed and queue an assess task

**The status comes from the database, never from the writer.** Do not call a source "superseded" or
"refuted" in prose unless `get_source` shows that status or a `superseded_by` / `refutes` edge
exists. If you have the evidence, patch the source first, then cite it. Do not coin status words.

**Every overturned hypothesis** names: who proposed (year), who overturned (year), on what evidence.
Event dates come from findings (signed years); publication years from metadata (`n.d.` if absent).

### 3. Due weight and disagreement

Disagreements are content, not problems. But they must be **proportional** (WP:WEIGHT):

- A position held by one source against fifteen gets one sentence, not a subsection.
- "Three sources say X" is different from "the field agrees on X" — the latter requires evidence
  that no published position disagrees (rule 2, step 3).
- Never turn absence of disagreement into consensus. State what was checked.
- Never resolve a disagreement the sources have not resolved — represent it, both sides, years,
  evidence that separates them, the edge that connects them.
- A disagreement edge touching a cited source counts as discussed when both sources are cited in the
  **same paragraph** — the one that states the criticised claim; one that does not bear on the article
  gets a stored reason, not prose. A paragraph that names an author's work cites it there.

### 4. Sections

**Terminal answer and `report` articles:**

1. Question — restated in one sentence.
2. What the sources say — grouped by the question's axis (period→region / hypothesis / pub year).
3. Disagreements — who holds which position, what separates them, which edges.
4. Unresolved — what the corpus cannot answer, **and what kind of source would settle it**.
5. Not used — every citekey the filter returned that was excluded, with a reason (an article: generated
   from its recorded filter and reasons, never written; a terminal answer: written).
6. *(terminal only)* Sources cited — citekeys with a one-line relevance note.

**`concept` / `culture` / `region` / `myth` / `timeline` articles:**

Summary · What the sources say · Disagreements · Open questions · Glossary — then the page appends the
generated *Not used* list and the `_Method_` note.

> **Do not write a `## Sources cited`, a `## Not used` section or a `_Method_` note into an article
> body.** The page generates the first from the `[[citekey]]` links and the other two from the
> article's recorded filter and reasons. An article written before filters were recorded keeps its
> hand-written ones until its filter is set; then delete them and move each reason into `reasons`.

### 5. Plain language

Short sentences. Concrete nouns. **A number beats an adjective.** A sentence that survives a topic
swap is filler — delete it. Zero banned phrases (see list below).

**Define any term a newspaper would gloss**, in one clause of ≤20 words (Annual Reviews cap), inline,
on the term's first use in the article. More than five glossed terms → add a `## Glossary` block.
When appending, reuse the existing definition.

**No editorial voice.** The article has no opinions: not "the wiki holds", "the honest position",
"the clincher". A judgement is a source's (cite it) or the reader's (give the evidence and stop).

**No workflow chatter.** File paths, queue names, `confidence: medium`, "queued", "OA fetch failed"
do not belong in the body. The article says what the sources say.

**No invented terminology.** Use the term the sources use. Never coin a term to make the exposition
smoother — it is better to teach the real term once than cycle through four fake synonyms.

### 6. Never invent

Never invent a term, a citekey, a date, a place, or a comparison. Gaps go under **Unresolved**,
filled from the corpus's own shape (`facets`), never from background knowledge.

<!-- only:writer -->
**The filter line goes in the trailing `_Method_` note**, not the opening — and for an article that note
is generated from the recorded filter:

```
_Method: search {topics: ["steppe_hypothesis"]} → 16; statuses: 11 current, 5 contested; none
superseded, debunked or retracted; disagreement edges touching the cited sources: [[a]] challenges [[b]];
disagreements checked: 1 pair — 1 addressed, 0 reasoned, 0 open._
```

### 7. Before returning

Tick the self-check. Run lint:

```
lint {rules:["article-filler","article-uncited-paragraph"]}
```

Then hand the article to a reviewer that is not the writer (`/review-article <slug> --fix`).
<!-- /only:writer -->

---

## Banned filler

If any of these appear, rewrite the sentence.

| banned | banned | banned |
|---|---|---|
| rich tapestry | delve / delve into | nuanced interplay |
| sheds light on | it is important to note | multifaceted |
| a testament to | navigate the complexities | underscores the importance |
| paints a picture | weave together | at its core |
| the intersection of | speaks volumes | plays a crucial role |
| a fascinating glimpse | complex and dynamic | serves as a reminder |
| in the realm of | tapestry of | profound implications |

Also banned: "In conclusion" openers; hedging stacks ("may perhaps possibly suggest"); any sentence
that survives a topic swap; any sentence that renames the phenomenon instead of explaining it.

<!-- only:writer -->
---

## Self-check

- [ ] Filter stated as `search {…} → N` (an article: recorded with it); N ≤ 40 or narrowed with both stated.
- [ ] Every one of the N citekeys is cited or has a reason (`histmersion article coverage <slug>` shows no
      source "neither cited nor given a reason").
- [ ] Status search, neighbors, followups --since on foundational sources — run and reported.
- [ ] Only real edge kinds; edges counted only when the target is a citekey.
- [ ] Every paragraph cites at least one full `[[citekey]]`; no escaped brackets.
- [ ] No formatting error: the write's *Formatting* list (or `histmersion article lint <slug>`) is
      empty of errors — tables have a blank line above and a `|---|` row, `**` pairs, links render.
- [ ] No WP:SYNTH bridge claims — every comparative sentence traceable to one source.
- [ ] No decorative citations — every cited source's record supports the sentence.
- [ ] Non-current statuses stated at first citation; no retracted source used as evidence.
- [ ] Every overturned hypothesis has proposer + year, overturner + year, evidence.
- [ ] Disagreements sized by due weight, not 50/50.
- [ ] Sections present and unduplicated; no Sources cited, Not used or `_Method_` written into an article body.
- [ ] One paragraph per claim; prose ≤ 600 words in a terminal answer.
- [ ] Zero banned filler; numbers not adjectives; no topic-swappable sentences.
- [ ] Every specialist term defined (≤20 words) on first use; Glossary if > 5; no coined terms.
- [ ] Certainty phrased by GRADE levels (shows/probably/may/disputed), never bare confidence tags.
- [ ] No editorial voice; no workflow chatter; no invented terms, dates, comparisons.
- [ ] `_Method_` note at the end, not the opening (an article: generated).
- [ ] Independent review run; no MAJOR finding open.
<!-- /only:writer -->
