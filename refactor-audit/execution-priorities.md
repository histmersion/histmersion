# Migration execution priorities

This changes execution order, not the overall migration goal or its 90% automatic
repair target. It supersedes earlier instructions to choose the largest or easiest
rewrite category first. Detection and repair are separate deliverables.

## Immediate sequence

1. Close validation of the current test-copy batch: compilation, unchanged test
   identities, no new failures or skips, fixture checks and project-wide remeasurement.
   Preserve the failed copy holdout (20 TP, 3 FN, 0 FP, 5 TN). Then stop expanding
   copy detection and rewrite automation; retain residual findings and limitations.
2. Establish identity-based evidence/history and regression checking for transaction
   ownership, capability access, module isolation and primitive/newtype modeling.
   Aggregate count gates alone do not meet this requirement.
3. Establish the same bounded measurement contract for every remaining family in
   migration-rounds.tsv. Unsupported checks remain explicitly unmeasured.
4. Choose repair batches by architectural importance and safe common transformation,
   with definitions and callers together. Difficult detection must not block other
   useful families. Required design work remains visible alongside bulk opportunities.

## Bounded measurement contract

Each family reports production and tests separately, with its source/module manifest,
policy and detector fingerprints, supported shapes, omitted shapes, sample confidence,
and explicit unresolved evidence. Scanning every file is not complete semantic coverage.
Keep the existing two-refinement-round limit per family. At that limit, publish useful
supported coverage and a residual backlog; do not keep expanding the inventory.

Evidence classes are confirmed violation, candidate, unresolved, accepted exception,
and adjudicated non-violation. Unsupported repair never changes the evidence class.
Every confirmed finding gets one treatment: bulk rewrite, targeted rewrite,
manual/design work, or explicitly accepted exception. Exceptions require a specific
reason and scope; legacy suppression rows are not automatically accepted exceptions.

IDs are persistent ledger keys, independent of line numbers and aggregate counts.
Attach semantic owner/role, rule, source location and AST evidence as revisable anchors.
Moves/renames require an explicit identity mapping; ambiguous matches stay unresolved.
Repeated identical expressions must remain distinct occurrences. Related transaction
diagnostics can share a boundary ID without deleting their individual evidence.

Keep append-only observation and adjudication events: first seen, confirmed, treatment
assigned, moved, corrected, repaired, verified, reopened, or exception accepted/revoked.
Absent findings are not verified fixes until comparable coverage and repair validation
support that conclusion. A new ID must fail regression checking even when an old ID
disappears and the total count is unchanged. Policy/coverage changes require explicit
reconciliation, never automatic baseline acceptance. Accepted exceptions remain scanned.

## Reporting and completion

Per family and production/test scope, publish coverage/confidence, confirmed outstanding,
verified fixes, candidates, unresolved cases, accepted exceptions, and new regressions.
The migration denominator is the versioned confirmed inventory; additions and corrections
are events, not silent replacement. Preserve prior snapshots and distinguish automatic
from manual fixes. Do not add raw expression counts, boundary counts and relationship
counts together. The 90% target is assessed across the migration, not just copy updates.

Current aggregate regression gates are provisional. Stable identity/history support is
required work, not an implemented guarantee merely because this document specifies it.
