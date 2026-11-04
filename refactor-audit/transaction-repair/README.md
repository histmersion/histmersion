# Verified transaction ownership batch

TX-0001–TX-0003 are verified fixed by one targeted Scalafix batch. This is three
reviewed ownership boundaries and 22 removed detector occurrences, not 22 separate
design problems. The historical denominator and its additive detector correction
remain unchanged. This does not establish 90% automation across the migration.

QueryService selects read-only rollback work; QueryRepo only executes and projects
SQL. GazetteerImport selects batch/prune/sweep transactions while GazetteerRepo
borrows DbCon for SQL. VacuumJobs selects autocommit while VacuumRepo performs
validated maintenance statements. Transactor supplies the shared lifecycle handling.
Definitions, callers and test adapters changed together. Two workflow suites moved
from db to server so they exercise the actual feature orchestration.

## Verification

- Frozen 12-file preview compiled; application matched its hashes and ASTs.
- Final production and test compilation passed across the project, with SemanticDB.
- The same 62 test identities passed before and after against an isolated PostgreSQL
  `histmersion_test` database: no failures, skips, or lost identities. The test server
  was stopped afterward. See `database/baseline.tsv`, `after.tsv` and their summaries.
- Recording JDBC fixtures passed for commit, rollback-only and read-only work,
  timeout setup, body/commit/setup/restoration failures, suppressed cleanup failures,
  closing after rollback failure, refusing nested transactions, and autocommit.
- Two subsequent comment/whitespace-only Scalafix corrections preserve ASTs and
  record before/after hashes under `trivia/`; final compilation passed again.
- Project-wide semantic measurement covers 2,358/2,358 production files. Repository
  transaction and factory findings are zero. All 209 other occurrences retain their
  prior stable IDs and multiplicities. No new supported capability identity appeared.
- Positive/negative transaction fixtures still pass, including enforcement exit 1
  on forbidden direct/helper calls and three explicitly unresolved SQL shapes.
- The final integrated assessment passes: 3,128 source/test files covered, all 31
  capability source assertions checked, and the identity gate reports 209 occurrences
  in 175 anchors, zero new/reopened identities and zero multiplicity changes. Its
  generic absence state is supplemented by this batch's verified repair evidence.
  The aggregate baseline is preserved and marked policy-incomparable; this is not
  a claim that an aggregate no-increase gate ran against a replacement baseline.

`plan.tsv`, `resolved-identities.tsv` and `identity-verification.txt` connect the
patch and its tests to the frozen findings. Duplicate anchors remain represented by
their original multiplicity; no claim of distinct per-occurrence identities is made.

## Limits and policy correction

Two production SQL expressions remain unresolved: dynamic QueryRepo execution and
generated VacuumRepo SQL. Dynamic dispatch, callbacks, external helper bodies and
inferred capability escapes remain detection limits. Zero direct findings is not
complete architectural proof. Project-wide test capability detection is not yet
established; 62 passing behavioral tests are not a test-policy inventory. Native
images and the entire behavioral test suite were not run for this batch.

The repaired QueryRepo source assertion changes from a positive to a negative guard.
`capability-history/corrections/transaction-repair/` preserves both complete assertion
files and predecessor/current contracts. Its recording rule verifies that this is
the only policy change; positive helper-mediated fixtures remain active. No original
baseline, adoption, or historical assertion evidence is overwritten.

Copy automation remains paused. Capability access, module isolation, and
primitive/newtype classification remain independent deliverables. These targeted
repairs do not turn their candidates or unresolved evidence into compliant code.
The fresh model inventory has 30,322 primitive leaves in production member positions,
up nine from 30,313 after the signature split; these remain candidates requiring
semantic classification. Across all scopes, 46,650 leaves remain unresolved. This
batch claims no primitive/newtype fixes and no test-policy regression coverage.
