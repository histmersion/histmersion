# Violation repair plan

Starting checkpoint: `528bae71` on `contracts3-scalafix`. Production and aggregate test compilation pass. Ordinary SemanticDB covers 2,355/2,355 main files. Current policy reports 432 capability references and 50 structural findings; these are not 482 independent operations or a complete inventory of all 43 migration goals.

Application and application-test edits remain Scalafix-only. Use semantic symbols for declaration/reference changes; use AST/token patches for syntax. No raw-text migration scripts. Planning does not authorize replacing CLI output with logging or relaxing domain validation.

## Ordered batches

| Batch | Work and evidence | Acceptance |
|---|---|---|
| 1. Controller names | Rename `AgentsHandlers` and `ManagersHandlers`, their companions, imports, constructor/type references and filenames through a semantic Scalafix rule. The four findings represent two class/companion pairs. Preflight destination collisions and inspect aliases, named imports and references in tests. | Four Handlers findings disappear; all references resolve; no wire endpoint/tool names change; aggregate compilation and relevant controller/contract tests pass. |
| 2. CLI output boundary | Inventory the 204 console references by actual symbol and AST shape. Introduce narrow output methods at the existing permitted `cli.Cli` edge through Scalafix, then redirect supported CLI calls, including `Output.emit`. Keep stdout, stderr, newline, formatting and flush behavior distinct. Server diagnostics use scribe with an explicitly chosen level. | Golden captured-output tests remain byte-equivalent; JSON output stays valid; callbacks, interpolation and side effects evaluate once; unsupported shapes remain diagnosed. Only the supported subset's findings should disappear. |
| 3. Repository visibility | For the 12 exposed feature repositories, index all references first. Add feature-private visibility only when every consumer can still access it. For external consumers, identify the owning service method before introducing a delegation and updating consumers. | No public repository access remains in the completed subset; no broad `private[domain]` escape; compile application and tests. Cross-feature consumers are reported, never silently broken in this safe batch. |
| 4. Clocks and startup globals | Classify 103 clock, 8 environment, 12 property and 11 lifecycle findings. Use supplied `java.time.Clock` for wall time; preserve monotonic elapsed-time semantics with a separate ticker abstraction. Thread capabilities through constructors/factories and composition roots. Reuse `server.SystemContext` and assess `config.Environment` rather than inventing duplicate bootstrap readers. | Fixed-clock/ticker tests, unchanged time units/zones and exit behavior, complete caller rewrites; raw globals confined to explicit infrastructure owners. Property mutation, shutdown hooks and process exit require individual behavioral review. |
| 5. Files, HTTP and processes | Inspect 67 filesystem, 1 HTTP and 7 process findings by owner. Reuse/adapt supplied capabilities such as acquisition `Http`, ingest `ToolFiles`, `ServerToolFiles`, `UpstreamHttp` and plugin SPI facilities. Separate plugin-testkit infrastructure from plugin implementation policy. | Domain/plugins depend on supplied interfaces; adapter implementations stay in permitted layers; resource closure, cancellation, charset, path behavior, retries and errors have contract tests. No package-wide exemptions. |
| 6. Typed service contracts | Start with the 34 configured primitive-role findings in `GoalService`, `SourceService` and `AssetService`. Map exact parameter symbols to `Goal.Slug` or `Citekey`, then enumerate overrides, InMemory implementations, repositories, controllers and all call sites. Parse wire values once at the boundary; propagate typed values internally. | Positive and rejecting inputs retain behavior; no `assume`/unchecked constructor shortcuts; real and InMemory contracts agree. Type definition, implementations and callers change together whenever mechanically provable. |
| 7. Transaction ownership | Treat the 19 findings as three behavioral migrations, not a replace-all: `QueryRepo`, `GazetteerRepo`, `VacuumRepo`. Move connection/transaction policy to the service/infrastructure owner; repositories borrow `DbCon`. | Lifecycle findings and indirect witness disappear only after behavior tests pass. Live database integration tests are required before declaring this batch complete. |

Batch 1 is complete: 137 semantic patches across 48 files, both files moved, all four targeted findings removed, compilation and 25 contract/agent tests passing. The contract run also exposed and repaired an API/domain role comparison; snapshot expectations were unchanged.

