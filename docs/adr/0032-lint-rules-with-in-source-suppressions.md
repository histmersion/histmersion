# 0032. The checks are stateless lint rules; a finding is fixed or tolerated in the source, with a reason

- Status: accepted (owner decision of 2026-11-24, queue row 42)
- Date: 2026-11-24
- Supersedes: [0023](0023-guideline-metric-redefinitions.md) (its proof of a redefinition's count, the merge-base tree
  inventories and the ratchet files); [0017](0017-ratchet-checker-scope.md) and
  [0020](0020-checker-scope-lifted-instances-and-own-names.md) keep only what they say a rule counts

## Context

The guideline inventory, ContractsSuite, ArchitectureSuite, TotalOrder and ReadOnlyRoutesSuite each compared their
findings with a list in a TSV file (`guidelines-ratchet.tsv`, `contracts.tsv`, `exceptions.tsv`, `places.tsv`,
`total-order.tsv`, `get-writes.tsv`, `boundaries.tsv`, `text-branch-exceptions.tsv`), kept by update modes, an approval
file (`accepted-rises.tsv`) and a proof that measured the merge-base tree. The owner (2026-11-24): "I do not want us
maintaining some goddamn list of tsv files … the Scalafix rules for verification they should either fail or be
suppressed with some no-warn that explains why they are being bypassed for a moment. With the goal being that we get
the list of nowarn to zero. Its absolute abomination if you have a stateless scalafix rule that is actually operating
with side effects writing to an external file."

## Decision

- **Every check is a lint rule that reads the sources and writes nothing.** `tools/lint/` is a scala-cli project of
  Scalafix rules, run by `tools/lint/lint.sh` over every module, main and test, with the SemanticDB of the last
  compile: `GuidelineLint` (the guideline inventory's rules), `ContractLint` (ContractsSuite's), `ArchitectureLint`
  (ArchitectureSuite's and TotalOrder) and `ToleratedLint` (the shape of a suppression). A finding fails the run.
- **A suppression is an annotation on a definition, with a reason.** `@tolerated(LintRule.X, "pending G44 (…)")`
  (module `modules/lint`, a compile-only dependency of every module) drops rule `X`'s findings inside the annotated
  definition. A file's rule (Placement, Direction, Layer, FileSystem, Process, Repository, Bucket, G47Package), and a
  finding outside every definition (an import), is tolerated on a top-level definition of the file. The rule is a
  `LintRule` case, never a string; the reason a non-blank literal. Comments, `scalafix:off` and
  `@SuppressWarnings("scalafix:…")` are no suppression. A `@tolerated` whose definition has no finding of its rule left
  fails: fixing a site means deleting its annotation, so progress is the count of annotations falling.
- **What was data is a typed annotation too.** A boundary file carries `@atBoundary(BoundaryKind.Sql, "why")` (was
  `boundaries.tsv`); an unplaced file `@movesTo(FeaturePlace.LibrarySources)` (was `places.tsv`; it is the Placement
  rule's toleration, and the Direction rule's place for the file). A route that writes on GET carries
  `@tolerated(LintRule.GetWrites, "why")` on its endpoint, which ReadOnlyRoutesSuite reads from the compiled trees
  (the call graph is no per-file lint). `Unused(answer, Unused.Why.X)` stays the in-code reason for a dropped write.
- **No rises, computed from git.** `tools/lint/count.sh` counts the `@tolerated` per rule, the `@movesTo`, the
  `@atBoundary` and the main-code `Unused` calls at HEAD and in the blobs at the merge base with `contracts3-pilot`;
  any count above the base's fails CI. A suppression that moves with its code changes no count. A rule the base's
  `LintRule` enum does not have starts at the baseline the PR that adds it inserts.
- **The baseline is mechanical.** `GuidelineBaseline`, `TextBaseline` and `FileBaseline` (the `baseline-*.conf` of
  `tools/lint`) insert one annotation per (innermost member definition, rule) with the reason naming the register goal
  it waits for; another branch re-runs them instead of merging annotations by hand.
- **A new finding inside a tolerated definition is not told apart.** The goal is zero suppressions; a new finding in a
  tolerated definition keeps its annotation from being removed, which is the signal.
- **What did not become a rule.** G22 (comments) stays the git comparison of each file's comments with the merge base:
  a comment cannot carry an annotation. `unwrap` is not ported: every Iron `.value` it found is a G44, G44Conversion or
  G45Boundary finding (12546 of 12561 hits on 8bcf100b1; the other 15 are `.value` fields of other types). G44PlainDef
  is reported at the call that feeds `.value` into a public plain parameter, not at the parameter (a lint sees one
  file). `architecture/migrations.tsv` and the sentence ledgers stay with their suites; a re-pin of an applied migration
  is the owner's signed commit.

## Consequences

- The TSV ratchet files, their update modes (`HISTMERSION_UPDATE_ARCHITECTURE` for them), `accepted-rises.tsv`, the
  accept scripts and the merge-base tree inventories are gone; CI's `lint` job is the check.
- Every rule is per file: the cross-file rules read the project's SemanticDB index or its sources, read-only.
- The number of `@tolerated` per rule is the progress measure of the register's goals.

## Code

`modules/lint` (`LintRule`, `tolerated`, `atBoundary`/`BoundaryKind`, `movesTo`/`FeaturePlace`); `tools/lint/rules`
(`Tolerance`, `Shape`, `Guidelines`, `Contracts`, `Architecture`, `TotalOrder`, `Baseline`, and their tests);
`tools/lint/lint.sh`, `test.sh`, `fixture.sh` (the guideline rule over `tools/refactor/guideline-fixture/expected`),
`count.sh` with `tools/lint/count/Suppressions.scala`; CI's `lint` job; `ReadOnlyRoutesSuite` and `RouteWrites.judge`
(GetWrites); `CommentGrowthSuite` (G22).
