# Bounded transaction ownership source audit

An independent `gpt-6-luna` source audit was instructed not to read detector code
or reports. It selected 15 positive lines and 10 negative lines. Two positive
lines each contain both commit and rollback, so these represent 17 positive
occurrences, not 15 independent transaction designs. Positive cases cover
GazetteerRepo batch/prune/sweep, QueryRepo execution and VacuumRepo mode selection.
The negative sample spans eight source files and includes statement cleanup,
service-owned transactions and ordinary use of borrowed connections.

The auditor initially treated required read-only and VACUUM behavior as possible
exceptions. The explicit user policy was restated: the caller must choose the
required transaction capability, and no repository-ownership exception has been
approved. The auditor then classified those cases as positive. This is a recorded
policy clarification, not a label change to agree with detector results.

Coordinates were checked against source before freezing the configuration:
VacuumRepo statement cleanup is line 38, not the initially supplied 37. An
erroneous QueryRepo:38 row was retracted before freezing. The valid negative
sample has ten rows, despite the auditor initially reporting nine.

Parent source review also requested separate adjudication of QueryRepo's two
setReadOnly calls and its SET TRANSACTION READ ONLY statement. The auditor
confirmed all three as supplemental positives without consulting detector
reports. They are retained separately in transaction-sample.conf, rather than
replacing original examples or being silently omitted from recall reporting.

The frozen config asserts exact per-line diagnostic counts and exact source
snippets. Run it with the standard Scala 2.13.18 Scalafix CLI launcher:

```
--config refactor-audit/transaction-sample.conf --scala-version 3.10.0 --sourceroot . --auto-classpath --auto-classpath-roots target/out --files modules
```

Read transaction-sample/verification.txt together with the command exit status.
An expected count of one on an unsupported case must fail verification; do not
change it to zero or permit the owner. Detector refinement remains bounded.

This is a purposive sample with positives concentrated in three repository
owners. It does not establish recall for undiscovered repositories, dispatch,
callbacks, external helper bodies or dynamically constructed SQL. The original
sample and the supplemental discovery sample must be reported separately as
well as together. No application source changed during this audit.

## Measured result before refinement

The normal SemanticDB scan covered 2,357/2,357 production files and checked all
28 source examples. It exited **1**, with exactly three missing diagnostics:
QueryRepo lines 54, 59 and 77. No stale snippet or unchecked example was reported.

The original independent sample matches all 17 positive occurrences on 15 lines,
with no false positives on ten negative lines. The supplemental cases add three
false negatives. Combined occurrence-level precision is 100% and recall **85%**
(17 TP, 3 FN, 0 FP); line-level recall is 15/18 = 83.33%. Neither establishes
the 95% recall target. Preserve this failed result before improving the detector.

Next bounded refinement: recognize JDBC read-only mode changes and statically
known SQL transaction-control operations in repositories, with negative cases for
ordinary SQL and resource cleanup. Dynamic SQL remains unresolved; do not claim
complete SQL-effect inference. Adding these pre-existing findings requires an
explicit history-policy reconciliation, not an automatic baseline reset.
