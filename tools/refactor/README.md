# Refactor detectors

Detector configurations read application sources; explicitly named repair configurations apply Scalafix patches.
Run commands from the `contracts3-scalafix` worktree root.

## Project-wide measurement and bulk rewrite gates

```text
scala-cli run --server=false -q -S 2.13.18 --dep ch.epfl.scala:scalafix-cli_2.13.18:0.14.9 --main-class scalafix.cli.Cli -- --config tools/refactor/assessment.conf --scala-version 3.10.0 --files tools/refactor/ProjectAssessment.scala
```

This read-only Scalafix assessment compiles production and tests with ordinary SemanticDB and formatting disabled, runs the developed capability/structural detectors and current migration measurements over all modules, and verifies the structural fixtures and source expectations. The assessment itself never applies application patches. Child commands are sbt and Scalafix; no Python or shell rewrite scripts are used.

`refactor-audit/assessment/current/summary.md` gives per-rule totals; `matrix.tsv` gives per-rule/per-source-module counts and baseline deltas. `coverage.tsv` lists production/test source counts, and `status.txt` must say MEASUREMENT_COMPLETE before these reports are accepted. Logs and detailed source/AST reports are in the same directory. Any failed compiler, missing/stale SemanticDB, source assertion, fixture, coverage check or source/policy change during the run fails the assessment; old reports must not be treated as current success. Sources shared by cross-built rows are counted once, not as independently verified platform-specific occurrences.

The 26 current metrics cover 14 capability checks, five structural checks, configured repository consumers, configured old owners/controller names, the CLI stdout/stderr/console-injection migrations, and plain Kindlings-to-Tapir schema bridges. The CLI migration metrics are applicable only to CLI production; their zero entries in other modules are not compliance evidence. Primitive-role mappings and repository/owner inventories remain explicitly configured scopes. Historical insertion/generation recipes (Console scaffolding, test insertion, one-off boundary patches and file movers) are not general detectors and must not be replayed as assessments. ContractInventory produces review candidates rather than confirmed violations. The 43-goal readiness matrix remains separate and still exposes unimplemented policy checks.

### Schema derivation batch

`SchemaDerives.scala` detects `given Schema[A] = summon[KindlingsSchema[A]].schema` by resolved symbols. It inventories all production/test modules and records each match's AST, eligibility, generated given symbol and explicit consumers. Scala 3 omits symbols on derives syntax; eligibility therefore checks the typed compiler-generated Kindlings instance on the companion. Non-local, generic, modified or otherwise unsupported definitions are reported for review. Customized schema expressions and strict JSON codecs are outside this rewrite.

Run the Scalafix CLI with `--config tools/refactor/schema-derives.conf --scala-version 3.10.0 --sourceroot . --auto-classpath --auto-classpath-roots target/out --files modules` to inspect. Apply with `schema-derives-apply.conf` after inspecting the plan. Source-set/hash checks reject stale plans. Apply adds `com.histmersion.api.json.Schema` to the existing derives clause, removes the redundant given/empty companion, and updates explicit given references, including imported aliases. The rule generates the shared adapter through Scalafix. `SchemaPatchWhitespace.scala` removes whitespace tokens left by removed definitions; run it over the same files after the rewrite. Recompile before semantic verification.

The adapter must be a concrete subtype of Tapir Schema: Scala resolves a plain type alias's derives through Tapir's companion, bypassing the intended Kindlings bridge. The concrete adapter delegates to Kindlings and preserves every schema constructor field. It requires no additional dependency. Its allocation occurs when the schema instance is constructed.

`SchemaVerification.scala` is the repeatable fixture runner, invoked by the project assessment. It compiles input and expected output, compares the automatic patch exactly, verifies failure before and success after, compiles and executes the rewritten consumers, checks idempotence, and tests nested automatic derivation and schema-field preservation. Its child processes are Scala CLI and Scalafix; artifacts stay under this worktree's `target/`.

Use `assessment-schema.conf` for the project-wide zero gate on `derivation/schema-bridge`. Zero means the plain bridge family is removed; it does not mean customized schemas, codecs, database derivations, or the entire derives policy are finished. The previous measurement baseline remains versioned; adding this detector makes the previous policy fingerprint incomparable rather than silently resetting it.

