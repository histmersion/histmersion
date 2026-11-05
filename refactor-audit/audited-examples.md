# Audited examples and detector checklist

## Multi-concept model purpose review after aeb46ecb

Three requested cheap-model source audits reviewed library/research, catalogue/media
and automation/jobs/storage concepts concurrently. This was purpose adjudication,
not a new detector-quality holdout or refinement round. The library audit inspected
six API files plus existing domain types; catalogue inspected ten API files plus
existing value types. The automation auditor reported seventeen API files rather
than the requested ten-file cap; that overrun is recorded, not described as meeting
the cap. No second search round was started.

Parent review checked the selected record declarations and existing wire types.
Corrections: Place.Footprint is directly in Place, not Place.Entry; the first review
run rejected the wrong exact owner before writing a ledger. The selector was
corrected from the source and existing inventory without dropping the field or
weakening exact-leaf assertions. Catalogue IDs use Place.Id/Event.Id companion
ownership, not new prefixed PlaceId/EventId names. Existing api.catalogue.Year,
Latitude and Longitude, and JobInfo.Id/Kind, are reuse targets. Wire request fields
are not exempt solely because they carry input: Pure wrappers preserve permissive
wire representation. Long historical bounds are not narrowed to Int Year.

The existing model Review rule now accepts configuration and retains its legacy
pagination default. model-concepts-review.conf freezes 79 distinct confirmed public
production API primitive leaves across 17 concepts, with source hashes, exact ASTs
and unchanged position/route identity construction. The event lives in
models/reviews/multi-concept-1/review-1.tsv; it is additional evidence for the
existing model family ledger, not another migration tracking system. The eleven
original pagination IDs and all their repair history remain unchanged.

Shared concepts: source citekeys, bibliographic titles, DOI, author-name elements,
publication year and venue; catalogue place and event IDs; latitude and longitude;
byte and file counts; storage measurement duration and freshness age; SHA-256;
background job ID and kind. Optionality/collection shape does not create another
concept. API types are Pure. No concrete cross-entity misuse justifies adding a
phantom parameter to these mappings.

Explicit limits: this review covers selected wire declarations, not every use of
these concepts or every module's definitions. The subsequent rewrite must discover
the whole definition/caller closure across modules. Raw parser strings, arbitrary
id/name/value fields, article-title equivalence, mixed external identifiers, Long
historical bounds, Int-to-Long asset-size widening, pixel-dimension ownership,
manager/run/item identity equivalence and unrelated count semantics remain
unresolved or outside this review. They are not compliant or accepted exceptions.
The 79 bulk-rewrite treatments describe the intended transformation, not current
rule support: ModelTypeRewrite still lacks general String/Long/Double/List support.
No newtypes or application fixes are claimed by this review.

## Bounded acceptance review at a06cb734 (5 November)

This is the current acceptance-pass evidence for migration-rounds.tsv, not a second
finding ledger. Three gpt-6-luna auditors read source without detector code, configuration
or reports. Each was capped at twelve source/build files. They inspected 12 model,
9 structural and 12 capability/build files. Their selections are purposive and small;
the same path can appear in different groups. No detector refinement was performed.
Missing positive/negative evidence freezes the family with a stated limit; it does
not justify an indefinite search or an accepted-quality claim.

Parent review normalized positive to violation and negative to compliant. The auditors
initially used inconsistent polarity. The structural auditor corrected its file count
from 11 to 9 (a failed open and search hits are not inspected files). SourceRepo.naming
is line 38, not the supplied 33; BasemapService is line 28, not 22; BuiltinAi.runtimes
is line 18, not its preceding comment. These coordinate corrections change no label
to fit detector output. Cross-layer MediaRole duplication was retracted as a G5
positive: G5 concerns duplication within one layer. Its unknown-role handling also
prevents assuming a closed vocabulary. AssetService's API reuse and RuntimeConfig's
placement remain unresolved, rather than inferring or granting exceptions.

### Source samples and comparison with existing evidence

Paths below are relative to modules unless explicitly marked build.sbt. The symbol
or original plan key in the evidence column is the persistent identity for newly
confirmed obligations; location lines are evidence, not identity. None is repaired
by this review. Treatments are source judgments, not generated repair eligibility.

