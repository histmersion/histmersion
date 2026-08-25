# Writing guide — how to compile an answer from hist-db

These are the rules for every piece of prose an agent compiles out of hist-db: a terminal research
answer, a `report` article, a concept/culture/region/myth/timeline article. They exist once, here.
The `histdb-compile` skill and the research-workspace skills link to this file instead of restating it.

The reader is a **curious general audience**, not a specialist. A compiled answer succeeds when
someone who has not read the sources ends up knowing what the sources actually say — **including
where they disagree**.

---

## The ten rules

### 1. State the filter and its total; place every citekey it returned

Open the work by running one filter and writing down its total, in the form `search {…} → N`.
Every one of those N citekeys ends the job in either **Sources cited** or **Not used (reason)**.
Nothing may vanish silently.

```
search {topics:["steppe_hypothesis"]} → 16
```

If **N > 40**, the filter is too broad to exhaust. Narrow it with `facets` and state **both** filters:

```
search {tags:["demic_diffusion"]} → 42        # too broad
facets {field:"tags", tags:["demic_diffusion"]} → steppe_ancestry (8), corded_ware (8), …
search {tags:["demic_diffusion","steppe_ancestry"]} → 8
```

An answer built from three sources when the filter returned forty is wrong even if every sentence in
it is true.

### 2. Find the disagreements, in this order, and say which steps you ran

Run these four steps in order. Report which ones you ran and what each returned — the reader has to
be able to tell a checked "no conflict" from an unchecked one.

1. **`search status:[contested,superseded,debunked,retracted]` on the same filter.** The cheapest
   signal: the corpus already knows which of your N sources are disputed.
2. **`neighbors` on each source you lean on.** Only `challenges`, `refutes`, `updates` and
   `superseded_by` edges count as disagreement, **and only when the edge target is a citekey**.
   **Ignore `references → <topic-slug>`**: 1,039 of the corpus's 1,048 `references` edges point at
   concept slugs (`steppe_hypothesis`, `ancient_dna`) rather than at sources, and carry no
   argumentative content. Non-`references` edges can point at slugs too — `challenges → gimbutas`,
   `refutes → big_gods_hypothesis` flag a *hypothesis*, not a source disagreement. Worth reading,
   not a citable conflict.
3. **`followups --since <year>` on each `foundational` source.** Stored edges are **sparse**: today
   the whole corpus holds exactly **6** source-to-source disagreement edges (2 `challenges`,
   1 `refutes`, 3 `updates`, 0 `superseded_by`). So step 2 returning nothing is not evidence of
   agreement, and this step carries real weight. It is a live Semantic Scholar call — seconds per
   source — so run it on the foundational sources, not on all N, and prefer a stored edge when
   step 2 already answered the question.
