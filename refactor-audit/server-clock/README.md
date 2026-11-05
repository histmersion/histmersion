# Supplied wall-clock batch

ClockAccessPreview is a configured semantic Scalafix rule: resolved zero-argument
Instant.now/currentTimeMillis calls are rewritten to an explicitly selected Clock.
Owner paths, expected occurrence counts, contextual constructor definitions and
constructor caller symbols/providers are configuration. It traverses the complete
3,159-file source universe, records real symbols/ASTs and freezes all source hashes.
Unknown configured constructor callers, count changes and overlapping patches fail.
ApplyFrozenPreview applies only the reviewed, hash-matching outputs.

The first batch replaces nine reads in four owners: BasemapService (four),
ServerWiring (three), InstanceControllers (one), MaintenanceControllers (one).
BasemapService gains `(using clock: java.time.Clock)`; BasemapWiring supplies
deps.clock. Existing Deps receivers supply the other reads, including default
arguments and callbacks. Explicit time overrides remain intact. Five production
files change together, plus one generated regression-test update.

## Behavioral evidence

The original 38 tests across six suites pass with a real isolated PostgreSQL database.
A Scalafix AST recipe then supplies a fixed clock through Deps and adds an assertion
for vector, terrain, labels and bathymetry metadata timestamps. Before the production
rewrite, exactly this test fails: the obtained values are live 2026 timestamps,
while the expected value is 2000-01-02T03:04:05Z. The other 37 tests pass.
After the batch, all 38 identities pass without skips. Full production/test compilation
passes on all 3,159 files. This probe directly tests metadata behavior; it does not
independently exercise every controller/default or the alternative GEBCO branch.

The initial probe constructor omitted the required query datasource for Deps' clock
overload and failed compilation. A separate AST patch adds the same datasource used
by the original two-argument factory. No assertion was weakened. Both previews and
the failed-build log are retained. The first unapplied probe preview's bare block was
superseded by an explicit locally block before compilation to avoid parsing it as
an argument to the preceding expression.

## Scope and acceptance

The preview edits zero-argument semantic calls and configured Term.Apply constructor
sites. It is not a general contextual-dependency solver: alternate constructor forms,
eta expansion and more complex propagation need explicit shapes and compilation.
The current source traversal/count guards and whole-build compile establish this
batch's bounded caller coverage. The initial guessed companion symbol failed its
caller-count guard; actual SemanticDB identifies Scala 3 construction as the class
symbol ending in `#`, which the final configuration records.

ServerClockVerification requires an exact source-hash patch chain (including test
construction repair), the full compiled source universe, the same test identities,
the failing-before/passing-after probe, and a fresh capability observation removing
exactly eight reviewed anchors representing nine occurrences. It retains every other
identity and multiplicity and emits the original-ID treatment ledger. Verification
passes in verification-1: eight anchors / nine occurrences repaired, 96 remaining
capability occurrences, no other identity or multiplicity change.

The remaining review-3 findings stay outstanding/candidate/unresolved as recorded.
Clock defaults, the cache/timer propagation chain, CLI environment and home expansion
remain required batches. Copy automation stays paused. No new detector refinement
round, independent precision/recall acceptance or migration-wide 90% claim follows
from this batch.

The subsequent execution correction in ../execution-priorities.md stops further
repairs until the all-family acceptance pass and existing-tool consolidation. These
batch-specific tools predate that correction; do not add more without user approval.
