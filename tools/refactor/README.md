# Refactor detectors

These tools read application sources. They do not rewrite application code.
Run commands from the `contracts3-scalafix` worktree root.

## Migration readiness

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
