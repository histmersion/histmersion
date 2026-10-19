# Citations and statuses

Scholarship moves on. A source can be challenged, superseded or retracted after you collected it.
Histmersion records this in two ways: **links between sources** and a **status** on each source.

## Links between sources

| link | means |
|---|---|
| `references` | A cites B (from A's reference list) |
| `challenges` | A argues against a claim of B |
| `refutes` | A shows a claim of B to be wrong |
| `updates` | A revises B (a new edition, a correction) |
| `superseded_by` | A, the older source, is replaced by B, a newer one |

Only the last four are disagreements. A `challenges`, `refutes` or `updates` link is stored on the
source that makes the criticism; a `superseded_by` link on the older source, pointing at the newer.

```bash
histmersion neighbors cline2014          # links to and from one source, and the articles citing it
histmersion graph cline2014 --depth 2    # the network around it (the web page draws it)
histmersion contradictions cline2014     # its disagreements, with the other side's year and status
histmersion contradictions --since 2015  # disagreements across the collection
```

!!! warning "No link is not agreement"
    Most sources have no disagreement links yet, because nobody recorded them. An empty answer means
    "nothing recorded", never "nobody disagrees". Look further with `followups` (below).

## Statuses

| status | means |
|---|---|
| `foundational` | a classic the field still builds on |
| `current` | accepted, as far as is known |
| `contested` | its main claims are disputed |
| `superseded` | replaced by later work |
| `debunked` | shown to be wrong |
| `retracted` | withdrawn by its publisher or authors |
| `unassessed` | nobody has checked yet — the starting point |

Set one by hand, and record the link that justifies it:

```bash
histmersion patch older2001 --status superseded --add-edge superseded_by:newer2019
histmersion patch critic2020 --add-edge challenges:older2001
```

## Checking whether a source still stands

```bash
histmersion assess update-retractions    # refresh the local copy of Retraction Watch (do this first)
histmersion assess <citekey|doi>          # retracted / corrected / current / low-impact
histmersion assess <citekey> --apply      # write the verdict into the status
histmersion refs <citekey> --apply        # its reference list → `references` links
histmersion followups <citekey>           # later papers citing it, each with a proposed link kind
```

- **`assess`** looks the work up in a local copy of the Retraction Watch database (refresh it with
  `update-retractions`; it starts empty), in Crossref's correction and retraction notices, and in
  citation counts.
- **`refs`** reads the reference list the publisher deposited with Crossref and resolves it against
  your collection.
- **`followups`** asks Semantic Scholar which papers cite this one, with the citation context, and
  proposes a kind for each: `confirms`, `challenges`, `refutes`, `supersedes` or `cites`. It is a
  network call (seconds per source), and a proposal is a hint, not a verdict: read the citing paper
  before you record a disagreement.

On the web page, each source shows its status as a coloured badge, and the citation graph window
draws the links.
