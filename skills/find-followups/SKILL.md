---
name: find-followups
description: Given a source already in the corpus, walk the citation graph forward to find follow-ups, critiques, debunkings and superseding work — classify each citing paper as confirms/challenges/refutes/supersedes, record the typed edges, and queue the important new papers. Use when the user asks "is this still accepted?", "what came after X?", "has this been debunked?", or to keep a topic current.
allowed-tools: Bash(histmersion:*), {{tool:*}}
---

# Find follow-ups, critiques and debunkings

## 1. Pull the citing papers

```bash
histmersion followups <citekey|doi> [--since 2015] [--limit 50]
```

`{{tool:followups}}` with `id` (+ `since`, `limit`).

Each result carries Semantic Scholar's citation **intents** (background/methodology/result), the
influential-citation flag, the actual citing **contexts**, and a **proposed edge kind**:
`confirms | challenges | refutes | supersedes | cites`. Results are ordered so influential +
method/result + recent come first.

## 2. Judge the proposals — do not rubber-stamp them

Read the contexts. The proposed kind is read from language, so it is a hypothesis:

- **confirms** — replicates or extends the finding.
- **challenges** — disputes methods or interpretation ("however", "contrary to", "fails to").
- **refutes / supersedes** — newer evidence overturns it; `supersedes` implies the older source's
  status changes.
- Ambiguous snippet → ingest the citing paper (**ingest-paper** skill) and read it before deciding.

## 3. Record what you concluded

```bash
histmersion followups <citekey> --apply     # record the proposed edges for citing papers already in the corpus
```

For anything you decided differently, patch the edge yourself — edges are directed
`src --kind--> dst` and are stored on their **source**, so patch the citing paper (`src` may be
omitted; a `src` other than the patched citekey is refused):

```
{{tool:patch_source}} {
  citekey: "kassian2024_rebuttal",
  patch: { edges: { add: [ { dst: "heggarty2023_language", kind: "refutes" } ] } }
}
```

CLI equivalent: `histmersion patch kassian2024_rebuttal --add-edge refutes:heggarty2023_language`
(repeatable; `--remove-edge <kind>:<citekey>` drops a wrong one). Never `put_source` — it erases
everything else.

Then set the older source's status:

```bash
histmersion patch <old-citekey> --status superseded      # or debunked / contested
```

There is no `superseded_by` field any more: the `supersedes` edge from the newer source **is** the
relationship, and `histmersion neighbors <citekey>` (`{{tool:neighbors}}`) walks it.

## 4. Make the disagreement visible

A status change that no reader sees is worthless. Add the verdict where the claim lives:

- the source `notes` (`{{tool:patch_source}}` → `notes`), and
- the concept article that cites it (`{{tool:get_article}}` → `patch_article` with `append`),
  naming both papers with years: "Heggarty (2023) dated PIE to ~8100 BP from Bayesian phylogenetics;
  `[[kassian2024_rebuttal]]` refutes the cognate coding."

Queue the citing papers worth having:

```bash
histmersion queue tasks add "10.x/y — <paper>, refutes <citekey>; ingest" --topic "Followups: <topic>"
```

## 5. Report the verdict per claim

Tell the user, claim by claim: still accepted, contested, or overturned — naming the specific newer
papers and years. That is the core "which theories are outdated" capability.

## Done means

The edges exist (`histmersion neighbors <citekey>` shows them), the statuses match the evidence, the
articles that cite the source say what changed, and `histmersion lint --rule dangling-edges` is clean.