4. **The papers' own wording.** `get_source` summaries and findings state rebuttals in prose that no
   edge captures (`chang2015`'s summary calls it "a direct methodological rebuttal of Bouckaert et
   al."; no edge says so).

The **edge kinds that exist** are `references`, `challenges`, `refutes`, `updates`, `superseded_by`.
There is no `cites` edge and no `supersedes` edge in the corpus — do not write either.

A disagreement between sources is content, not a problem to smooth over.

### 3. Cite `[[citekey]]`, every paragraph, with status inline

- **Every paragraph cites at least one citekey.** A paragraph with none is deleted, not rewritten.
- Cite as `[[citekey]]` — the full stored key, exactly. Real keys look like `cline2014_year`,
  `haak2015_massive`, `drews1993_endbronze`, `heggarty2023_language`. **Never** author-year alone
  ("Cline 2014"), never a shortened key (`cline2014`, `haak2015`).
- The **first** citation of a non-`current`, non-`foundational` source carries its status inline:
  - `contested` → `[[heggarty2023_language]] (contested — refuted by [[kassian2025_critique]], 2025)`
  - `superseded` → cite it **with its successor**: `[[x]] (superseded by [[y]], 2019)`
  - `debunked` → say what was debunked and by whom
  - `retracted` → cited **only** to say that it is retracted; never as evidence for a claim
  - `unassessed` → say the status is unassessed and queue an `assess` task

**Status taxonomy** (`SourceStatus` in the code, and the `status:` frontmatter field):

| status | meaning |
|---|---|
| `foundational` | the standard reference for its problem; still cited on its merits |
| `current` | current mainstream, no published challenge in the corpus |
| `contested` | **published critiques exist; not yet superseded** — the claim is live and disputed |
| `superseded` | a later work replaces it; set `superseded_by` |
| `debunked` | the central claim is refuted |
| `retracted` | withdrawn by journal or authors |
| `unassessed` | never checked — treat as unknown, not as current |

In the live corpus today: `foundational` 97 · `current` 91 · `contested` 17 · `debunked` 2 ·
`superseded` 2 (209 sources).

### 4. Every overturned hypothesis gets years

For each hypothesis that has been overturned or revised, name: **who proposed it (year)**, **who
overturned it (year)**, and **on what evidence**. Three facts, one sentence.

> Gray & Atkinson dated Proto-Indo-European to 7,800–9,800 BP from Bayesian lexical phylogenetics
> ([[gray2003_language]], 2003); [[chang2015]] (n.d. in the corpus; published 2015) cut that to a
> median ~6,000 BP by forcing documented ancestor→descendant pairs to be treated as direct lineages.

- **Event dates** (a destruction layer, an eruption, a migration) come from `findings` or the body —
  signed years, BCE negative.
- **Publication years** come from source metadata, never from the body and never from memory. When
  the metadata has no year, write `n.d.` and queue a task (`queue add tasks "fill year for <ck>"`).
  Twelve sources currently have no year, including `chang2015`, `allentoft2015` and `anthony2007_horse`.

### 5. Group section 2 by the question's axis, and say which

Pick the grouping from the question, and name it in the section heading or its first line:

| the question is about | group by | tool that gives you the shape |
|---|---|---|
| a place and/or a time | **period → region** | `search {groupBy:"time,region"}`, `histogram` |
| causes of an event | **by hypothesis**, one subsection each | `facets {field:"topics"}` |
| "what has changed since YEAR" | **by publication year** | `search {sort:"year"}`, `facets {field:"status"}` |

Do not group by source. One subsection per group.

### 6. Sections

**Terminal answer and `report` articles:**

1. **Question** — restated in one sentence.
2. **What the sources say** — grouped per rule 5; every paragraph cites.
3. **Disagreements** — who holds which position, what evidence separates them, which edges connect them.
4. **Unresolved** — what the corpus does not answer, **and what kind of source would settle it**.
5. **Not used** — every citekey the filter returned that you excluded, with a reason.
6. *(terminal only)* **Sources cited** — citekeys with a one-line relevance note.

> **Do not write a "Sources cited" section into an article.** The page generates it from the
> `[[citekey]]` links in the body and renders it as `## Sources cited (N)` under the backlinks.
> Write it **only** in a terminal answer, where nothing renders it for you.

**`concept` / `culture` / `region` / `myth` / `timeline` articles:**

`Summary` · `What the sources say` · `Disagreements` · `Open questions` · `Sources`

Same rules throughout. `Sources` here is the hand-written annotated list (citekey — author, year,
one clause on what it contributes, status in bold) that the existing articles carry; it is separate
from the generated `Sources cited` footer.

**When patching an existing article**: read it first with `get_article`, then append **under the
matching existing section** with `patch_article`. Never add a second *Disagreements* or a second
*Open questions*. Sections 3 and 4 are not optional — see rule 10 for the wording when there is no
conflict.

### 7. One paragraph per claim, not per source

Group the sources that speak to the same claim into one paragraph. Do not walk the result list.
A terminal answer is **≤ 600 words** of prose plus its lists. Stop when every source from rule 1 has
been placed — not when you run out of things to say.

### 8. Plain language

Short sentences. Concrete nouns. **Prefer a number to an adjective**: "a ~300-year drought from
1200 BCE" beats "a severe and prolonged drought". A sentence that would survive unchanged if the
topic were swapped for a different one is filler — delete it. Zero phrases from the banned list below.

**Define any term a newspaper would gloss**, in one clause, inline, on the term's first use *in the
article*:

- culture names — Yamnaya, Corded Ware, Bell Beaker, Sea Peoples
- methods — aDNA (ancient DNA sequenced from skeletal remains), Bayesian phylogeny, qpAdm, ABC simulation
- period labels — LH IIIC, Late Minoan IB, PPNB
- reconstructed forms — `*h₂éusōs`, with a note on first use that `*` marks a reconstruction

Do **not** gloss ordinary words: century, empire, drought, migration, tablet.

When appending to an existing article, **reuse its definition** rather than defining the term again.

### 9. Never invent

Never invent a term, a citekey, a date or a place. Use the term the sources use. If `get_source`
returns `_No source '<key>'_`, that key does not exist. Coordinates and periods come from `findings`.

Gaps in the corpus go under **Unresolved**, filled from the corpus's own shape (what `facets` shows
is missing), never from background knowledge.

