# Writing guide — how to compile an answer from hist-db

These are the rules for every piece of prose an agent compiles out of hist-db: a research answer, a
topic summary, a concept article, a report. They exist once, here; the `histdb-compile` skill and the
research-workspace skills link to this file instead of restating it.

The reader is a **curious general audience**, not a specialist. The purpose of a compiled answer is
that someone who has not read the sources ends up knowing what the sources actually say — including
where they disagree.

---

## 1. Exhaust the topic

Enumerate **every** source the filter returns, not the first page.

- Run the query, note the total, and page through it (`search --limit`, then `facets` to see the
  shape of the result set).
- Every source that matched must be either **used** or **explicitly excluded**, with a reason:
  "not used: `cline2014` covers the Aegean only", "not used: `smith1971`, superseded by `jones2019`".
- End the answer with a **Sources used** list of citekeys, and a short **Not used** list.

An answer built from three sources when the filter returned forty is wrong even if every sentence in
it is true.

## 2. Hunt contradictions

The corpus contains typed edges (`cites`, `updates`, `challenges`, `refutes`, `supersedes`). Use them.

- For each substantive claim, call `neighbors` on the source it came from.
- Surface every `refutes` / `challenges` / `supersedes` edge that touches a claim you are making.
- When a newer finding overturns an older hypothesis, **say so with years**: "Gimbutas (1956) placed
  the homeland in the Pontic steppe on ceramic grounds; Anthony (2007) reaches the same conclusion
  from horse domestication and wheeled-vehicle evidence, and `haak2015` settles it with aDNA."
- A `status:` of `superseded`, `debunked` or `retracted` must be stated in the text where the source
  is cited, never silently dropped and never silently used.

A disagreement between sources is content, not a problem to smooth over.

## 3. No generic answers — every paragraph cites

**Every paragraph cites at least one citekey.** A paragraph with no source is deleted, not rewritten.

- Cite as `[[citekey]]` (the article renderer turns it into a link) or plain `citekey` in terminal output.
- Do not pad with background the sources do not supply. If the corpus cannot answer part of the
  question, say so under **What is unresolved** — do not fill the gap from memory.
- Never infer a date, a place, or a quantity that is not in a source. Numbers come from findings.

## 4. Plain technical language

Short sentences. Concrete nouns. Numbers and dates rather than adjectives.

**Banned filler** — if any of these appear, the sentence is rewritten:

| banned | banned | banned |
|---|---|---|
| rich tapestry | delve / delve into | nuanced interplay |
| sheds light on | it is important to note | multifaceted |
| a testament to | navigate the complexities | underscores the importance |
| paints a picture | weave together | at its core |
| the intersection of | speaks volumes | plays a crucial role |
| a fascinating glimpse | complex and dynamic | serves as a reminder |
| in the realm of | tapestry of | profound implications |

Also avoid: opening a section with "In conclusion", hedging stacks ("it may perhaps possibly
suggest"), and any sentence that would survive unchanged if the topic were swapped for another.

Test: delete every adjective. If the paragraph loses no information, it had none.

## 5. No invented terminology; define specialist terms on first use

- Never coin a term. Use the term the sources use.
- The first time a specialist term appears, define it **in one clause, inline**:
  "the Kurgan hypothesis (that Indo-European languages spread from steppe burial-mound cultures)".
- If more than five such terms appear, add a **Glossary** block at the end, one line each.
- Transliterations and reconstructed forms keep their conventional marking (`*h₂ner-`), with a note
  on first use that `*` marks a reconstruction.

## 6. Structure

Every compiled answer follows this order:

1. **The question** — restated in one sentence, with the filter that was run (tags, region, period).
2. **What the sources say** — grouped **by period, then region** (the same nesting the aggregation
   tree uses). One subsection per group; every paragraph cites.
3. **Disagreements** — claims that conflict, who holds which position, what evidence separates them,
   which edges connect them.
4. **What is unresolved** — questions the corpus does not answer, and what kind of source would.
5. **Sources used** — citekeys with one-line relevance notes.
6. **Not used** — citekeys the filter returned that were excluded, with reasons.

Sections 3 and 4 are not optional. If there genuinely are no disagreements, write "The sources in
this filter do not conflict on this point" — that is itself a finding.

---

## Self-check before returning an answer

- [ ] Did I enumerate the whole result set, not just page one?
- [ ] Does every paragraph cite a citekey?
- [ ] Did I run `neighbors` on the sources I leaned on, and report what came back?
- [ ] Are `superseded` / `debunked` / `retracted` statuses stated in the text?
- [ ] Zero banned-filler phrases?
- [ ] Is every specialist term defined on first use?
- [ ] Are all six sections present?
- [ ] Are there **Sources used** and **Not used** lists?
