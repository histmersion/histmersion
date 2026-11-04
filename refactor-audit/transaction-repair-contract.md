# Transaction ownership repair contract

The next repair batch covers all three reviewed owner problems, TX-0001 through
TX-0003. Detection/history corrections are not repairs. Repositories must not
receive a transaction callback or Transactor and choose when to invoke it: that
would preserve ownership under a different API.

## Shared capability

Extend the database transaction utility with explicit operations for ordinary
commit-on-success work, rollback-only work, read-only rollback-only work with a
statement timeout, and autocommit work. Provide a scoped session for several
operations on one physical connection. Services choose these operations;
repository bodies receive only DbCon. Keep direct style and using parameters.

The utility owns setup, transaction outcome, restoration and connection lifetime.
Specify behavior on setup/body/commit/rollback/restoration failures in executable
fixtures before applying the rewrite. Do not mask a body failure with cleanup
failure or silently commit a nested borrowed transaction. Avoid exposing raw
connection-mode setters as the service interface.

## Definition and caller changes together

| Finding | Repository after rewrite | Caller responsibility | Required preservation |
|---|---|---|---|
| TX-0001 QueryRepo | Statement execution and result projection on DbCon; no setAutoCommit, setReadOnly, rollback or transaction-control SQL | QueryService selects read-only rollback-only work on the dedicated query pool; test adapter SearchFixtures selects the same capability | SQL rejection/error values, dedicated role, timeout and row/cell caps, rollback on success/failure, statement cleanup |
| TX-0002 GazetteerRepo.Session | Database operations for writing a prepared batch, recording seen refs, obtaining prune candidates, deleting a prune chunk, and sweeping rows | Feature import orchestration owns transaction choices and sequencing on one scoped connection | Batch deduplication/skipped-count behavior; seen refs persist after a dry-run batch rollback; pruning commits by chunk; sweep only after complete input; temporary gaz_seen stays on the same connection |
| TX-0003 VacuumRepo | Validated VACUUM statement and statement cleanup on DbCon | VacuumJobs selects autocommit capability | VACUUM outside a transaction, before/after statistics, table-name validation, cleanup on failure |

Current GazetteerLoad.run calls prune, batch and sweep inside one db.read. Moving
that entire block into one transaction would be wrong. Moving all of Session into
a service would also move its SQL into the service. Split the orchestration from
the database operations instead. Do not duplicate the orchestration in test helpers:
tests exercising the workflow must reach the same implementation; move such tests
to the server module through Scalafix if their module cannot depend on that service.

## Rewrite and verification

Use one reviewed AST/symbol plan for utility generation, method-body extraction,
caller changes and necessary test-file moves. Match the actual source shapes and
reject stale manifests before patching. Expected fixtures must show borrowed-DbCon
repository bodies and service-owned transaction selection, not just removed names.

Compile utility, repositories, services and test callers together. Run connection
lifecycle fixtures with a recording JDBC implementation, then real test-database
query, Gazetteer dry-run/prune/sweep and maintenance checks. Skipped database tests
are incomplete validation, not a passing repair. Remeasure the whole project and
retain both removed diagnostic identities and verified boundary outcomes.

The broader primitive/newtype and capability migrations remain separate work.
Preserve existing signatures where the ownership repair does not require changing
them; do not use this batch to rename or weaken model types, add repository
permissions, or reopen copy automation.