Batch 2's stdout pass is complete: 163 `Predef.print`/`println` references in 23 files route through the existing permitted Cli boundary. All 233 CLI tests pass, including byte/evaluation regression coverage. Capability findings are 269, including 41 remaining console findings; structural findings remain 46. Next: classify remaining stderr/progress calls and non-CLI diagnostics, preserving stream selection and flush behavior. Each batch is independently reviewable and committed after verification.

## Transaction designs that must be preserved

- `QueryRepo.exec` establishes read-only mode and timeout, always rolls back, and restores connection state. The service must retain the restricted database role and SQL validation. Extend the database capability with an explicit read-only/rollback operation if needed; ordinary `Transactor.read` is not an equivalent replacement. Test forbidden writes, timeout, errors and pooled-connection state restoration.
- `GazetteerRepo.session` commits or rolls back batches, prunes in batches and supports dry-run counts. Extract orchestration without turning a bounded batch operation into one giant transaction. Test successful batches, mid-batch failure, dry run and pruning/sweep behavior.
- `VacuumRepo.vacuum` needs autocommit outside a transaction. The infrastructure owner must supply that execution mode; moving it into `transact` would be incorrect. Test both analyze modes and restoration/closure on failure.

## Rule and configuration contract

Keep policy choices in checked configuration: exact source/target symbols, ownership mappings, parameter roles, approved adapter owners and allowed capabilities with reasons. Hardcode AST mechanics and semantic invariants, not a growing list of filename substitutions. Existing snapshot repair recipes are recovery tooling, not the model for the next migration rules.

Before each rewrite, produce a Scalafix shape inventory containing symbol, owner, source/AST, references, intended action (`rewrite`, `lint`, `unchanged`, `decision`) and refusal reason. Resolve configured symbols against the index; reject unknown owners, ambiguous targets, duplicate mappings and stale permissions. A type belongs on another companion only when an explicit ownership mapping or reviewed model evidence says so; name prefixes alone are suggestions.

For every supported shape, retain an input/expected-output fixture plus lookalikes that must remain untouched. Cover aliased imports, overloaded methods, callbacks, nested owners, cross-file references and Scala 3 syntax where applicable. Compile expected outputs; assert exact diagnostics; deliberately introduce missing/unexpected diagnostics and missing coverage to prove failure. Apply twice and require zero second-pass changes. Fail before writing if an entire cross-file rewrite cannot be planned safely; per-file atomic patches alone are not a cross-file transaction.

## Measurement after every batch

1. Freeze the starting report and policy identity. Record the exact finding identities targeted, not just a promised count.
2. Run fixtures, apply Scalafix, compile production with ordinary SemanticDB and run aggregate test compilation plus affected behavioral suites. Run database tests for transaction changes.
3. Rerun capability and structural audits with complete source coverage. Report removed, new and remaining findings, alongside unsupported shapes and permission changes. A new wrapper must be checked for allowed ownership and hidden effects; disappearance of a direct match alone is insufficient.
4. Update audited examples deliberately when a known violation becomes a verified clean case. Preserve an equivalent violation fixture so the detector's sensitivity is retained.
5. Commit the batch with its evidence. Reject regressions in completed families while allowing the recorded backlog; never treat the current intentionally failing global readiness gate as a successful check.

## Deliberately non-compiling phase

Use only after safe batches, for an explicitly enumerated type/ownership migration. Save a compiling checkpoint, export its semantic reference graph, and have Scalafix generate the final signatures across the entire affected scope before resolving compiler errors. Record the expected error set and repair implementations/call sites through further rules. Do not use partial/error metadata to claim the audit is complete. The phase ends only with normal compilation, behavioral checks and full remeasurement; an intermediate red build is not a completed batch.

## Remaining detector work

The readiness register still has 42 partial/unmeasured goals. Extend coverage alongside the relevant repair batch: alias/inferred-type checks for primitive contracts, companion ownership and one-type rules for model moves, helper/callback effects for capabilities, and dependency closures for module boundaries. Review configuration loading's zero count and repository factory's zero count against these limitations; zero currently means no matches under this policy, not proven absence of every possible violation.
