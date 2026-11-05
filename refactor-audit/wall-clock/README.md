# Wall-clock batch: verified bounded repair

The batch now compiles with its callers. build-2 passes full production and test
compilation; tests-2 compiles the added suite and passes 21 tests without skips:
RunTokensClockSuite (4), CoreQuotaLendSuite (3), CoreBudgetSuite (7), and
HttpChallengeSuite (7). The new tests cover fixed-clock default issuance and
authentication, explicit-time overrides, expiration/revocation, independent clocks,
and the requirement to supply a Clock. The first test run failed because its
contextual-only constructor calls included an extra empty argument list; the
Scalafix correction and original source are preserved. Assertions were unchanged.

measurement-2 scans all 2,373 production files with fresh SemanticDB and passes all
31 source assertions. Capability occurrences fall from 147 to 118. Identity
reconciliation proves the exact 29 reviewed occurrences in 28 anchors are absent,
with no new identities or changes to residual multiplicities. All 13 reviewed
monotonic/polling occurrences remain confirmed outstanding. No exception was added.
The original observation and treatment decisions remain unchanged; repair events
and a 42-occurrence status ledger are under reconciliation-1.

The RunTokens source assertion now expects zero for the injected-clock default.
Its old positive and the new negative assertion, policy contracts and reason are
retained under capability-history/corrections/wall-clock. No ban or permission was
relaxed. The unchanged clock-fixtures.scala compiled and passed its exact diagnostic
checks, retaining global-clock positives and the supplied-clock negative.

This is a bounded capability measurement, not a fresh full multi-family assessment
or its history gate. Independent family precision/recall, test capability policy,
database-backed workflow runtime and the overall 90% target remain unproven. Tests
that previously used implicit real time now supply that real-time callback explicitly;
this batch does not claim test-policy cleanup. Seventeen existing tests pass, but
no before/after runtime comparison was run for them in this batch.

## Implementation and retained failed checkpoint

The complete reviewed batch is 29 production occurrences in ten files. The
hash-guarded Scalafix preview rewrites global reads to supplied clocks, adds
contextual Clock dependencies to ModelPicker and RunTokens, and removes global
callback defaults from CandidateSearch, CoreBudget and SttpHttp.apply.
ApplyFrozenPreview applied all ten frozen outputs. No manual application edits.

At the initial checkpoint the migration was incomplete. Full compile stopped at
AgentsControllers constructing ModelPicker without the new contextual Clock.
Test compilation did not execute. build-1 retains the failed command, source
hashes and log. The first preview generation also rejected an incorrect expected
SemanticDB spelling (`Instant.now` instead of `Instant#now`); the symbol assertion
was corrected from the frozen evidence before generating any output.

## Caller propagation requirements

These requirements were completed in callers-1 (30 changed files). Composition's
optional budget is now an Option, constructed from system.clock only when omitted.
Explicit budgets remain supplied as Some, and Main shares one startup clock between
its budget and SystemContext. Domain factories no longer construct default budgets.

- AgentsControllers constructs ModelPicker: explicitly pass its existing clock.
- CliRuntime already receives Clock contextually and constructs RunTokens.
  AuthHandler's unused default constructs RunTokens without it; require the supplied
  tokens already passed by Composition rather than invent another clock there.
- AcquisitionControllers constructs CandidateSearch without today. Supply UTC
  LocalDate from a contextual Clock and thread that context from ServerWiring and
  test constructors. Preserve explicitly supplied deterministic today callbacks.
- CoreBudget callers with explicit callbacks remain valid. Main must use the same
  startup Clock as SystemContext. Composition, ServerPlugins and ServerGraph must
  receive the chosen budget/clock, without hidden SystemContext.isolated lookups.
  Indexes and OaFetch must receive their budget rather than construct a default.
- SttpHttp.apply callers must pass the chosen clock callback; constructing SttpHttp
  directly over an already-supplied PolicyHttp is a separate, unchanged shape.
  Composition.acquireClients needs its owning graph's clock threaded through.
- Test constructors should supply deterministic Clock values for behavior tests.
  Existing explicit mutable-clock CoreBudget tests must retain their semantics.

The caller batch preserves this contract: do not restore global
defaults to make compilation pass. Retain explicit time overrides in public methods.
The new methods in Engine, RunCtx, OaBacklog and AcquireRecheckService reuse their
existing agents clock; they introduce no new clock origin.

The evidence above completes the bounded 29-occurrence repair. Eleven monotonic
sites and two polling-deadline sites remain separately
confirmed design work. No detector refinement round or exception was consumed.
