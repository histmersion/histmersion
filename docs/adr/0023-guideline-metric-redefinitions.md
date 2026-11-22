# 0023. Guideline metric redefinitions, and how a redefinition's count is proven

- Status: accepted (owner decisions 1–5 of 2026-11-19; the proof approved by the coordinator 2026-11-22)
- Date: 2026-11-22

## Context

The owner redefined several guideline metrics on 2026-11-19 (notes/refactor-goals.md, decisions 1–5). A redefinition
that finds less is a fall, which the suite's update mode writes. A redefinition that finds more (closing the
interpolation hole) is no rise of the code, but the ratchet file at the merge base lists the old, lower count. Since
2026-11-21 no accept script may approve it: a count above the base's file must be proven from the merge-base tree.

Until now that proof (`RatchetBase.TreeCount`) covered only a key the base's file did not list, and only for a checker
that reads source text (G22comment), whose tree inventory measures the base's blobs directly. The guideline inventory
is a Scalafix rule over SemanticDB: the base tree cannot be measured without compiling it.

## Decision

### The proof

- **Every key a tree inventory owns may exceed the base's file, new or listed, by no more than the merge-base tree
  measures under the checker's current definition.** A key no inventory owns still fails above the base's file.
- **The guideline inventory stands for the merge-base tree while the branch changes no input but the checkers' own
  files.** Its inputs are the modules' Scala sources, the build (`build.sbt`, `project/`) and the files its policy reads
  (`architecture/boundaries.tsv`, `architecture/text-branch-exceptions.tsv`). The checkers' own sources (the ratchet
  suites in `modules/server/src/test/scala/com/histmersion/server/`) are inputs too, so their findings are left out
  of the measurement: it counts the findings of the files the branch left alone, which the base tree has unchanged, a
  lower bound of the base's count. A redefinition's new code in the checkers therefore must add no finding of a risen
  key. Any other changed input refuses the proof and names the paths: a redefinition lands apart from code changes,
  and the base tree is never compiled.

### The redefinitions

- **G49 is a label, not a count** (decision 3). Every `.value` it named already carried a G44, G45boundary or
  G44conversion finding at the same place, so it counted those twice. The shape it named (a lambda passed to a
  collection operation, an argument of contains/indexOf, a comparison of two `.value`s) is now the suffix
  `;G49:<shape>` of that finding's detail.
- **G45boundary counts only taking the raw value out** (decision 2). In a boundary file (`architecture/boundaries.tsv`)
  `.value` is still a finding; building a newtype there from outside data (`apply`, `assume`, `either`, …, or one of
  them passed as a function) is the boundary's job, policed by G68, and no finding of any rule.

## Consequences

- A checker's redefinition is reviewed as code (owner decision 2026-11-21, point 4) and its count is proven by the
  suite, with no human approval of the number.
- A branch that redefines a semantic rule carries no code change; the code that lowers the new count follows on a
  branch of its own once the redefinition is merged.

## Code

`RatchetBase.TreeCount`, `GuidelineRatchetSuite.Inventory.AtBase` (and their tests in `RatchetLimitsSuite` and
`GuidelineRatchetSuite`).
