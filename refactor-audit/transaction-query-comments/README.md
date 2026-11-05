# Transaction source audit and nested-comment repair

This checkpoint adds TX-0004 without changing the original TX-0001–TX-0003
baseline. It is a newly discovered existing guard defect, not an observed new code
regression. No detector refinement round was consumed; copy automation stays paused.

## Independent source review and correction

A cheap-model source-only audit reviewed repository borrowing and the Query/Vacuum
SQL paths without consulting detector implementations, configurations or reports.
The requested bound was fifteen production files and twenty-four examples; the
auditor also consulted API helpers, so this was not a strictly fifteen-file sample.
It used the supplied policy and AGENTS.md; refactor-goals.md was not found in this
worktree. This is a small purposive sample, not a representative random sample.

Twelve negative repository snippets reduce to ten distinct methods in six files:
EventRepo.put and LabelRepo.facets each appeared twice. FreezeSourceAudit.scala
freezes their source and AST, verifies each source hash against build-v7, and checks
the whole method owner (including nested owners) against the completed preceding
capability observation. All ten have zero repo-transaction/repo-factory diagnostics.
This is a comparison with a frozen observation, not a new project-wide scan.
There are no independent positive methods in this sample: precision and recall
remain undefined, and the 95% acceptance threshold has not been met.

The auditor initially judged Query's dynamic SQL safe because Query.check guards
execution and the service owns readOnlyRollback. Parent review supplied the
counterexample `/* outer /* inner */ outer */ COMMIT`. The auditor independently
confirmed the bypass and withdrew its earlier negative judgment. That correction
is retained here; the supplemental discovery is not retroactively included as an
independent positive in the holdout sample.

Vacuum's generated statement was reviewed as VACUUM with quoted identifiers and
job-owned autocommit. That source judgment grants no exception and does not resolve
the detector's general dynamic-SQL limitation. Both QueryRepo.exec and
VacuumRepo.vacuum remain unresolved in the preceding detector observation.

## TX-0004: nested comments hide transaction-control keywords

Query.statements already recognizes nested block comments. Query.leadingKeyword
stopped at the first closing delimiter, reading OUTER instead of COMMIT. The guard
therefore accepted the control statement before QueryRepo executed the supplied SQL.
PostgreSQL nests block comments and removes the complete comment before syntax
analysis ([lexical syntax](https://www.postgresql.org/docs/current/sql-syntax-lexical.html#SQL-SYNTAX-COMMENTS)).

QueryLeadingComments applies an exact-file/hash-guarded Scalafix patch to the parsed
method, replacing the comment skipper with a depth-tracking loop. It also adds
three regression tests through a Scalafix patch. This is a targeted repair, not a
general automatic SQL-policy inference rule. No application files were hand-edited.

The frozen tests-before run compiled successfully: the original 26 tests passed
and all three new tests failed. CompileBatch's generic COMPILE_FAILED status refers
to the failed command, not a Scala compilation error. The tests-after run passes
all 29 with no failures or skips. The control test checks thirteen prohibited
commands against three nested-comment prefixes; other tests check allowed queries,
mixed line/block comments, and unterminated comments. Both runs retain source hashes
and complete logs. The repair's original AST and replacement are retained too.

Verification proves this lexical guard defect is fixed. It does not execute a
transaction-control statement against a database or prove arbitrary SQL safe.
The subsequent build/ observation passes full `compile` and `Test/compile`, with
all 3,143 source hashes stable throughout both commands.
After validation, TrimTestTrailingLine removes one extra trailing newline token
introduced by test insertion, proving the Scala AST unchanged and retaining both
hashes in whitespace.txt. Its first invocation omitted the Scala 3 parser option
and failed before applying a patch; the corrected invocation uses Scala 3.10.0.
Dynamic execution, procedural effects and dispatch remain unresolved detector
coverage. No database, HTTP, native-image or full runtime-suite validation is
claimed by this checkpoint.
