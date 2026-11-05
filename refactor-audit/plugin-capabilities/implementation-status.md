# Applied plugin capability batch: validated

The Scalafix preview has been applied across 27 application files. LocalFiles and
Processes live in plugin-api; DiskLocalFiles and SystemProcesses live in plugin-host.
Context forwards the injected capabilities, and composition supplies the real adapters.
Calibre and vault consumers receive capabilities with using parameters. Unavailable
defaults refuse operations. Vault tests depend on plugin-host only in Test, through
an AST-located build.sbt rewrite; production dependencies remain unchanged.

Full project compile and Test/compile passed after the initial application. The frozen
plan and applied-verification describe that initial snapshot. Two subsequent Scalafix
corrections are explicit: PluginContextCapabilities wires an anonymous Calibre test
context missed by the first matcher; PluginCaptureFailure preserves failure-to-Left
handling across the entire unbounded capture operation. The preview rule and adapter
generator include these corrections for future replay from their original inputs.
Do not rerun the original preview generator on already migrated sources.

The first Calibre after-run failed one test due to the missing anonymous context wiring.
Its reports remain under calibre-tests/failed-attempt-1.*; assertions were unchanged.
The corrected run passes all 45 tests with the same identities. Vaults retain 28 passes
and the same installed-KeePassXC opt-in skip. The verifier records its exact identity
and reason; no additional skips or failures are accepted.

Standalone adapter contracts passed against both generated and applied sources:
file staging cleanup on success, returned failure and thrown exception; filename
confinement; bounded reads; refusing defaults; separate process streams; stdin and
environment; timeout; interactive session closure. Host tests pass 41/41 and the
testkit public API snapshot check passes 1/1.

The plugin API test run is not green: 42 passed and three ValuesSuite tests failed.
Coordinates, DOI and Wikidata tests expect validation from their existing Pure
plugin types. Those type definitions and test assertions are unchanged by this batch.
No pre-batch execution of that suite was captured, so the recorded evidence is the
current failures plus unchanged source, not a claimed before/after passing result.
This inconsistency remains outstanding model/test work; do not weaken assertions
or reintroduce client-side constraints merely to make the suite green.

The production capability scan covers 2,366 files, none missing, with 155 findings
versus 180 previously. Identity reconciliation proves exactly 23 reviewed anchors /
25 occurrences removed, with one three-occurrence clock anchor relocated from vault
Subprocess.Session.await to host SystemProcesses.Session.await. All other identities
and multiplicities remain unchanged. The clock anchor is still visible and is not
counted as a verified repair. The pure path-separator constant remains recorded as
a non-violation. Two dynamic transaction SQL expressions remain unresolved.

The final integrated assessment rebuilt all modules and passed all inventory/fixture
checks and the capability history gate. It covers 2,366 production files with none
missing: 152 raw findings / 130 anchors, zero new or reopened identities, and zero
multiplicity changes. The three clock occurrences have an explicit accepted-exception
record and a permission limited to SystemProcesses.Session.await. They are not repairs.
The pre-permission 155-finding observation and identity reconciliation remain preserved.

The policy correction chain preserves the original baseline and all three successive
corrections. All 31 source assertions remain. The repaired Files/ProcessBuilder guards
now assert supplied-capability use, while positive fixtures remain. final-plan.tsv and
final-source-verification.txt account for every applied source and the post-preview
corrections, including token-only whitespace cleanup. verified-findings.tsv attaches
the compilation, behavioral and identity evidence to the 23 repaired anchors.

Across the original capability review, 44 of 48 confirmed anchors (54 of 58 occurrences)
are repaired. Four confirmed occurrences remain: SourceNote reads (3) and Upload metadata
(1), neither accepted nor deferred. Two separate confirmed resource-loading gaps were
also repaired by the earlier agent-storage batch. The current raw residual comprises
four confirmed occurrences, 146 candidates and two adjudicated non-violations. Test
policy coverage and independent sampled precision/recall remain unestablished.

Other policy families are not counted as improved by this batch. The model inventory
now reports 30,430 production primitive leaves in 28,242 member positions, increases
of 83 and 75 respectively. Unresolved leaves across scopes increased by 78 to 46,865.
These are candidates/unresolved evidence, not an approved violation denominator.
Transaction ownership has zero supported remaining findings and two unresolved dynamic
SQL expressions. Module isolation remains bounded by its configured build/source
checks. Copy automation remains paused. The overall migration goal remains open.

Whitespace checking still reports nine indented empty lines in the generated
SystemProcesses adapter and empty trailing TSV fields in preserved test reports.
No clean whitespace-check result is claimed. These cosmetic differences are retained
in the exact source/report hashes; they do not change the verified AST or test results.
