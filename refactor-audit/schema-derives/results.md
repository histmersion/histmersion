# Schema derivation batch

Starting commit: `6015e9aa`. Scope: every production/test Scala source in `modules/`, counted once across JVM/JS rows.

| Measurement | Before | After |
|---|---:|---:|
| Plain semantic Kindlings-to-Tapir bridges | 523 | 0 |
| Eligible for automatic rewrite | 523 | 0 |
| Unsupported matches in this family | 0 | 0 |
| Source/test files scanned | 3126 | 3127 |

All 523 bridge definitions were rewritten by one semantic rule in one project-wide application. They span 280 existing source files in api, core and server; 521 are production and two are tests. The additional source is the shared schema adapter. No application Scala source was edited by hand. Explicit given consumers are rewritten by symbol; none occurred in this project inventory, while fixtures exercise named, anonymous and aliased references.

The detector covers this specific bridge shape. Customized schemas, strict JSON codecs, database codecs and other derivations remain separate inventory items. The broad initial syntax inventory reported 521 candidates; resolved semantic matching found 523. Completion is measured against the latter.

## Verification

- Full `compile` and `Test/compile` pass with formatting disabled and ordinary SemanticDB enabled, including Scala.js rows.
- The repeatable Scalafix fixture runner passes exact-output comparison, compilation and execution, negative verification before rewriting, positive verification afterwards, and a second application without changes.
- The adapter tests preserve every schema constructor field and cover nested records without their own declared schema. A plain alias is deliberately unsuitable: Scala follows Tapir's companion for derivation, bypassing Kindlings. The concrete subtype delegates to the existing Kindlings instance.
- WireSnapshotSuite passes. All 39 checks in ContractSnapshotSuite, AgentSchemaSuite, AnswerSchemasSuite and AiProvidersSuite pass. ContractsSuite passes all four checks after its snapshot is regenerated, with regeneration disabled for the final run.
- API tests: 780 pass, three fail. A source archive of `6015e9aa` under `target/schema-baseline` reproduces the same three failures: ArticlesSuite slug normalization and invalid-slug rejection, and TargetSuite coordinate rejection. These predate this batch and remain outstanding; the API test suite is not fully green.
- `assessment-schema.conf` passes the project-wide zero gate. It also runs the other developed detectors and fixtures. Capability findings remain 228; structural findings remain 42. The prior assessment baseline was retained; the expanded rule/configuration fingerprint is explicitly incomparable.

## Ledger reconciliation

The checked-in contracts ledger was already stale before this batch. It was regenerated with ContractsSuite's supported update mode. The same scanner was run against the exported starting commit's sources using a separate test working directory. Comparing those two fresh ledgers shows changes only in the derives family; other families are identical. The larger diff against the old checked-in ledger also includes earlier moves and fixes and must not all be attributed to this batch.

The current historical scanner records 505 remaining derives occurrences and 2204 occurrences across all families. Those are heuristic exceptions, not a fresh semantic migration denominator. This batch establishes 523 automatic fixes; it does not establish completion of 90% of the overall migration.

## Next batches

Reconcile the remaining derivation inventory into strict JSON bridges, customized schemas, database codecs and legitimate instances before choosing the next bulk rewrite. Inventory copy operations by resolved receiver and AST shape to distinguish field updates from allocating folds. Preserve the same definition-and-consumer fixture checks, project-wide measurement, and explicit unsupported-case accounting for each batch.