The initial baseline is versioned in `assessment/baseline.tsv`. Normal runs never replace it. `assessment-baseline.conf` explicitly captures a new reviewed baseline; do not use it to hide regressions. Rule/configuration fingerprint changes make deltas incomparable until the policy change is reviewed. `assessment-regression.conf` rejects increases in any rule/module cell against a comparable baseline.

For a batch completion gate, include `assessment.conf` and set `ProjectAssessment.zero = ["capability/console", "migration/old-owner"]` to the exact metrics the batch promises to eliminate. The command must exit successfully and report zero for each selected metric. `assessment-zero-reject.conf` deliberately selects the still-violated clock rule and must fail. Unsupported stdout/stderr shapes count as residuals, not as successful rewrites. Set `compile = false` only when ordinary compilation was already completed for the exact sources; SemanticDB validity is still checked.

Bulk migration target: at least 90% of the agreed violations fixed automatically, with the difficult remainder recorded explicitly. Select whole AST/symbol transformation families by volume; a one-occurrence rewrite proves a fixture, not useful migration throughput. `automation-backlog.md` reconciles the historical architecture ledger by family without presenting its stale exception counts as current findings. Before claiming a percentage, distinguish confirmed violations, valid cases, automatic fixes and deferred cases, retain a stable denominator, and validate definitions and call sites together. Do not sum overlapping detector metrics or count relocation/reference changes as separate policy violations fixed.

## Injected command console

Command output uses our `com.histmersion.util.console.Console`, passed with `using`. `Cli.out.println(...)` and `Cli.err.println(...)` are contextual accessors, not global streams. Commands that previously captured output in singleton fields are now contextual builders. Console adapters are selected at process entry points; no companion default given is supplied. Scribe remains the diagnostic logger, and the CLI configures it to stderr. Configuration warnings retain lazy evaluation and use scribe.

The dependency-free JVM `console` module is shared by CLI, server launchers and plugin-resolver. It contains the interface and stream adapters, never domain code. Only the adapter has raw stream/input permission. Four exact entry methods may select an adapter, but may not print directly. Rules reject console construction inside commands and Console dependencies in domain/plugin code.

`console-inventory.conf` uses SemanticDB to trace output dependencies through methods and member values, with file-scoped local symbols. `console-inject.conf` applies the resulting plan through AST/token patches. Always regenerate the plan from ordinary SemanticDB immediately before applying: source hashes reject stale plans. The final inventory has no remaining targets, and `--check` on the regenerated plan passes. This is snapshot migration tooling, not proof of arbitrary callback or dynamic-dispatch coverage.

The other `console-*.conf` recipes generate the parsed interface, update launchers/tests, handle default arguments, expose the imperative API and separate terminal prompts. They record the reviewed stages of this migration; do not rerun historical generation recipes indiscriminately against the completed code.

Compile the policy fixture against the real interface:

```text
scala-cli compile tools/refactor/console-policy-fixtures.scala modules/console/src/main/scala/com/histmersion/util/console/Console.scala --server=false --semanticdb --semanticdb-targetroot /Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix/target/refactor-console-fixtures
scala-cli run --server=false -q -S 2.13.18 --dep ch.epfl.scala:scalafix-cli_2.13.18:0.14.9 --main-class scalafix.cli.Cli -- --config tools/refactor/console-policy-fixtures.conf --scala-version 3.10.0 --sourceroot tools/refactor --semanticdb-targetroots target/refactor-console-fixtures --files tools/refactor/console-policy-fixtures.scala
```

Six exact diagnostics must pass fixture verification; substituting `console-policy-reject.conf` must exit 64. User-output regressions cover isolated injected consoles, channels, Unicode, JSON, progress, prompts and exit statuses. Production and aggregate test compilation use `scalafmtOnCompile := false` and ordinary `-Xsemanticdb`.

## Historical launcher output repair (superseded)

`launcher-output.conf` is a reviewed snapshot recipe using `RepairBoundaries` AST templates, scoped to four production files. It threads supplied output functions through resolver/relink signatures and callers and sends three diagnostics to scribe. Run it twice: outer definition patches intentionally defer nested body rewrites. `launcher-output-check.conf` must then pass with zero remaining matches. It is not a general semantic migration rule: its source paths, owners and shapes must be reviewed again for a different snapshot.

