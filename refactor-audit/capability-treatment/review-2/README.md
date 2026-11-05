# Domain clock treatment review

This review confirms 42 production occurrences in 38 existing anchors across
15 files. These are existing candidate identities adjudicated from source, not
42 newly introduced regressions or 42 independent architectural boundaries.
No code was repaired and no exception was granted in this checkpoint.

DomainClockTreatment consumes the complete frozen 147-occurrence, 125-anchor
observation. The other 87 anchors remain in observation.tsv with their original
identities and evidence. This is an additive decision ledger over that observation;
it does not replace review-1, earlier repair evidence, or the identity baseline.
The tool verifies each selected file against its measured source hash and checks
that the parsed selection AST multiplicities agree with the observation before
writing decisions. Evidence retains ambiguous identity groups rather than assigning
an arbitrary source line to one of several indistinguishable anchors.

The source review checked direct System clock reads, zero-argument Instant.now,
and LocalDate.now(ZoneOffset.UTC) in domain code. Supplying a UTC zone does not
supply a clock. An optional callback defaulting to a global read remains a bypass:
the caller can omit the capability. Existing injected-clock calls are not selected.
This is detector-informed treatment review, not an independent precision/recall
sample. No quality threshold or detector refinement round is claimed.

| Batch | Occurrences | Treatment | Required verification |
|---|---:|---|---|
| Wall clock | 29 | Bulk rewrite | Supply Clock through definitions and callers; preserve explicit now arguments, epoch-millisecond units, UTC calendar dates, and instants. Fixed-clock tests must cover defaults and internal reads. |
| Monotonic clock | 11 | Design then reusable rewrite | Supply a monotonic timer to core/db/server consumers; preserve nanosecond deadlines, elapsed durations and regex interruption. Wall-clock Clock is not a substitute. |
| Polling deadline | 2 | Design then reusable rewrite | Autopilot backup polling needs supplied timing and waiting together; preserve the bounded wait, interrupt behavior and status polling. |

The 29 wall-clock sites span AgentsControllers, Engine, ModelPicker, RunCtx,
RunTokens, CandidateSearch, CoreBudget, SttpHttp, AcquireRecheckService and OaBacklog.
Several already reach an injected Clock through agents or engine; others require
constructor and caller propagation. Treat them as one declared batch with explicit
substeps, not one rewrite invocation per occurrence. No domain default may silently
construct a system Clock. Any actual system-clock construction belongs at an
approved composition boundary.

The monotonic sites are PassageFinder (3), QueryRepo (2), AgentsControllers (2),
LatencyInterceptor (2), and PlaceGeometry (2). PassageFinder is in core and must
not gain a server dependency. The timer abstraction must therefore live in an
allowed shared mechanism module, with the real adapter supplied at composition.

Next: freeze the 29-site rewrite contract, cover existing-clock and added-clock
constructor shapes with expected-output and fixed-clock fixtures, then apply the
batch and compile definitions plus callers together. Retain all 13 design sites
as confirmed outstanding until repaired or explicitly accepted. They are not
boy-scout deferrals. Test capability coverage and independent family acceptance
remain open, as do the remaining unreviewed capability candidates.
