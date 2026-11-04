# Audited examples and detector checklist

Two `gpt-6-luna` auditors inspected a source subset independently of the detector output: one checked capabilities and transactions, the other checked model contracts. This is a reviewed sample, not a census or a claim of measured recall. Application sources have not been modified.

`tools/refactor/source-contracts.conf` contains executable expectations for actual source locations: path, line, source guard, rule, exact count, and rationale. A moved/changed example fails verification rather than silently disappearing. Positive and negative fixtures additionally check every diagnostic, including unexpected ones.

## Model and visibility examples

Paths below are relative to `modules/`. The concept definitions were checked in `values/src/main/scala/com/histmersion/domain/library/Citekey.scala:12–14` and `values/src/main/scala/com/histmersion/domain/automation/Goal.scala:11–13`.

| Classification | Source | Evidence | Executable check |
|---|---|---|---|
| Violation | `server/src/main/scala/com/histmersion/domain/media/assets/AssetService.scala:36,39,41,49,51` | Public `citekey: String` parameters have an existing domain Citekey | Yes: primitive-role |
| Violation | `server/src/main/scala/com/histmersion/domain/automation/goals/GoalService.scala:24,30,41` | Public `slug: String` parameters have an existing Goal.Slug | Yes: primitive-role |
| Violation | `db/src/main/scala/com/histmersion/domain/automation/goals/GoalRepo.scala:13` | Public feature repository | Yes: repo-visibility |
| Valid | `server/src/main/scala/com/histmersion/domain/personal/seen/SeenRepo.scala:11` | `private[seen]` | Yes: repo-visibility |
| Valid | `server/src/main/scala/com/histmersion/domain/personal/bookmarks/BookmarkRepo.scala:13` | `private[bookmarks]` | Yes: repo-visibility |
| Valid | `server/src/main/scala/com/histmersion/domain/personal/reading/ReadingRepo.scala:12` | `private[reading]` | Yes: repo-visibility |
| Valid | `server/src/main/scala/com/histmersion/domain/personal/notes/NoteRepo.scala:12` | `private[notes]` | Yes: repo-visibility |
| Valid | `server/src/main/scala/com/histmersion/domain/activity/ActivityRepo.scala:14` | `private[activity]` | Yes: repo-visibility |
| Valid | `server/src/main/scala/com/histmersion/domain/library/collections/CollectionLinksRepo.scala:7` | `private[collections]` | Yes: repo-visibility |

The mappings identify concepts, not files or line numbers to flag. They apply to every matching public method parameter in an explicitly configured owner. Adding another concept requires architectural evidence. An unrelated `slug`, `id`, or `limit` does not automatically mean Goal.Slug, Citekey, or PageSize. This iteration does not infer concepts from names across the entire project or propagate them to implementations and call sites.

## Capabilities and transaction examples

`tools/refactor/capability-source-examples.conf` now checks 21 representative source locations, including positive and negative cases below, against actual application SemanticDB. All 21 pass with best-effort metadata included for server/CLI. This is distinct from the synthetic fixture result. Remaining sample locations still need their own assertions; provisional compiler output is not evidence that all type resolution is trustworthy.

| Classification | Source | Evidence / expected rule |
|---|---|---|
| Violation | `server/src/main/scala/com/histmersion/domain/automation/agents/AgentTrees.scala:93` | `System.getProperty("user.home")`: properties |
| Violation | same file `:147,153,162,164,190` | `Files` directory/read/write calls: filesystem |
| Violation | `server/src/main/scala/com/histmersion/domain/automation/agents/Prompts.scala:26,28` | `Files.isRegularFile/readString`: filesystem |
| Violation | `server/src/main/scala/com/histmersion/domain/automation/agents/RunTokens.scala:20,34,43` | `System.currentTimeMillis()` defaults: clock |
| Violation | `plugins/calibre/src/main/scala/com/histmersion/plugins/calibre/Calibre.scala:27` | `Files.isRegularFile`: filesystem |
| Violation | `plugins/calibre/src/main/scala/com/histmersion/plugins/calibre/Shell.scala:18` | `new ProcessBuilder`: process |
| Violation under current policy | `cli/src/main/scala/com/histmersion/cli/PluginCmd.scala:16,21` | Fully qualified System.getenv/getProperty outside approved composition owner |
| Valid | `db/src/main/scala/com/histmersion/domain/library/sources/SourceRepo.scala:53,272` | Repository borrows `(using DbCon)` |
| Valid for transaction ownership | `db/src/main/scala/com/histmersion/domain/automation/goals/GoalRepo.scala:20,57` | Repository borrows DbCon; its visibility is a separate violation |
| Valid | `server/src/main/scala/com/histmersion/domain/automation/goals/GoalService.scala:105` | Service owns `db.transact` |
| Valid | `server/src/main/scala/com/histmersion/domain/library/sources/SourceService.scala:156` | Service owns `db.transact` |
| Valid | `plugins/fetchers/src/main/scala/com/histmersion/plugins/fetchers/ArchiveOrgPlugin.scala:12` | Supplies `ctx.http` |
| Valid | `plugins/calibre/src/main/scala/com/histmersion/plugins/calibre/CalibrePlugin.scala:15` | Uses `ctx.env` |
| Valid | `plugin-testkit/src/main/scala/com/histmersion/plugin/testkit/PluginHarness.scala:455` | `ConfigFactory.parseString(text)` parses supplied text |