`launcher-output-tests.conf` adds regression tests through Scalafix in `PluginResolverSuite.scala` and `PluginToolSuite.scala`. These exercise JSON, digest-only stdout, usage stderr and exception exit status. The test insertion is idempotent. The application compiles with `-source:future` and normal SemanticDB; always disable scalafmt-on-compile during this migration.

The three exact entry-function permissions in `capabilities.conf` permit console access only. Seven raw stream references remain at these entries (four relocated, three existing); report this policy change separately from code repairs. The server generated-plugin-tree test currently rejects additional local skill and metadata files; do not delete those files to make this batch green. See `refactor-audit/RESULTS.md` for full results and outstanding findings.

## Migration readiness

### Repository visibility

`repository-visibility.conf` inventories the twelve configured exact repository symbols across production and test SemanticDB. Run with `--auto-classpath --auto-classpath-roots target/out --files modules` after ordinary production and test compilation. The report in `refactor-audit/repository-visibility/` records every resolved reference with its AST, outside consumer files, source coverage and a checklist. Counts include member and parameter references; they are not independent operations. Reflection and inferred public-type escapes still require review and compiler validation.

`repository-visibility-apply.conf` permits only the reviewed bundle repositories. It verifies that the entire source file set and content hashes match the inventory before constructing patches, and refuses targets with outside consumers. It adds `private[goals]` to GoalBundleRepo and `private[worklists]` to QueueBundleRepo. Compile all production and tests, then run `repository-visibility-check.conf` on both files. Missing approved declarations fail check mode. Fresh inventory reports the two as private and the remaining ten as blocked.

The fixture under `repository-fixture/src/main/scala` covers renamed imports, child-package access, method references, an outside consumer and a same-named unrelated object. Compile with SemanticDB into `target/repository-fixture`, inventory with `repository-fixture.conf`, apply `repository-fixture-apply.conf` using Scalafix `--out-from`/`--out-to`, and compare with `repository-fixture/expected/Repositories.scala`. Compile the output and check with `repository-fixture-check.conf`. `repository-fixture-reject.conf` must reject the exposed repository before writing; applying an old application manifest after a source change must also fail.

The 43-goal register is encoded in `migration.conf`, based on the user's `docs/refactor-goals.md` in the main checkout (read only). Historical counts and checkmarks are not completion evidence. Run the readiness entry point:

```text
scala-cli run --server=false -q -S 2.13.18 --dep ch.epfl.scala:scalafix-cli_2.13.18:0.14.9 --main-class scalafix.cli.Cli -- --config tools/refactor/migration.conf --scala-version 3.10.0 --sourceroot . --files modules --files tools/refactor/source-fixtures.scala --files tools/refactor/structure-fixtures.scala
```

This runs the current syntactic checks, both syntactic fixture files and 24 guarded real-source expectations in one invocation. It writes `refactor-audit/migration/coverage-matrix.md`, `readiness.txt`, findings, coverage and SHA-256 fingerprints of the parsed inputs and decoded policy. Input changes during the scan fail verification. Synthetic fixture findings are excluded from application counts.

**Current expected result: exit 1, NOT READY.** All 3,125 application/test Scala files are parsed, both fixture files pass, and all 24 real-source assertions pass. G10's exact production package constraint is measured; 42 goals remain partial or unmeasured. Syntactic findings are 34 primitive roles, 12 repository visibility and 4 Handlers declarations (two classes and their companions). Entry-point and bucket checks currently find zero in production. Zero for a narrower check does not complete its wider goal.

The entry point does not execute the separate semantic audit or claim its results are fresh. Those goals remain blockers until semantic coverage and the other evidence specified in the matrix are integrated. Removing goals, referencing unknown checks or omitting configured fixtures cannot produce a readiness pass. Only goals supported by the rule's implemented coverage registry can become measured; editing a configuration title or deleting its remaining-work text cannot promote unsupported goals.

`migration-incomplete-register.conf` is a negative configuration fixture: passing it in place of `migration.conf` must fail decoding with exit 4. The source assertion and fixture rejection checks below remain separate tests of the verifier itself. Readiness measures whether detectors can govern the migration; migration completion will additionally require zero violations or explicitly approved review decisions. No application rewrite is authorized by a successful fixture run alone.