| Goal | Source and anchor | Source judgment | Existing detector/inventory outcome; treatment or limit |
|---|---|---|---|
| G6 | values/.../com/histmersion/Period.scala:4,8–9; Period constructor/from | Negative: private constructor and checked order | model-construction and construction-site evidence only; no invariant completeness classifier. |
| G1 | values/.../domain/library/Citekey.scala:12–17; Citekey alias/companion | Negative: meaning-bearing domain newtype | newtype-constraints records the constrained Iron companion; not a whole-model semantic-purpose decision. |
| G35 | values/.../domain/library/Source.scala:9–13; Source.Status | Negative: enum with wire form | Enum declaration evidence retained; no complete vocabulary classifier. Approval.Kind in the same audited set is corroborating evidence, not an additional scored sample. |
| G3/G34 | core/.../domain/ApprovalModes.scala:4–6; G34:core/domain/ApprovalModes (original object-plan key) | Positive: standalone parsing helper has a planned type owner | object-plan says original-object-present/destination-review. Confirmed planned operation outstanding; targeted rewrite after destination mapping. Destination name in the historic plan is stale ApprovalMode, so do not mechanically invent an owner. |
| G11 | server/.../domain/personal/bookmarks/BookmarkRepo.scala:13; BookmarkRepo | Negative: feature-private repository | visibility inventory agrees feature-restricted; source detector has no violation. Earlier independent GoalRepo positive remains at db/.../goals/GoalRepo.scala:13 and still has the configured diagnostic. One positive/one negative is not validation of 490 discovered roles. |
| G16 | server/.../domain/personal/bookmarks/BookmarkService.scala:19,54,100; BookmarkService | Negative for structure | service-obligations finds trait/private Implementation/InMemory. Behavioral parity remains unproven. |
| G17 | server/.../domain/personal/BookmarkServiceContractSuite.scala:8–11 | Unresolved beyond suite shape | Shared-contract syntax is present, but this sample did not inspect both backend implementations; no parity or test-isolation acceptance. |
| G42 | server/.../util/basemap/BasemapService.scala:28; G42:com/histmersion/util/basemap/BasemapService# | Positive: required service remains a class | required-shape-missing for trait and companion implementations. One confirmed aggregate design obligation, not four defects; manual/design treatment. Structural sample: one TP and one TN (BookmarkService), no observed FP/FN, insufficient for whole-family acceptance. |
| G22 | core/.../domain/MergeFold.scala:16; G22:com/histmersion/domain/MergeFold.:internal-doc-scaladoc | Positive: internal documentation reference in type Scaladoc | comments.tsv marks internal-reference-review. One confirmed comment block; targeted rewrite. No justified-external-reason negative in this sample; candidate review is not a binary quality result. |
| G13 | db/.../domain/library/sources/SourceRepo.scala:38; SourceRepo.naming | Negative: method borrows DbCon | No repository ownership diagnostic. One TN, no positive; precision/recall undefined. Historical 17 TP/3 FN/0 FP and later negative-only holdout remain separate. |
| G14 | server/.../domain/media/MediaJobs.scala:15; G14:com/histmersion/domain/media/MediaJobs#:ds | Positive: domain caller holds DataSource | Declaration/database-dependency evidence exists, but the repository-only capability restriction does not diagnose this caller. Confirmed outstanding manual/design obligation and G14 detection gap; one missed positive, not a new code regression. |
| G15/G23 | config/.../config/UserPaths.scala:13–17; supplied environment methods and Path construction | Negative for those narrow expressions | No effect inferred from Path values or injected environment. This does not label the whole file compliant: basemapDir's previously confirmed user.home read remains outstanding. |
| G24 | build.sbt:926,965; cli/web dependency definitions | Negative for direct source edges | Evaluated Compile/Runtime closure also has no configured forbidden edge. Two source negatives, no positive; test and published-plugin dependencies remain unaccepted. |
| G27 | server/.../server/Main.scala:14 and cli/.../cli/Main.scala:5 | Negative: top-level @main | No object-main diagnostics. Two negatives, no positive; native launcher behavior not independently tested here. |
| G29 | build.sbt:681; G29:server/Sloth.patch-in-build.sbt | Positive: Sloth/native configuration remains in build.sbt | Build AST inventory records expressions, not a relocation verdict. One confirmed placement obligation; targeted rewrite through existing build/AST facilities after pipeline consolidation. |
| G32 | build.sbt:290; G32:upstreams-project | Positive: module still exists under the original dissolution goal | Evaluated build records upstreams; no G32 completion classifier. One design obligation, distinct from individual raw capability accesses. AGENTS describes the current module, not completion of the original migration goal. |
| G32 capability subcheck | upstreams/.../upstreams/http/UpstreamHttp.scala:7 and plugin-api/.../plugin/Plugin.scala:129 | Negative: supplied Plugin.Http | This narrow compliant path does not discharge the upstreams dissolution obligation. |
| G40 | server/.../server/agents/BuiltinAi.scala:18; G40:com/histmersion/server/agents/BuiltinAi.:runtimes | Positive: CLI runtimes remain host supplied | Declarations/build evidence exists, not a plugin-migration verdict. One extraction design obligation, not three newly counted provider defects. |
| G36 | api/.../api/media/MediaMarkdown.scala:7; G36:com/histmersion/api/media/MediaMarkdown. | Positive: standalone renderer object | Declaration and renderer/ownership evidence only. Confirmed renderer migration; targeted rewrite after rendered-type/caller mapping. |
| G43 | api/.../api/media/DataCredit.scala:3; G43:com/histmersion/api/media/DataCredit# | Positive: explicitly named legacy record remains | Declaration evidence only. Confirmed Attribution design obligation; caller and field semantics remain unreviewed. |

