# Transaction detector refinement 1 of 2

This is detector progress, not a transaction repair. Application sources and the
original history baseline remain unchanged. The failed original source sample
is preserved in transaction-sample/ and version control.

The rule now recognizes JDBC Connection.setReadOnly and configured JDBC methods
whose first argument is a direct string literal beginning with a transaction
control command. Matching uses resolved JDBC symbols and the AST literal; an
unrelated execute method or SELECT containing the word COMMIT is not a hit.
Known helper bodies propagate these effects to repository callers as before.

The configurable SQL pattern covers BEGIN, START TRANSACTION, COMMIT/END,
ROLLBACK/ABORT, SAVEPOINT, RELEASE SAVEPOINT, SET TRANSACTION and SET SESSION
CHARACTERISTICS AS TRANSACTION. It is deliberately not a general SQL parser.
Nonliteral, comment-prefixed and compound SQL is recorded as unresolved review
evidence. It is not classified as compliant. Existing direct-symbol checks and
exact-owner permissions remain in force; no permission was broadened.

## Verification

- All 28 frozen source assertions pass over 2,357/2,357 production files.
  Original and supplemental positives now yield 20 expected occurrences, with
  no false positives on the ten negative lines. Because these cases drove the
  refinement, this is development-regression evidence, not fresh independent
  acceptance of 95% precision/recall for the whole family.
- The new compiled fixture has five direct and one helper-mediated diagnostics,
  ordinary SQL, getters, statement cleanup, legitimate service ownership and
  unrelated execute lookalikes. Exact diagnostic verification passes. Normal
  enforcement rejects it with exit 1, including the helper-mediated violation.
  Three unsupported SQL expressions appear in its unresolved evidence.
- The existing six semantic fixture files pass unchanged.
- Project-wide diagnostics rise from 228 to 231; transaction diagnostics rise
  from 19 to 22. Other configured capability counts are unchanged.
- TransactionRoundVerification proves all 228 prior occurrence multiplicities
  and anchor IDs remain present, application sources match the prior assessment,
  and the only additions are QueryRepo lines 54, 59 and 77. These three
  pre-existing confirmed violations belong to TX-0001 and two new diagnostic
  anchors. The two setReadOnly calls share an ambiguous anchor; no distinct
  per-occurrence identity is claimed.

The three production SQL expressions requiring further review are QueryRepo's
interpolated statement timeout, QueryRepo's user SQL argument, and VacuumRepo's
generated VACUUM statement. This is an evidence count, not three additional
confirmed violations. Missing-symbol collection in ordinary mode remains a
separate limitation; the unresolved counter now includes these SQL expressions.

## History and next action

transaction-round1/reconciliation-additions.tsv assigns the new anchors to
confirmed/manual-design/TX-0001. reconciliation.txt records the additive proof.
The original 228-occurrence baseline and detector contract were not replaced.
The history gate correctly rejects the changed detector contract pending an
explicit versioned policy reconciliation. Do not refresh the baseline to make
it pass. No claim is made that the integrated assessment passes under this new
policy yet; only the targeted checks above have run.

One detector refinement round remains. Do not use it for open-ended SQL inference.
Adopt the documented additive history correction, then advance transaction repair
and useful coverage of the other priority families. Unknown SQL/dispatch must not
block that work or become an accepted transaction-ownership exception.