## Semantic capability policy

`CapabilityAccess.scala` is a Scalafix semantic rule. `capabilities.conf` defines
eleven checks: environment, system properties, console output, clocks, lifecycle,
filesystem, HTTP, configuration loading, processes, repository factories and
repository transaction lifecycle.

The policy matches resolved symbols, including aliases and method references.
`symbols` uses normalized semantic matching (all overloads); `prefixes` uses raw
SemanticDB symbol prefixes. `within` restricts enclosing owner prefixes; `outside`
excludes owner prefixes. `repositoriesOnly` selects explicitly configured
`repositoryOwners` or enclosing types with a configured `repositorySuffixes` suffix.
Suffixes are an explicit naming convention, not proof that all repositories were found.

Permissions name an exact enclosing owner symbol and specific capability IDs, with
a reason. They are not file-wide or general-purpose exemptions. Permissions apply
to nested code inside that owner. The report counts permission uses; zero uses
requires review and does not prove the permission is obsolete when coverage is incomplete.

The clock policy distinguishes overloads using parameter signatures: `now(clock)`
is allowed, while `now()` and `now(zone)` are reported. Configuration parsing of
supplied strings is allowed; external loading and access to environmental config
are reported. Whether a domain should depend on a config representation at all is
a separate, not-yet-implemented layer dependency check.

Unknown configuration keys, duplicate IDs and permissions naming unknown
capabilities are rejected. `sourceRoots` independently enumerates expected Scala
sources containing `sourcePathContains`; missing analyzed files fail the run.

## Fixture verification

Compile fixtures with the same Scala version as the application:

```text
scala-cli compile tools/refactor/fixtures.scala tools/refactor/clock-fixtures.scala tools/refactor/config-fixtures.scala tools/refactor/import-fixtures.scala tools/refactor/transaction-fixtures.scala tools/refactor/transaction-helpers.scala --server=false --semanticdb --semanticdb-targetroot /Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix/target/refactor-fixtures
```

Run the fixture assertions through Scalafix itself:

```text
scala-cli run --server=false -q -S 2.13.18 --dep ch.epfl.scala:scalafix-cli_2.13.18:0.14.9 --main-class scalafix.cli.Cli -- --config tools/refactor/fixtures.conf --scala-version 3.10.0 --sourceroot tools/refactor --semanticdb-targetroots target/refactor-fixtures --files tools/refactor/fixtures.scala --files tools/refactor/clock-fixtures.scala --files tools/refactor/config-fixtures.scala --files tools/refactor/import-fixtures.scala --files tools/refactor/transaction-fixtures.scala --files tools/refactor/transaction-helpers.scala
```

The rule runtime is Scala 2.13; analyzed code is Scala 3.10. Fixture mode compares
every diagnostic's ID and line against `// expect:` markers, including duplicate
counts, and rejects unexpected diagnostics. Unmarked lines are negative examples.
The configured fixture-file manifest prevents accidentally omitting a file.
This mode returns no lint patches after successful assertions. Exit 0 is required.

Repeat the same invocation with these configurations to test failure paths:

| Configuration | Expected result |
|---|---|
| `reject-fixtures.conf` | Exit 1: direct lint errors plus indirect repository violations rejected at completion |
| `typo.conf` | Exit 4: misspelled configuration key rejected |
| `invalid.conf` | Exit 4: permission references an unknown capability |
| `missing-diagnostic.conf` | Exit 1: deliberately allowed violation breaks expected diagnostics |
| `incomplete-fixtures.conf`, passing only `fixtures.scala` | Exit 1: omitted fixture files break coverage |

These fixtures cover look-alikes, aliased and ordinary imports, method values,
captured console streams, nested calls, explicit-owner and suffix-selected
repositories, borrowed connections, narrow permissions, injected clocks, config
loading and pure parsing. They are a starting contract, not exhaustive coverage of
all libraries and syntax forms.

The six compiled semantic fixture files assert 37 diagnostics: 32 direct and 5 through known helper bodies. Transaction fixtures cover cross-file helpers, aliases, multiple hops, recursion, local helpers, exact-owner permissions and safe statement closure. A regression with repeated local SemanticDB IDs failed before file-scoped graph keys were introduced and passes afterwards; raw `localN` IDs must never be treated as project-wide symbols. A chained ProcessBuilder construction/start is one capability dependency; the source audit caught its previous triple report, and the regression now asserts one.

