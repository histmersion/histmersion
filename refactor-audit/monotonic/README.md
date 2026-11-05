# Monotonic timing batch: wired and remeasured

The 28-file Scalafix wiring batch compiles for all production and test modules.
TimerSuite (3) and PassageFinderSuite (21) pass without skips, including the
pathological regex timeout with an explicitly supplied elapsed duration. Ordinary
passage tests use a frozen timer; the timeout case uses an already-exceeded timer.
Their original behavioral assertions are retained. Database query, geometry and
backup-job runtime tests have not been run in this checkpoint.

measurement-2 covers all 2,377 production files and passes all 31 source assertions.
Identity reconciliation proves exactly the thirteen reviewed timing occurrences
removed, with no other added identities or changed residual multiplicities:
118 → 105 capability occurrences. All 42 occurrences in the clock treatment review
are now recorded fixed across the wall-clock and timing batches. This is bounded
repair evidence, not whole-family sampled acceptance or 90% migration completion.

The two SystemTimer raw clock reads and six selections of real adapters in Deps
and SystemContext are explicit allowed adapter/composition evidence, retained in
reconciliation-1/accepted-boundaries.tsv. These are policy-boundary identities with
multiplicities, not eight independently disambiguated source anchors. The policy
now forbids concrete timing adapter types outside the specified composition owners.
The fixture preserves two positive consumers, a supplied-capability negative and
allowed composition. Exact fixture matching passes; normal enforcement rejects both
consumer factories (CLI exit 64). No original finding baseline was reset.

The first prefix-based factory policy also matched ThreadPause's duration parameter.
measurement-1 retains that false positive. The final exact-type policy fixes it
without granting a self-permission. Both policy versions and unchanged source
assertions are retained in capability-history/corrections/monotonic-timing, which is
chained into the history configuration. This consumes capability refinement round
1 of 2. It is development-fixture validation, not an independent quality sample.

The full multi-family assessment/history gate and independent detector acceptance
remain outstanding. Production and test compilation covered 3,149 files; the
capability scan covers production only. Test-policy findings remain unmeasured.

## Retained foundation checkpoint (superseded by evidence above)

The remaining reviewed timing batch is thirteen production occurrences, with
original IDs in capability-treatment/review-2 and outstanding status retained in
wall-clock/reconciliation-1. This checkpoint claims no repaired occurrence.

TimingCapabilities created five parsed source files through Scalafix. Timer and
Pause are abstract mechanisms in core, so db and server consumers share them without
introducing a core-to-server dependency. SystemTimer and ThreadPause are explicit
server adapter instances, with no global singleton or domain fallback. The timer
returns a measurement or deadline using FiniteDuration instead of exposing raw
timestamp primitives in domain signatures. SystemTimer computes elapsed nanoseconds
by subtraction; wall-clock adjustments do not change elapsed time. Pause delegates
to interruptible TimeUnit sleep, preserving sub-millisecond duration precision.

Full production and test compilation pass on 3,149 files. TimerSuite passes all three
tests without skips: equality versus strictly passing a deadline, independent
measurement start times, and a zero budget. These are contract tests, not runtime
proof of migrated consumers. The real adapters are not wired yet. Their two raw
System.nanoTime calls are new adapter-boundary evidence; their classification and
exact-owner detector permission must be recorded with wiring, not hidden in counts.
No capability permission or baseline has been changed at this checkpoint.

TimingPreview generates seven hash-guarded files covering all thirteen original
reads, plus QueryService's dependency forwarding. The preview parses and records
AST shapes but has not been applied or compiled with its callers. It preserves
strict `>` in regex limits, `>=` in place-repair budgets, and millisecond conversion
for JDBC, model calls and probe timings. Backup polling uses a three-hour monotonic
deadline and supplied interruptible pauses, so wall-clock jumps no longer alter
the intended elapsed timeout. That behavior needs a deterministic polling test.

## Required caller batch

- Pass Timer/Pause from composition through SystemContext, ServerGraph, Deps,
  EngineSite and ServerEngineSite. AgentsControllers exposes its supplied instances;
  RunCtx passes the same timer to LatencyInterceptor. Choose real adapters at
  composition; do not add domain defaults or use a global adapter.
- QueryService receives Timer and forwards it to the repository's borrowed DbCon
  execution. LibraryWiring supplies deps.timer. Timing must not change transaction
  ownership or move SQL outside readOnlyRollback.
- Supply Timer to SourcePassages and its Text reader, SourcesControllers, and
  PassageFinder.find. Body rendering and regex compilation remain pure and do not
  require a timer. Adapt core tests with a deterministic advancing timer so the
  pathological-regex test cannot hang behind a frozen timer.
- Supply Timer to PlaceGeometry's factory and implementation from CatalogueWiring
  and PlacesData. CheckContext must carry the selected timer from check composition;
  it must not construct one inside a domain fix.
- Record exact adapter-boundary decisions and prevent unapproved domain construction
  of those adapters. Preserve the current capability baseline and assertion history.

After applying definitions and callers together, compile all modules/tests, run
timer, passage, query and relevant job tests, and remeasure every production file.
Compare finding IDs and multiplicities, including newly introduced adapter evidence.
Keep the thirteen original findings outstanding until those checks pass. This work
does not consume a detector refinement round, accept a domain violation, or defer
known violations under the boy-scout rule.
