# Migration execution priorities

This changes execution order, not the overall migration goal or its 90% automatic
repair target. It supersedes earlier instructions to choose the largest or easiest
rewrite category first. Detection and repair are separate deliverables.

## Immediate sequence

1. Finish or safely revert the active server-clock batch using existing verification
   tools. No additional batch-specific infrastructure. Copy automation stays paused;
   preserve its failed test holdout (20 TP, 3 FN, 0 FP, 5 TN).
2. Before any further repair, complete one bounded inventory acceptance pass across
   every family in migration-rounds.tsv. Record coverage, independent sample results,
   confirmed findings, candidates, unresolved evidence and accepted exceptions, with
   production and tests separate. Respect the existing two-round limit. Freeze an
   insufficient detector with explicit limits rather than refining indefinitely.
3. Consolidate the existing migration-rounds.tsv as the migration ledger and
   priority-family-status.md as the status report. Reference original finding IDs,
   observations, adjudications and repair histories; do not replace or duplicate them
   in another tracking system. The original 43-goal scope, including all twelve
   historical slices, remains binding.
4. Consolidate existing rewrite/verification tools into one configurable pipeline.
   Batch configuration is allowed. A new batch-specific runner, verifier or report
   framework needs a concrete justification and explicit user approval before creation.
5. Select further batches by reusable transformation and project-wide applicability.
   Apply each supported transformation to all eligible confirmed findings together;
   explain exclusions before proceeding. Compilation, relevant behavioral tests and
   stable-ID remeasurement are the standard contract. Add extra verification only
   for a concrete risk those checks cannot address.

Report inventory acceptance completed, confirmed violations repaired, confirmed
violations remaining and automation coverage separately. Tooling volume, generated
artifacts and repeated scans are not migration progress. Do not claim accepted
inventory completion or an overall repair percentage without supporting evidence.

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