The repository-only checks follow resolved calls through known source method bodies. The report's `call-chain` column gives a witness from the reported call to the forbidden API. These are call-site findings, not independent transaction operations. Direct diagnostics and indirect ones are deduplicated at a given rule/location. Source-local symbols are keyed by file and symbol; global symbols retain their resolved identities. General filesystem/HTTP helper propagation is not enabled: calling an approved supplied utility must not become a violation merely because its implementation performs I/O. Dynamic dispatch, unknown helper bodies, callback targets passed as values, constructor/initializer effects and inferred escapes remain outside this graph's coverage.

## Application audit

First compile with SemanticDB, without automatic formatting:

```text
sbt --server -batch 'set every scalafmtOnCompile := false' 'set ThisBuild / scalacOptions += "-Xsemanticdb"' compile
```

Then run the detector. Both JVM and Scala.js outputs are searched:

```text
scala-cli run --server=false -q -S 2.13.18 --dep ch.epfl.scala:scalafix-cli_2.13.18:0.14.9 --main-class scalafix.cli.Cli -- --config tools/refactor/capabilities.conf --scala-version 3.10.0 --sourceroot . --auto-classpath --auto-classpath-roots target/out --exclude '**/src/test/**' --files modules
```

Do not disable stale SemanticDB checks. Production compilation now passes after the
Scalafix boundary repairs. The normal audit covers all 2,355 main files and reports
432 policy findings. Use `capabilities-source.conf` to also assert all 21 audited
source examples. The run fails on violations, with no missing files;
`refactor-audit/capabilities/missing-files.txt` lists any gaps and
`coverage.tsv` groups them by module. `findings.tsv` includes source locations,
owners, referenced symbols, source fragments and ASTs. The command exit status
and coverage report both matter.

## Compiler repair rules

### CLI stderr boundary batch

`CliErrorBoundary.scala` inspects resolved Console.err/System.err uses and the
actual PrintStream method signature. It rewrites immediate calls only: String
print/println and zero-argument println/flush on stable known singleton roots.
Method references, non-string overloads, escaped streams and effectful receivers
remain unsupported in the report. `cli-error.conf`, `cli-error-apply.conf` and
`cli-error-check.conf` provide inspect/apply/check modes with the ordinary
semantic options and `--files modules/cli/src/main`.

`AddCliError.scala` inserts narrow package-private methods and a regression into
OutputSuite through AST patches. By-name String parameters allow each method to
capture its stream before evaluating the message, preserving original evaluation
order. Console.err and System.err remain separate; no PrintStream escapes the
boundary. Run aggregate compilation and `cli/testOnly *` after applying.

The executable fixtures use `cli-error-fixtures/Support.scala` and either
`cli-error-fixtures/input` or the separately rewritten output. Run with
`--main-class fixture.Verify --server=false --semanticdb` and a dedicated target
root. Apply `cli-error-fixtures.conf` with `--out-from`/`--out-to`, compare its
Example.scala with the expected file, and run the output with Support.scala.
Tests assert stream replacement during argument evaluation, distinct dynamic and
process stderr, exact bytes, overload selection and flush counts.
`cli-error-fixtures-check.conf` rejects the original's eight supported calls and
passes the recompiled output, retaining two unsupported examples. Application
check mode must report zero supported and unsupported cases for this batch.

### CLI stdout boundary batch

`CliOutputBoundary.scala` matches semantic `Predef.print`/`println` references in
CLI production sources, excluding the configured exact boundary owner. It replaces
only the callable reference, preserving argument syntax, overloads and callbacks.
Selected receivers must resolve to the Predef singleton; potentially effectful
receivers are left unchanged and recorded as unsupported. This rule does not
rewrite imports, stderr, printf, arbitrary PrintStreams or server diagnostics.

Use `cli-output.conf` for inspection, `cli-output-apply.conf` for application and
`cli-output-check.conf` for idempotence, with ordinary semantic options and
`--files modules/cli/src/main`. The TSV and summary distinguish supported rewrites
from unsupported cases. Check mode fails on remaining supported matches; it does
not certify unsupported cases as compliant. The independent capability audit
continues to detect raw console access.

