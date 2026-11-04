# Detector results

Baseline: `6e1b5387`. Application and test changes use Scalafix. The current build adds the dependency-free console module and scribe for CLI diagnostics.

## Current checkpoint

The callback/permission approach below was rejected and replaced with our own Console interface, passed with `using`. `Cli.out.println` and `Cli.err.println` resolve the supplied instance. CLI, resolver and relink implementations no longer receive loose printing callbacks or access global streams. Command builders receive the contextual console; the four process entries choose adapters. No global Console instance or fallback given is defined. Diagnostic output uses scribe; CLI logs are configured for stderr.

Raw output permission now belongs only to the Console adapter. The previous Cli and entry-point output exceptions were removed. New checks forbid adapter construction inside command implementations, raw terminal input outside the adapter, and Console dependencies in domain/plugin code. The console fixture verifies six diagnostics and permitted/lookalike cases; enforcement rejects it with exit 64. Production coverage is 2,356/2,356, with 31/31 capability source guards. Findings remain 228 capability and 46 structural; this correction strengthens the architecture without claiming an additional backlog reduction. The structural audit covers 3,126 source/test files plus two fixtures, with 24/24 source guards.

Production and aggregate test compilation pass. The complete CLI and resolver suites and selected server launcher suites pass; details are in STATUS.md. The refreshed semantic migration inventory has no remaining output targets and its no-write second pass succeeds. Native builds and the full server runtime suite were not run.

The following entries are historical checkpoints, including the superseded callback design.

The launcher batch moves three diagnostic calls to scribe and replaces eleven raw console references in resolver/relink implementations with supplied output callbacks. Four raw stream references now live at their two entry functions. Three exact entry-function console permissions cover those four references and the server entry's three existing help/error/self-check references. This is both a code repair and an explicit policy change, not seventeen code fixes. No other capability permission changes.

Capability findings are 228 (console 0); structural findings remain 46. Ordinary SemanticDB covers all 2,355 production files. All 32 capability source guards pass, including a lifecycle violation on an entry with console permission. Production and aggregate test compilation pass. All 24 resolver tests pass; selected server suites pass 24/25, including the new JSON/digest/error-channel regressions. PluginTreeSuite's existing generated-tree consistency assertion fails on extra local Scalafix skill files and `.DS_Store` files; these were preserved. The rule's check mode finds no remaining rewrites. This snapshot-scoped syntactic recipe is not a general symbol-safe migration rule.

The following paragraphs record earlier batches.

The stderr batch redirected 24 resolved calls in 12 CLI files to narrow operations at the existing permitted Cli boundary. Capability findings fell from 269 to 245, with console findings 41 → 17; no other category changed and no permission was broadened. The remaining console findings are outside the CLI module.

Production and aggregate test compilation pass, as do all 234 CLI tests. Executable before/after fixtures preserve dynamic Console.err versus process System.err, receiver-before-argument evaluation when System.err is replaced, String overload selection, blank lines, carriage returns and flush counts. Method references and non-string overloads remain unsupported. Check mode rejects the original fixture, passes the compiled output and reports zero remaining or unsupported application cases. The real progress renderer has byte-exact regression coverage, including exceptions and single evaluation.

The preceding stdout batch redirected 163 symbol-resolved `Predef.print`/`println` references across 23 CLI files to three package-private methods on the existing permitted `Cli` owner. No capability permissions were added or broadened. Console findings fell from 204 to 41 and total capability findings from 432 to 269; all other category counts were unchanged.

Production and aggregate test compilation pass. All 233 CLI tests pass, including a new byte-exact output regression covering stdout/stderr isolation, blank lines, null, Unicode, callbacks, Markdown/JSON rendering and exactly-once argument evaluation/rendering. The executable fixture passes before and after rewriting, its output matches the expected source, and check mode rejects the original fixture but accepts the compiled result. An effectful receiver remains unchanged and is reported as unsupported. Application check mode reports zero supported rewrites and zero unsupported cases.

The first repair batch renamed AgentsHandlers and ManagersHandlers to Controllers: 137 semantic declaration/reference patches across 48 files, two guarded file moves and comment-token link updates. The four Handlers declaration findings are gone. Aliases and unrelated names are preserved; the compiled rewrite fixture matches its expected output and a second semantic pass has zero matches. The original fixture is correctly rejected by check mode.

The readiness matrix accounts for all 43 goals. Its command parses all 3,125 Scala source/test files, runs two structural fixture files and passes all 24 structural source assertions. It now reports 46 structural findings (34 primitive roles, 12 repository visibility), down from 50. G10's exact production-package constraint is measured; 42 goals remain partial/unmeasured, so readiness intentionally fails.