**The filter line goes in a trailing `_Method_` note**, not in the opening. The reader wants the
answer first.

```
_Method: search {topics:["steppe_hypothesis"]} → 16 sources; status filter → 5 contested;
neighbors on 6; followups --since 2015 on gray2003_language._
```

### 10. Before returning

Tick the self-check below. Then run the corpus linter once the article rules exist:

```
lint {rules:["article-filler","article-uncited-paragraph"]}
```

Those two rules do **not** exist yet — the 13 live rules are corpus-integrity rules, not prose rules
(see `docs/plan.md` §6; they are handed to the code stream). Until they land, the self-check is the lint.

**"The sources in this filter do not conflict" must list what was checked.** Bare, it is an
unverifiable claim. Write:

> The sources in this filter do not conflict: `neighbors` on all 16 returned no `challenges`,
> `refutes`, `updates` or `superseded_by` edge between them, and `followups --since 2015` on the
> 6 `foundational` sources surfaced no critique already in the corpus.

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

Also banned: opening a section with "In conclusion"; hedging stacks ("it may perhaps possibly
suggest"); and any sentence that survives a topic swap.

---

## Self-check

Tick every box before returning. An unticked box is a rewrite, not a caveat.

- [ ] I wrote the filter and its total as `search {…} → N`, and N ≤ 40 (or I narrowed and stated both filters).
- [ ] Every one of the N citekeys appears in **Sources cited** or in **Not used**, with a reason.
- [ ] I ran the status search, `neighbors`, and `followups --since` on the foundational sources — and said so.
- [ ] I used only the real edge kinds (`references`, `challenges`, `refutes`, `updates`, `superseded_by`), counted an edge as a conflict only when its target is a citekey, and ignored `references → <topic-slug>`.
- [ ] Every paragraph cites at least one `[[citekey]]`, spelled in full.
- [ ] Every non-current source carries its status at first citation; no `retracted` source is used as evidence; every `superseded` one names its successor.
- [ ] Every overturned hypothesis has proposer + year, overturner + year, and the evidence.
- [ ] Publication years come from metadata (`n.d.` where absent, with a queued task); event dates come from findings.
- [ ] Section 2 is grouped on one named axis (period→region / hypothesis / publication year).
- [ ] The sections are present and unduplicated; a patched article appends under existing headings.
- [ ] No **Sources cited** section written into an article body (terminal answers only).
- [ ] One paragraph per claim; prose ≤ 600 words in a terminal answer.
- [ ] Zero banned-filler phrases; numbers instead of adjectives; no topic-swappable sentences.
- [ ] Every specialist term defined in one inline clause on first use in the article; existing definitions reused.
- [ ] No invented term, citekey, date or place; gaps are under **Unresolved**.
- [ ] The filter line is a trailing `_Method_` note, not the opening.
- [ ] Any "no conflict" claim lists what was checked (`neighbors` on N, `followups` on M).