`AddCliOutput.scala` adds the three package-private methods at the existing Cli
boundary and the real OutputSuite regression via AST patches. No new permission
is required. After recompiling, run `cli/testOnly *` and the full capability audit.
The executable input/expected fixtures in `cli-output-fixtures/` assert exact
stdout and stderr bytes and evaluation behavior. Run the input with SemanticDB,
apply `cli-output-fixtures.conf` to a separate output directory, compare with
expected source, then compile/run the result with fresh SemanticDB.
`cli-output-fixtures-check.conf` fails on the original and passes on the result,
while retaining and reporting the effectful-receiver case.

### Controller rename batch

`RenameControllers.scala` uses the exact configured class/companion symbols. It
preserves aliases, unrelated same-named symbols and string literals. Its TSV
records every candidate's symbol, source, AST and disposition. These two mappings
were inspected against normally compiled production and test sources before use;
this rule is not a general package/class relocation engine.

Use `controller-renames.conf` to inspect, `controller-renames-apply.conf` to apply
and `controller-renames-check.conf` to reject remaining matches, with the normal
Scalafix semantic options and `--files modules/server` (including tests).
After applying, `MoveControllerFiles.scala` moves the two source files only when
the renamed class and companion exist and destinations do not collide.
`ControllerDocLinks.scala` updates the reviewed old links inside comment tokens.
Recompile before running semantic checks again.

`controller-fixtures/input/Example.scala` exercises constructors, companions,
aliased imports, qualified types, callbacks, lookalikes and string preservation.
Compile with SemanticDB, run `controller-fixtures.conf` using `--out-from` and
`--out-to`, compare against `controller-fixtures/expected/Example.scala`, and
compile the result. `controller-fixtures-reject.conf` must fail on the input and
pass on the recompiled output. The application check must also pass after the
rename. The four structural findings become zero without removing the Handlers
violation cases from `structure-fixtures.scala`.

The contract snapshot run also exposed an API/domain role comparison in
`AgentDefinitions.agent`. `controller-boundary-repair.conf` performs the checked
AST replacement that converts the parsed API role to the domain role before
roster membership is tested. Snapshot expectations are unchanged.

`RepairBoundaries.scala` matches parsed Scala trees using scoped recipes in
`repair-boundaries.conf` and `repair-tests.conf`. Names starting with `__` are
AST captures. Repeated captures must have identical structure. Paths and optional
enclosing method names restrict each rewrite. Captured source tokens retain their
syntax, and the resulting tree must equal structural AST substitution before a
patch is accepted. These are compiler-guided repairs for this snapshot, not general
symbol-safe migrations to apply indiscriminately in other projects.

Use the usual Scalafix CLI invocation with `repair-boundaries.conf` to inspect,
`repair-apply.conf` to apply, and `repair-check.conf` to reject remaining matches.
The report contains before/after source and ASTs for the latest run. Some recipes
depend on earlier repairs; repeat until check mode passes, then compile. Application
sources were changed only through these Scalafix rules, with formatting-on-compile
disabled.

The fixture under `repair-fixtures/input/agents/ModelPicker.scala` rewrites to
`repair-fixtures/expected/agents/ModelPicker.scala`; the expected output compiles
against Chimney 2.1.0. Running check mode on the input fails with one remaining
repair. Checking repaired production sources succeeds. `RelocateDomainSuites.scala`
moves the three suites that depend on domain helpers from API to core, checking
their class names, refusing existing destinations and verifying the copied contents
before deleting the original. `AddTargetRegression.scala` adds checked-coordinate
boundary cases to the existing personal model suite.

Test recipes apply with `repair-tests.conf`; `repair-tests-check.conf` rejects
remaining matches. Pass the repaired modules' `src/test` directories as files.
The three CLI validation tests now assert preservation of wire inputs for server
validation. Domain citekey rejection remains tested in the relocated core suite;
the server coordinate regression covers out-of-range and non-finite values.

Compilation and targeted verification (without formatting application sources):