After this batch, production and aggregate test compilation pass. All 25 tests in ContractSnapshotSuite, AgentsSettingsSuite and AiProvidersSuite pass, with snapshot files unchanged. The snapshot test exposed an API/domain role membership mismatch in AgentDefinitions; a scoped Scalafix boundary conversion fixed it. Live database tests were not run for this rename batch.

Production compilation passes with normal SemanticDB, including server and CLI. The ordinary audit reads all 2,355 main-source files with zero missing files. All 32 guarded capability source assertions pass. It reports 228 policy findings, down from the compiling checkpoint's 432; the launcher permissions above contribute to that reduction.

| Configured check | Findings across all normally compiled main files |
|---|---:|
| Environment | 8 |
| System properties | 12 |
| Console output | 0 |
| Clock | 103 |
| Process lifecycle | 11 |
| Filesystem | 67 |
| HTTP | 1 |
| External configuration loading | 0 |
| Process execution/dependency | 7 |
| Repository factory dependency | 0 |
| Repository transaction lifecycle | 19 |
| Total | 228 |

The transaction count includes one known helper-call witness: `QueryRepo.run -> QueryRepo.exec -> Connection.setAutoCommit`. These are source-reference/call-site counts, not independent business operations. Repository helpers are followed across files, aliases and recursive paths; dynamic dispatch and callback targets are not fully modeled. Chained capability dependencies are deduplicated. A discovered cross-file local-symbol collision was reproduced in a failing regression fixture and corrected before retaining these results.

Six normally compiled semantic fixture files assert 37 diagnostics, with no unexpected findings. A separate intentionally broken compilation fixture asserts two known findings; normal verification correctly refuses its provisional evidence. Structural fixtures assert 18 diagnostics and protect launcher lookalikes, DOM handlers, nested package roots and repository visibility boundaries.

The ordinary audit exits 1 because violations remain, including the indirect repository witness. Coverage and all source assertions pass. Current details are in `capabilities/`, the goal matrix in `migration/coverage-matrix.md`, and reproduction instructions in `../tools/refactor/README.md`.

Aggregate `Test/compile` now passes across the repository. Core's 817 tests pass across the initial run and the successful rerun of the two repaired suites. Compiler repairs and test updates use Scalafix AST patches; production and test repair check modes verify idempotence. Database integration tests have only been compiled, not executed against a live database.

At the compiler-repair checkpoint, the complete CLI suite passed (232 tests), the files suite passed (73 tests), and the selected server suites passed (43 tests: PersonalTypes, PersonalControllers, VocabularyWire, AgentsSettings and Pages). The server regression rejects invalid/non-finite coordinates while accepting coordinate limits. CLI tests assert wire-input preservation instead of obsolete client-side validation; domain validation remains covered in core. The full server runtime suite and native builds were not run. `git diff --check` passes.

## Historical initial run

The following snapshot predates helper-call analysis, chain deduplication and best-effort metadata. It explains the earlier 88 count; it is not the current baseline.

The semantic detector analyzed 1,664 of 2,355 main-source files. All compiled
modules, including web, are covered. Server (617 files) and CLI (74 files) lack
SemanticDB because the inherited refactor does not compile there. The audit fails
with incomplete coverage rather than claiming success.

| Configured check | Direct findings |
|---|---:|
| Environment | 2 |
| System properties | 5 |
| Console output | 3 |
| Clock | 10 |
| Process lifecycle | 1 |
| Filesystem | 39 |
| HTTP | 1 |
| External configuration loading | 0 |
| Process execution/dependency | 9 |
| Repository factory dependency | 0 |
| Repository transaction lifecycle | 18 |

These 88 findings are policy matches requiring review. In particular, the
current plugin package scope includes plugin-testkit infrastructure; its intended
adapter permissions need review. Zero findings in a category is not proof of
compliance across missing sources or unsupported call patterns.

Repository lifecycle findings include commit, rollback and auto-commit control in
GazetteerRepo (14), QueryRepo (3) and VacuumRepo (1). Moving ownership must preserve
their batch, read-only query and maintenance behavior; no automatic fix was applied.

Four compiled fixture files assert 31 exact diagnostics, with no diagnostics on
their valid look-alikes and allowed boundary accesses. Positive fixture verification
exits 0. Normal lint verification exits 64. Tests also establish failures for an
unknown capability, a misspelled configuration key, a deliberately suppressed
required diagnostic and omitted fixture files.

The broader AST inventory remains exploratory. Its checklist records G1–G43,
including unmeasured checks; it is not an acceptance gate. Full details and
reproduction commands are in ../tools/refactor/README.md.
