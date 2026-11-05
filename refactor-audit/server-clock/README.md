# Supplied wall-clock batch

## Method-body continuation, verified

The next configuration applies contextual-clock propagation to both remaining
confirmed direct method-body reads: DiskLru.get
(`CAP-7f7a906cb3c5171963abaa8d6de34267c1f779e78197280f9665e6ef4f22591f`)
and JavaRangeHttp.send's retry calculation
(`CAP-9b8af934229dd717b94c86a3349175d4243046c679f923e1722f6f0bbc7699ab`).
The latter retains its ambiguous local-owner identity; exact before/after ID
reconciliation is required, not an aggregate decrease. Its previous targeted
treatment can use the same configured propagation transformation as DiskLru.

The full-source preview covers 3,159 files, changes five production and two test
files, and resolves thirteen callers. OnDemandTiles forwards the clock to DiskLru;
BasemapService and BasemapWiring supply their existing clock. Test constructors
explicitly supply a system clock, preserving their prior timing behavior. DiskLru
retains Instant.ofEpochMilli(clock.millis()), including millisecond truncation.

Excluded from this transformation: global callback defaults in OnDemandTiles,
StorageCache, StorageFigures, Polite and PolicyHttp. They require default removal
or earlier-parameter dependency propagation that preserves explicit callback
overrides; a contextual parameter appended after a default is not its scope.
Monotonic timing, environment access and unresolved recording-adapter ownership
also remain outstanding. No exception or compliance classification is granted.

`server-clock-body-pipeline.conf` runs the existing compilation, paired basemap
behavioral tests, project assessment and exact capability-ID reconciliation. The
expected result is removal of exactly the two IDs above with every other capability
row and multiplicity unchanged. No new batch-specific verifier is introduced.
The pipeline passed: all 3,159 source/test files compile, and the same 47 test
identities pass before and after with no failures or skips. The post-observation
`capability-history/observations/2026-11-05T11-04-52.562146Z` retains exactly the
before rows minus the two reviewed IDs. It contains 77 anchors / 94 occurrences,
with zero new/reopened IDs or multiplicity changes. Review-3 now has fifteen
confirmed outstanding anchors / sixteen occurrences. Candidates (48 anchors / 64
occurrences), thirteen unresolved adapter operations and one non-violation remain.
The earlier eight-ID/nine-occurrence repair history is unchanged.

Isolation still has 111 production and 215 test relationships, 75 ambiguous owners,
182,326 unclassified direction rows, and zero new/reopened relationships. Model
primitive/unresolved counts are unchanged; four added clock parameter positions
increase declared/inferred positions to 107,466 without shrinking a denominator.

Existing tests cover cache eviction/restart, on-demand tiles and basemap behavior.
No new fixed-clock HTTP retry probe or native-image verification is claimed.
Compilation, paired tests and exact-ID reconciliation constitute the standard
verification contract for this batch; full evidence is in body-build-1,
body-tests-1 and body-pipeline-1. No new batch-specific runner or verifier was added.

## First completed batch

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
