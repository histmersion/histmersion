# Record-update migration

The production baseline is 441 resolved case-class copy line-expression groups, measured
after the first detector refinement. The first automatic batch selected 198
occurrences across 86 files in eight modules. The completed whole-project
assessment measures 244 residual groups: 197 fewer, or 44.7% of this family's
baseline. This is one policy family, not an overall migration percentage.

`reconciliation.txt` and `same-line-duplicates.tsv` explain the difference:
ReviewArticle had two identical calls at different offsets on the same line.
Both were rewritten, but the detector's existing deduplication counted them as
one finding. No remaining call is eligible for the current single-field rule.
The detector needs offset-based occurrence identity before its counts can be
called exact code-occurrence counts; preserve this original baseline when that
changes. The same issue may affect the pending test-count reconciliation.

Whole-project production and test compilation passed. The copy fixtures passed
expected-AST, compiled-behavior, evaluation-order, negative/positive check and
idempotence assertions. The full assessment completed after two source-example
line coordinates were updated for added imports; labels and counts did not
change. The core, files and plugin-host test suites passed earlier in the batch.
Native-image and database integration tests were not run for this batch.

## Evidence and scope

- `baseline.tsv` preserves the pre-rewrite denominator and unresolved evidence.
- `acceptance/quality.txt` and `acceptance/holdout-results.tsv` record the
  independent acceptance sample: 20 true positives, three true negatives, no
  false positives or false negatives. It is not a random sample and has limited
  negative diversity.
- The earlier holdout became a development regression after it exposed missing
  method-result receiver resolution. One of two detector-refinement rounds has
  been used.
- Holdout source guards describe the pre-rewrite sources. Do not change their
  labels or rerun them against rewritten sources as if they were fresh samples.
- `applied/counts.tsv` records rewrite eligibility, not the post-rewrite count.
- `verification.txt`, when produced by the assessment, records the rewrite's
  compiled behavior, evaluation-order, expected-AST and idempotence checks.
- `CopyBatchReconciliation.scala` was added after that assessment to explain the
  count difference. It changes the conservative tooling fingerprint, so the
  next full assessment must explicitly refresh the comparable policy baseline.

## Remaining work

The following categories come from the pre-rewrite planner. They are separate
from the confirmed detector denominator and must not be added to it.

| Planner category | Occurrences | Next action |
|---|---:|---|
| Multiple, positional or empty arguments | 141 | Separate argument shapes; implement a reusable rewrite preserving receiver and argument evaluation order. |
| Accumulator algorithms | 69 | Replace allocating accumulator updates with an appropriate loop; a lens alone does not satisfy that policy. |
| Modules needing optics dependency review | 20 | Check allowed dependencies and choose an approved implementation. |
| Generic record updates | 13 | Establish whether the update changes the type parameter before choosing a type-preserving lens. |
| Test code | 1587 | Classify deliberate copy fixtures separately from ordinary model updates; tests are not exempt. |
| Unresolved or non-case-class owners | 152 | Keep unresolved evidence separate from legitimate methods; do not infer a violation from the name `copy`. |

The operation detector reports 1585 resolved test occurrences, while the planner
reports 1587 test-deferred calls. Reconcile their occurrence identities before
using test counts as a confirmed migration denominator.

The project assessment measures production and test copies in every source
module. Its count regression gate does not yet prevent exchanging an old
violation for a new one at another site; an identity-based debt check remains
required by the migration goal.

## Second batch and final detector refinement

The second detector refinement adds source offsets to occurrence identity.
All 19 original fixture classifications remain unchanged, and two identical
copies on one line must now produce two distinct findings. The whole-project
operation scan still finds 244 production copies and now finds 1587 test copies,
matching the rewrite planner. The reconciled pre-first-batch occurrence baseline
is 442 (198 rewritten plus 244 remaining); the original 441-group baseline above
is preserved. Both allowed detector-refinement rounds are now used.

The multi-field rewrite has 136 eligible calls. It preserves named-argument
source order through chained lenses. It requires immutable constructor fields,
no explicit parents, and no executable constructor statements. Constructors
with observable work are deferred because multiple lens updates would execute
that work more than once. Generic updates, folds, inaccessible fields, custom
copy/modify methods and comments in argument separators remain deferred too.
New frozen fixtures cover reversed named arguments, nested rewrites, exactly-once
receiver evaluation, constructor effects, mutable fields and inherited records.
Both the original and multi-field fixture suites passed before application.
Whole-project production and test compilation passed after the generated-token
handling was corrected for parenthesized arguments and match-expression commas.
Both shapes now have frozen cross-file fixtures, checked against expected ASTs,
compiled output and idempotence. Core (709), files (59) and plugin-host (39)
tests passed. The full assessment then passed and measured 108 production
copies, exactly 136 fewer than the second batch's 244-occurrence baseline.
The two batches removed 334 of the reconciled 442 production occurrences
(75.6% of this family). Test copies remain separately counted at 1587; they
are not included in that production percentage or silently exempted.