```text
sbt --server -batch 'set every scalafmtOnCompile := false' 'set ThisBuild / scalacOptions += "-Xsemanticdb"' compile 'Test/compile'
sbt --server -batch 'set every scalafmtOnCompile := false' 'set ThisBuild / scalacOptions += "-Xsemanticdb"' 'core/test' 'cli/testOnly *' 'files/test' 'server/testOnly *PersonalTypesSuite *PersonalControllersSuite *VocabularyWireSuite *AgentsSettingsSuite *PagesSuite'
```

## Historical best-effort reproduction

Scala's [best-effort mode](https://www.scala-lang.org/api/3.5.1/docs/docs/internals/best-effort-compilation.html) can emit SemanticDB despite type errors. This was verified on the installed Scala 3.10.0 using `best-effort-fixture.scala`: compilation exits 1 with two intended errors, yet the two known capability references remain detectable. The missing type appears in the unresolved-name report. Error placeholders can still carry symbols, so an empty unresolved report would not certify valid code.

Generate provisional metadata separately from ordinary metadata, with formatting disabled:

```text
sbt --server -batch 'set every scalafmtOnCompile := false' 'set server / Compile / scalacOptions ++= Seq("-Xsemanticdb", "-Ybest-effort", "-semanticdb-target", "/Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix/target/refactor-best-effort")' 'set cli / Compile / scalacOptions ++= Seq("-Xsemanticdb", "-Ybest-effort", "-semanticdb-target", "/Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix/target/refactor-best-effort")' 'all server/compile cli/compile'
```

Observed exit 1: CLI reports eight inherited errors; server also reaches a compiler pickling assertion in `CompareRunsViews.scala`. SemanticDB extraction happened before that assertion. Do not call this a successful build. The following audit reads the isolated provisional output alongside the ordinary metadata already present in `target/out`:

```text
scala-cli run --server=false -q -S 2.13.18 --dep ch.epfl.scala:scalafix-cli_2.13.18:0.14.9 --main-class scalafix.cli.Cli -- --config tools/refactor/capabilities-best-effort.conf --scala-version 3.10.0 --sourceroot . --auto-classpath --auto-classpath-roots target/out --semanticdb-targetroots target/refactor-best-effort --exclude '**/src/test/**' --files modules
```

All 2,355 main files were read, with no omitted files. All 21 guarded source expectations passed, including service-owned transactions, borrowed DbCon, injected Clock overloads, ctx.http/ctx.env, pure config parsing and pure Path construction. There are 432 provisional policy findings and 4,578 name lookups without symbols. The latter is a diagnostic inventory, not a count of compiler errors. `bestEffort=true` forces exit 1 even when coverage and assertions pass; `verification.txt`, `coverage.tsv`, `findings.tsv` and `unresolved.tsv` are under `refactor-audit/capabilities-best-effort/`.

Reproduce the broken-code fixture separately:

```text
scala-cli compile tools/refactor/best-effort-fixture.scala --server=false --semanticdb --semanticdb-targetroot /Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix/target/refactor-best-effort-fixtures --scalac-option -Ybest-effort
scala-cli run --server=false -q -S 2.13.18 --dep ch.epfl.scala:scalafix-cli_2.13.18:0.14.9 --main-class scalafix.cli.Cli -- --config tools/refactor/best-effort-fixtures.conf --scala-version 3.10.0 --sourceroot tools/refactor --semanticdb-targetroots target/refactor-best-effort-fixtures --files tools/refactor/best-effort-fixture.scala
```

The compile must fail; the assertion run must pass. Switching to `best-effort-reject.conf` must fail verification because provisional compiler evidence cannot certify compliance. Keep best-effort metadata out of the ordinary audit's classpath roots.