Unresolved source judgments retained: Period's intrinsic Year unwrapping; AssetService
API reuse; MediaRole closed/open vocabulary and display; Citekeys and DeepTime ownership;
MergeFold work-list operation destinations; DeepTime comment rationale; AppConfig.load
bootstrap ownership; RuntimeConfig mechanism placement; OutboundTls process ownership.
No independent sample was established for G8, G18/G30, G20 or G33/G39. These outcomes
freeze the affected detection scope as insufficient; they are not zero violations.
G33 also requires external process/data evidence, which this source pass cannot supply.

### Files inspected by the independent auditors

Model group (12): values/{Period,domain/library/Citekey,domain/library/Source,
domain/automation/Approval,domain/media/MediaRole}; api/media/{MediaRole,AssetKind,
DataCredit,MediaMarkdown,AssetsMarkdown}; server/domain/media/{assets/AssetService,
links/AssetLinkService}. Each shorthand denotes its existing src/main/scala/com/histmersion
path and .scala suffix.

Structure group (9): core/domain/{ApprovalModes,library/sources/Citekeys,DeepTime,
MergeFold,MergePlanner}; server/util/basemap/BasemapService;
server/domain/personal/bookmarks/{BookmarkRepo,BookmarkService};
server/src/test/scala/com/histmersion/domain/personal/BookmarkServiceContractSuite.scala.
Search hits beyond these files and the nonexistent BookmarkServiceSuite are excluded.

Effects/build group (12): db/domain/library/sources/SourceRepo;
server/domain/media/MediaJobs; config/config/{AppConfig,UserPaths,RuntimeConfig};
plugin-api/plugin/Plugin; upstreams/upstreams/http/UpstreamHttp;
server/server/Main; cli/cli/Main; server/server/agents/BuiltinAi;
plugin-host/pluginhost/OutboundTls; build.sbt. Source shorthand has the same prefix rule.

### Reconciliation corrections, not new repair credit

The old migration-rounds rows showed zero model/derivation fixes despite retained
evidence. Pagination's original eleven-ID ledger verifies eight repaired and three
outstanding (Filter.limit, Filter.offset, PassageHit.offset). SchemaDerives' retained
results verify 523 bridge rewrites (521 production, two tests). The consolidated
ledger now references those results; this is correction of reporting, not new work,
not an expanded confirmed denominator and not a current full-suite success claim.
Schema and pagination histories preserve pre-existing failing tests. All earlier
audit sections below remain historical evidence, including their old counts/limits.

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
