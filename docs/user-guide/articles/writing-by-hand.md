# Writing an article yourself

You need no AI to write articles. You get the same help the agents get: the formatting checks, the
link checks, and the generated *Not used* list and *Method* note.

## 1. Decide what the article covers

Find the sources first, and write the search down — it becomes the article's **filter**:

```bash
histmersion facets topics                       # the topics that exist
histmersion search --topic steppe_hypothesis    # → 16 sources, say
histmersion search --topic steppe_hypothesis --status contested --status superseded
```

Read what each source says (`histmersion get`, `passages`) and note every claim with its citekey
before you write prose. The writing rules ({{ repo_file("docs/writing-guide.md") }}) call this the
claim table.

## 2. Write the note

An article is a Markdown file with a short header (frontmatter):

```markdown
---
type: concept
slug: steppe-hypothesis
title: The steppe hypothesis
summary: One or two sentences on what the article covers.
tags: [indo-european]
regions: [Pontic-Caspian steppe]
period_start: -4500
period_end: -2500
---

## Summary

The steppe hypothesis places … [[anthony2007]].

## What the sources say

…

## Disagreements

… [[critic2015]] challenges … [[anthony2007]] …

## Open questions

What the collection cannot answer, and what kind of source would.
```

- `type` is one of `concept`, `myth`, `culture`, `region`, `timeline`, `report`, `index`.
- Cite a source as `[[citekey]]` and another article as `[[slug]]`. **Every paragraph cites a
  source.** Never write author-year citations instead; the links are what build the *Sources cited*
  list and the backlinks.
- Do **not** write a *Sources cited* section, a *Not used* section or a *Method* note — the page
  generates them.

Check it before sending, and send it:

```bash
histmersion lint --file steppe-hypothesis.md         # formatting problems (--fix repairs the safe ones)
histmersion article put steppe-hypothesis.md --strict  # create it; --strict refuses one with an error
```

`article put` **replaces** a whole article. Use it to create one; to change one, use `patch`.

## 3. Record the filter and the reasons

```bash
histmersion article patch steppe-hypothesis --filter-topics steppe_hypothesis
histmersion article patch steppe-hypothesis \
  --reason older1990='restates [[anthony2007]] with no new evidence' \
  --reason regional2012='about the Caucasus only'
histmersion article coverage steppe-hypothesis
```

Now the page lists every source the filter finds and the article does not cite, with your reason or
*not yet assessed*. `coverage` also lists the **critique checklist**: each disagreement link that
touches a cited source, and whether the article addresses it (both sides cited in one paragraph) or
has a reason why it does not bear on the article (`--critique-reason <src>><dst>=<why>`).

## 4. Change it later

```bash
histmersion article patch steppe-hypothesis --append-file new-section.md   # add at the end
histmersion article patch steppe-hypothesis --body-file whole-body.md       # replace the body
histmersion article patch steppe-hypothesis --summary "…"                   # one field
histmersion article check steppe-hypothesis --body-file draft.md            # dry run: what the checks would say
histmersion article versions steppe-hypothesis                              # history
```

Each patch changes only what you pass and keeps the previous body as a version. `--if-updated-at`
refuses a patch when someone else changed the article since you read it.

## The rules in short

From {{ repo_file("docs/writing-guide.md") }}:

1. Every claim is traceable to **one** source. If no single source makes a comparison, the article
   does not make it.
2. A source's status comes from the database and is stated where the source is first cited.
3. Disagreements get space by their standing in the collection, not 50/50.
4. Gaps go under *Open questions* / *Unresolved*, with the kind of source that would fill them —
   never filled from memory.
5. Define every specialist term in 20 words or fewer on first use; add a Glossary when there are more
   than five.
6. No opinion of your own, no workflow notes, numbers over adjectives.