[Lorikeet](https://github.com/epfl-systemf/lorikeet) was also reviewed: it offers query/template syntax over Scalafix, but its documented grading workflow skips noncompiling submissions. It does not remove this evidence gap; no additional framework or formatting workflow was installed.

## Broader candidate inventory

```text
scala-cli run --server=false -q -S 2.13.18 --dep ch.epfl.scala:scalafix-cli_2.13.18:0.14.9 --main-class scalafix.cli.Cli -- --rules file:tools/refactor/ContractInventory.scala --scala-version 3.10.0 --sourceroot . --files modules
```

`ContractInventory` is exploratory syntax analysis. Its generated checklist names
G1–G43 and marks unimplemented checks as unmeasured. Candidates are not confirmed
violations: primitive roles, aggregate invariants, object ownership, comments,
custom codecs and module boundaries need more evidence. Comments carry their token
structure rather than repeated enclosing-file ASTs. This inventory does not replace
the tested semantic detector or the architectural design review.

## Remaining work

- Extend the repository call graph to dispatch, supplied callbacks and initializer effects; define approved capability boundaries before adding propagation for other capabilities.
- Analyze inferred types, aliases and overridden methods against forbidden base
  capabilities. Listing a base symbol does not automatically match an override.
- Expand and test the symbol catalog: it is not an exhaustive Java/Scala I/O list.
- Review plugin-testkit/infrastructure ownership and explicit boundary permissions.
  The current conservative package scope includes `plugin.testkit` for review.
- Validate permission-owner existence across the complete compilation scope.
- Add approved type-placement and operation-ownership mappings and their detectors.
- Implement and test the remaining goal-specific checks before any mass rewrite.
- Add independent testkit-level tests of the fixture assertion machinery itself.

Scalafix source checking is not a runtime sandbox. Reflection, native code,
unresolved dynamic dispatch and effects hidden inside external libraries are not
proven safe by these rules. No blanket effect-monad restriction is introduced.
# Audited source contracts

The source-audit checklist is in `refactor-audit/audited-examples.md`. Two cheap-model audits supplied both violations and valid cases. `source-contracts.conf` turns the reviewed model and repository-visibility subset into executable expectations against actual source files. Each expectation guards the original line text and checks the exact count for its rule. Stale examples, unchecked examples, missing source coverage and unexpected fixture diagnostics fail the process.

Run the AST checks on every Scala file, including sources that do not compile:

```text
scala-cli run --server=false -q -S 2.13.18 --dep ch.epfl.scala:scalafix-cli_2.13.18:0.14.9 --main-class scalafix.cli.Cli -- --config tools/refactor/source-contracts.conf --scala-version 3.10.0 --sourceroot . --files modules
```

This inventory returns exit 0 when coverage and assertions pass, even with violations. It writes `refactor-audit/source-contracts/{summary.txt,verification.txt,findings.tsv,files.txt,missing-files.txt}` plus input fingerprints. Findings include source and AST. It is not a zero-violation migration gate. The current checks cover configured primitive roles, feature repository visibility, exact forbidden packages, Handlers declarations in configured controller packages, and ordinary Array[String] launcher signatures on objects. The full readiness command above adds the goal coverage requirements and synthetic fixtures.

Run the synthetic fixtures:

```text
scala-cli run --server=false -q -S 2.13.18 --dep ch.epfl.scala:scalafix-cli_2.13.18:0.14.9 --main-class scalafix.cli.Cli -- --config tools/refactor/source-fixtures.conf --scala-version 3.10.0 --sourceroot . --files tools/refactor/source-fixtures.scala
```

Expected exit 0, six diagnostics asserted internally. Every unmarked line must stay clean. Cases include configured and unrelated owners, typed parameters, unrelated parameter names, private helpers, lambda parameters, nested implementations, feature-private repositories, overly broad private scope and protected repositories.

Use the same fixture command with these alternative configs to exercise rejection:

| Config | Expected result |
|---|---|
| `source-missing-diagnostic.conf` | Exit 1: removed concept mapping loses an expected finding |
| `source-unexpected-diagnostic.conf` | Exit 1: extra mapping flags a clean owner |
| `source-stale-example.conf` | Exit 1: source guard no longer matches |
| `source-missing-files.conf`, with `--files tools/refactor/fixtures.scala` instead | Exit 1: independently enumerated fixture was not analyzed |

Concept mappings are explicit owner/parameter/raw-type/required-type records. They check declared syntax, not inferred types or aliases. Unconfigured concepts and implementation/call-site propagation are unmeasured. Repository roles currently use the feature-package and `Repo` suffix convention; unnamed repositories remain unmeasured. A feature-private qualifier must name the feature package or an enclosing owner, not a broader domain. These rules make no claim about unwraps, constructor invariants, service ownership, transitive calls or capability coverage in files missing SemanticDB. Tests are parsed for coverage but production checks currently apply only to main sources.
