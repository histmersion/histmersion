# Transaction ownership batch

Status: this planning record is preserved. TX-0001–TX-0003 were subsequently
repaired and verified together; see `transaction-repair/README.md` and the
append-only `transaction-history.tsv`. The following describes the pre-repair state.

Source review confirms three different ownership problems behind the current
19 transaction diagnostics. Diagnostics include a transitive call witness and
several operations within one transaction; they are not 19 independent
transaction boundaries. This review does not establish sampled recall for the
whole detector family.

| Owner | Required outcome | Behavior that must survive |
|---|---|---|
| `QueryRepo.exec` / `QueryService.run` | The service selects a supplied read-only, rollback-only transaction capability; the repository executes SQL using its connection. | Dedicated query-role pool, statement validation, read-only transaction, local timeout, row/cell caps, rollback on both success and failure, connection cleanup, SQL errors returned as values. |
| `GazetteerRepo.Session` / `GazetteerLoad` | Import orchestration owns each batch/prune/sweep transaction; repository operations borrow a connection. | One physical connection for the temporary `gaz_seen` table; dry-run batches and sweep roll back; seen refs survive a dry-run batch rollback; pruning commits by chunk; sweep runs only after the complete source was read. |
| `VacuumRepo.vacuum` / `VacuumJobs` | The caller selects an autocommit connection capability; the repository does not change connection mode. | VACUUM runs outside a transaction, validated table names remain enforced, statements close on failure. |

Implement reusable transaction primitives in `Transactor` before moving the
orchestration through Scalafix. Replacing every caller with ordinary `transact`
would change behavior: it cannot represent rollback-only reads, dry-run imports,
or VACUUM. Renaming repositories or allowing these symbols in the linter is not
a repair.

The rewrite must update the production callers and the database test adapters
together. Known direct test callers include `SearchFixtures`,
`GazetteerRepoSuite`, and `ImportedPlaceTimeSuite`. Existing database tests cover
query write refusal, dry-run/import behavior and pruning; they must execute
against a test database rather than merely compile or report skipped tests.
Add transaction-primitive failure tests covering commit/rollback choice and
restoration, and keep positive/negative detector examples for both service
ownership and forbidden repository ownership.

After the batch, remeasure every module, inspect both direct and transitive
transaction findings, and distinguish removed duplicate witnesses from repaired
boundaries. Unknown dispatch and callbacks remain unresolved evidence, not proof
that the code is safe.
