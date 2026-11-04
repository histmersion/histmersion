# Independent test-source audit

The bounded `gpt-6-luna` audit reviewed test sources without reading detector
reports. Twenty-three case-class fixture updates were selected across API,
core, database, server, files, and the media/calibre/events plugins, together
with five array/file `copyTo` operations as negative examples. Ordinary fixture
variation is not an exemption merely because the surrounding test checks
compatibility, status validation or rendering. No selected test depended on
generated-copy semantics. The negative examples do not cover same-named
non-case-class `copy` methods; negative diversity remains limited.

The frozen sample is `tools/refactor/copy-test-holdout.tsv`. Verification against
the complete, source-hash-checked operation inventory produced **20 TP, 0 FP,
3 FN, 5 TN**: sampled precision 100%, recall 86.96%. This does **not** meet the
95% recall target. Preserve the failed acceptance result and all sample labels.

Confirmed misses are `WriteSuite.scala:362`, `PeriodsSuite.scala:114`, and
`PortalParsersSuite.scala:206`. They remain required work, alongside unresolved
copy owners, rather than becoming legitimate cases. Both allotted copy-detector
refinement rounds are used; do not start another open-ended resolution effort.

The next planner run is limited to already-resolved case-class owners, accessible
fields, approved optics modules and the verified constructor/update shapes.
It does not turn all 1587 detector matches into an audited violation denominator,
nor claim complete test coverage. Test batch eligibility, unresolved evidence,
and the known missed source cases must remain separate in migration accounting.

Adding this frozen sample changes the policy fingerprint after the production
batch assessment. That completed assessment remains historical evidence for
the committed production batch; refresh the comparable baseline in the next
full assessment.

## Applied batch and validation history

The planner selected 1535 already-resolved test updates, and Scalafix applied
them across 298 test files. No production source was changed by this batch.
Whole-project production compilation passed. Test compilation found two
additional rewrite shapes requiring rule-level handling:

- `PlaceEditsSuite.scala:50` and `GoalsViewSuite.scala:34`: integer literals lose
  the original copy parameter's expected `Long` / `Option[Long]` type through
  the polymorphic optics call, producing a `Long | Int` union.
- `GoalsExportSuite.scala:233` and `RunsViewSuite.scala:141`: the optics macro
  rejects aliased enum types (`ScheduleKind`, `DraftMode`) with an assertion
  claiming a type is not a subtype of itself.

The test batch is uncommitted and not counted as verified fixes. Preserve the
frozen input/expected fixtures; add coverage for these newly observed shapes.
Fix numeric contextual typing in the reusable rewrite and explicitly defer any
unsupported optics type shape through a generated rollback/rewrite, without
hand-editing application test code. Compilation must complete before testing
and remeasurement can establish the actual reduction. These are rewrite
validation iterations, not a reopening of the bounded detector inventory.

### Subsequent validation

The numeric-context rewrite now preserves expected numeric types, including
Option[Long]. Exported-enum macro limitations and reserved optics field names
are explicitly deferred. Frozen Numeric, Aliased and Reserved fixtures cover
these shapes. Unsupported generated expressions were restored by a Scalafix
AST rollback, not hand edits. The applied report records 1,532 patches after
initial deferrals; three generated expressions were subsequently restored,
leaving 1,529 retained updates pending final inventory reconciliation.

Whole-project production and test compilation passed. The uncached full test
run has the same 4,509 test identities as the frozen pre-batch baseline: 13
failures/errors and 194 skipped. Comparison finds no new failing identities and
no newly skipped identities. Five existing failure messages changed; their
summaries show the same failures at shifted source line numbers (ArticlesSuite,
TargetSuite, AcquireRecheckDbSuite and PluginTreeSuite). This is not a claim
that all tests pass or that skipped database integration tests were exercised.
The baseline remains unchanged in test-outcomes/baseline.tsv; current results,
new-failure/new-skip tables and changed-message evidence are retained beside it.

Full assessment passed and confirms 58 resolved test copies remaining, down
from 1,587: exactly 1,529 retained automatic fixes across 298 test files.
Production remains 108 raw / 107 confirmed; unresolved copy owners remain
52 production and 97 test. The report's zero/no-regression booleans do not
establish identity-based architectural regression protection: this invocation
explicitly refreshed the aggregate baseline after preserving its predecessor.
Copy automation is now paused under execution-priorities.md. Remaining copies,
unsupported rewrite shapes and the failed independent recall sample stay visible.
