---
name: histdb-compile
description: Write a research answer, topic summary or article from the hist-db corpus — the rules for structure, citation, contradiction-hunting and plain language. Use whenever compiling prose out of the database, so answers exhaust the topic, cite every paragraph and surface disagreements between sources.
---

# Compiling prose from hist-db

The rules live in one place: **[`docs/writing-guide.md`](../../docs/writing-guide.md)** in the hist-db
repo. Read it before writing. This skill is the short version plus the workflow.

## Workflow

1. **Query first.** Use the `histdb-query` skill: `search` → narrow → `get_source` → `neighbors`.
   Do not start writing until you have enumerated the whole result set.
2. **Draft against the structure** (guide §6):
   1. The question — restated in one sentence, with the filter you ran.
   2. What the sources say — grouped **by period, then region**, every paragraph citing a citekey.
   3. Disagreements — who holds which position, what evidence separates them, which edges connect them.
   4. What is unresolved — and what kind of source would settle it.
   5. Sources used — citekeys, one-line relevance note each.
   6. Not used — citekeys the filter returned but you excluded, with reasons.

   Sections 3 and 4 are **not optional**. "The sources in this filter do not conflict on this point"
   is a valid section 3 and is itself a finding.
3. **Lint the draft** against the checklist below.
4. **File it.** Terminal answer, or a markdown report. (Article storage in the DB — `put_article`,
   `/article/{slug}` — arrives in wave 2.7; until then a report is a file.)

## The five rules, compressed

1. **Exhaust the topic** — every source the filter returned is used or explicitly excluded with a
   reason. Three sources out of forty is a wrong answer even if every sentence is true.
2. **Hunt contradictions** — run `neighbors` on everything you lean on; surface every
   `refutes`/`challenges`/`supersedes` edge; state overturned hypotheses with years; never silently
   use or silently drop a `superseded`/`debunked`/`retracted` source.
3. **Every paragraph cites a citekey.** A paragraph without one is deleted, not rewritten.
4. **Plain technical language** — short sentences, concrete nouns, numbers and dates. Banned filler
   below.
5. **No invented terminology**; define each specialist term in one inline clause on first use; add a
   glossary if more than five appear.

## Banned filler (rewrite the sentence)

rich tapestry · delve / delve into · nuanced interplay · sheds light on · it is important to note ·
multifaceted · a testament to · navigate the complexities · underscores the importance · paints a
picture · weave together · at its core · the intersection of · speaks volumes · plays a crucial role ·
a fascinating glimpse · complex and dynamic · serves as a reminder · in the realm of · tapestry of ·
profound implications

Also: no "In conclusion" section openers, no hedging stacks ("may perhaps possibly suggest"), and no
sentence that would survive unchanged if the topic were swapped for a different one.

Quick test — delete every adjective from a paragraph. If it loses no information, it had none.

## Self-check before returning

- [ ] Whole result set enumerated, not page one
- [ ] Every paragraph cites a citekey
- [ ] `neighbors` run on the sources I leaned on, and the results reported
- [ ] `superseded`/`debunked`/`retracted` statuses stated in the text
- [ ] Zero banned-filler phrases
- [ ] Every specialist term defined on first use
- [ ] All six sections present, including Disagreements and What is unresolved
- [ ] **Sources used** and **Not used** lists at the end

Full rules and the reasoning behind them: **`docs/writing-guide.md`**.