## Cases deliberately not promoted to violation assertions

- `values/.../LatLon.scala:5`: public constructor over typed parts is valid; being a public case class is insufficient evidence of an unchecked invariant.
- `server/.../AgentStateControllers.scala:16–18`: `in.value` is an ordinary record field. `.value` spelling alone does not prove an Iron unwrap.
- `server/.../CollectionsControllers.scala:37`: parsing and unwrapping at the controller boundary is allowed.
- `server/.../PeriodService.scala:16,46–66`: service trait, nested Implementation and InMemory are a valid shape. Their presence elsewhere in the same file would not prove ownership.
- `server/.../MapTileService.scala:16–21`: documented SQL mechanism without an in-memory twin; ownership/exception decision required before enforcing a universal service-shape rule.
- `server/.../FollowupsInput.scala:9–16`, `AgentSearchInput.scala:21,44`: customized schema derivation needs classification; `.derived` spelling alone is insufficient.
- `plugin-testkit/.../HttpMode.scala:71–72`: infrastructure environment/property access needs an explicit ownership decision.
- Asset uid and page-size suggestions need representation and role review before becoming concept mappings; no automatic UUID/String or Int/PageSize conversion is authorized by this audit.

## Work checklist

- [x] Obtain cheap-model positive and negative source samples.
- [x] Independently inspect concept definitions, representative signatures, visibility and capability evidence.
- [x] Implement configurable primitive-role and feature-repository visibility checks as Scala AST rules.
- [x] Check audited model/visibility examples with source guards and exact diagnostic counts.
- [x] Add synthetic lookalikes, typed parameters, local helper/lambda, nested implementation, broad visibility and protected visibility cases.
- [x] Prove missing and unexpected diagnostics fail verification.
- [x] Enumerate expected Scala files independently and reject missing file coverage.
- [x] Make 21 representative capability/transaction source examples executable, including previously noncompiling files; retain provisional status and unresolved-symbol evidence.
- [ ] Cover remaining audited sample locations and obtain trustworthy typechecking evidence before certifying these goals.
- [ ] Expand concept ownership and boundary configurations through further reviewed samples; measure unconfigured concepts separately.
- [ ] Add semantic unwrap, type ownership/companion, layer dependency and transaction call-chain rules with corresponding valid cases.
- [ ] Review all policy exceptions, including infrastructure boundaries.
- [ ] Establish a fixed, verified detector baseline before applying application rewrites.

The older candidate inventory is a search aid. Its count is not a violation total. The semantic run's 88 findings are also not a repository-wide total. File coverage and rule coverage are different measurements.

## Second independent audit and regression outcomes

- `cli/PluginTemplateKinds.scala:9` has `KindSources.main(kind: PluginKind): String`, an ordinary selector. The launcher check now requires an object-owned Array[String] entry signature; it does not flag this helper or the three top-level @main entry points.
- `web/map/MarkerHandlers.scala:11` is a DOM callback trait. Controller naming applies to configured domain packages. The two domain facades and their companions remain four declarations to migrate, not four separate features.
- Exact forbidden production package roots must not flag `upstreams.core` or test harness packages. Synthetic fixtures cover flat and nested forbidden roots; actual production currently has zero matches.
- G8 needs an explicit local-authority decision: the existing ContractsSuite exempts PluginId, PluginRepository and ScalaPackage because local plugin tooling cannot delegate to a server. No new blanket permission was added.
- G21's alias/companion, nested records and sealed-hierarchy exceptions were audited, but no reliable positive unrelated-type sample was found. This goal remains unmeasured rather than being calibrated on negatives alone.
- Repository helper calls are followed across files, aliases, multiple hops and recursion. A source audit confirmed `QueryRepo.run:39 -> QueryRepo.exec -> Connection.setAutoCommit`; safe statement closure remains unflagged.
- The full run exposed a local-symbol collision bug in the new call graph. A fixture reproduced it (an unexpected diagnostic on a harmless local helper), then passed with symbols keyed by source file. Inflated interim counts were discarded.
- The source assertion for `Shell.scala:18` exposed triple-counting a chained ProcessBuilder operation. The expected one finding was retained, the matcher was corrected, and a regression fixture added.

The current goal-by-goal readiness result is generated in `refactor-audit/migration/coverage-matrix.md`; 42 goals still lack sufficient evidence for full migration governance.
