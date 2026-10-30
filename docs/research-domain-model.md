# Domain model, package by feature, first-party plugins out of the server (refactor step 3, design)

Status: **proposal, waiting for the user's approval**. Nothing here is built. Written 2026-10-30 on master
58de5489 (branch `domain-design`). Answers review items #7485, #7490, #7903, #7908 and #7911, and the
"ad-hoc model" remark.

Inventories too long for this page are in `research-domain-model/`:

| file | what it lists |
|---|---|
| [`objects.md`](research-domain-model/objects.md) | all 645 top-level objects without a same-named type, each with its destination and a one-line reason |
| [`files.md`](research-domain-model/files.md) | every source file of `server`, `core`, `db`, `api`, `config`, `files` mapped to its target feature package |
| [`primitives.md`](research-domain-model/primitives.md) | primitive parameters and fields by name, measured per module, and the counts behind §4 |

## 0. Summary

1. The code is regrouped into **36 features** (§3; 39 packages, counting three subpackages). Each feature is one package, `com.histmersion.domain.<feature>`,
   holding its types, its pure rules, its service (trait + `Implementation` + `InMemory`), its repository
   (package-private) and its controllers. Wire records go to `com.histmersion.api.<feature>`, settings records to
   `com.histmersion.config.<feature>`. Supporting code goes to `com.histmersion.util.<area>`.
2. The model stops passing primitives: **about 55 identifier and value newtypes** and 32 vocabulary enums (§4),
   most of them in `values`, replace roughly 4 000 measured `String`/`Int`/`Long`/`Double` parameters and fields.
   Their JSON, columns, HOCON keys, CLI flags and MCP schemas stay byte-identical: every newtype's instances are
   derived from its base type's by one generic given per library.
3. Of the 645 type-less top-level objects, 229 move onto a type (a companion, a new or renamed type, or an enum),
   51 namespace objects dissolve into packages, 29 move into plugin modules, 10 handler groups become
   controllers, 6 `main` objects become `@main def`s, and 320 stay standalone for one of six stated reasons (§5);
   205 of those are web views, CLI command groups and endpoint groups, 57 are repositories.
4. Every service has the shape of AGENTS.md rule 13, with one worked example on the queues (§6). Repositories take
   the connection they run in; services open transactions through a small `Transactor`; the clock and the
   environment come from the composition root.
5. The `db` module shrinks to database primitives (`util.database`); repositories and the 95 migrations move to the
   server (the migrations byte for byte, at the same class-path location). `api` is split into a root of shared
   wire types and one package per feature (§7).
6. The 26 first-party plugins that live in the server today move into **five plugin modules** (fetchers, events,
   media, ai-http, ai-cli), beside the existing vaults module, plus one shared library of upstream parsers and
   clients. Every
   plugin module depends on `plugin-api` only (and the library). They register through `META-INF/services` like
   any other plugin, stay built in and stay switchable. Nine additive SPI members close the measured gaps (§8).
7. The work is **12 slices** (§9), each a feature or a plugin family end to end. Equivalence is shown the way
   step 2 showed it, made permanent: snapshot files of the OpenAPI document, MCP `tools/list`, the agent toolbox
   schemas, the CLI help tree and the plugin report, which must not change. Each slice also needs codec round-trip
   tests, the DB suites, and a native bundle with `--self-check`.
8. Section 11 has 16 open questions. The ones that change the shape most are: whether inputs are parsed in decoders
   or controllers (Q1), whether the three CLI agent runtimes move now (Q5), and the module granularity of the
   plugins (Q4).

## 1. The review points and where each is answered

| # | asked | answered in |
|---|---|---|
| #7485 | package by feature; `domain`, `domain.<sub>`, `util.<area>`; package-private repos; db = primitives only; services own transactions; no globals; Handlers → Controllers; api split into shared vs API-specific; subdomains independent of endpoints | §3, §6, §7; slices 2, 5–12 |
| #7490 | `private final class Implementation` + `final class InMemory` on the service companion; InMemory behaves like the DB; shared atomic refs; seeded through the service; no mockito | §6 (with a contract-suite that proves "behaves like the DB") |
| model | "ad-hoc model, helpers on primitives instead of newtypes" | §4 (catalogue, measured), §5 |
| #7903 | no free-standing helper objects; rule 14 | §5, `objects.md`, rule text in §5.4 |
| #7908 | `@main def` instead of `object X { def main }` | §5.5 |
| #7911 | first-party plugins out of the server, one module per plugin or family, `plugin-api` only, ServiceLoader, built in, switchable | §8; slices 3, 4 (and 12 for ai-cli, Q5) |

Fixed decisions kept everywhere: one ServiceLoader service type (`com.histmersion.plugin.Plugin`); `plugin-api` holds
interfaces and plain data only; our own plugins are built in and can be disabled; everything stays AGPL-3.0-or-later.

## 2. Today, measured

All numbers are from `modules/*/src/main` at 58de5489; the scans are regexes run once for this document
(the method is at the top of each inventory file).

| measure | value |
|---|---|
| Scala sources (main) | 1 770 files, 161 k lines: server 311 / 44 k, api 597 / 25 k, web 264 / 34 k, db 75 / 18 k, core 100 / 17.5 k, cli 76 / 9.8 k, config 145 / 3.8 k |
| top-level objects | 1 281; **645** without a same-named type (the brief counted 644 of 1 313) |
| `citekey`/`ck`/`citekeys: String` | 426 (393 outside web) although `values.Citekey` exists; `Citekey` is used in 15 signatures |
| `slug: String` | 241 (198 outside web); no `Slug` type. Three different slug regexes (`Articles.slugOf` bounded at 121 chars, `ArticleLinks` and `MergePlanner` unbounded) |
| `doi: String` | 129 although `values.Doi` exists (3 typed uses) |
| `lat`/`lon` (and bbox edges): `Double` | 186 although `Latitude`/`Longitude` exist (4 typed uses each) |
| years as `Long`/`Int` (`periodStart`, `from`, `year`, …) | 450 although `values.Year` exists |
| source uids (`uid`, `sourceUid`, `origin`: `UUID`/`String`) | 157 |
| primitive fields in case classes | config 528, core 827, server 831, api 2 936 (`String`/`Int`/`Long`/`Double`; Booleans not counted) |
| global reads in main code | `Instant.now()` 53, `System.currentTimeMillis` 38, `System.nanoTime` 35, `Thread.sleep` 28, `System.getProperty` 17, `sys.env` 13, `System.getenv` 11, `UUID.randomUUID` 9, `LocalDate.now` 3, `user.dir`/`user.home` 8 |
| `object X { def main }` | 6 in main code (server `Main`, cli `Main`, web `Main`, `PluginTool`, `GeneratePlugin`, `PluginResolver`) + 3 test tools (`BasemapLabelsTool`, `BasemapExtractTool`, `PromptBudget`). #7908 names `AgentTrees`; it has no `main` — `GeneratePlugin` is the one that does |
| endpoint logic | `Handlers` (1 891 lines, 130 methods over 17 features), `OpsHandlers` (287), 9 more `*Handler(s)` classes (acquire, agents, managers, ingest, bundle, events, media, IIIF, auth) |
| first-party plugins in the server | 26 (7 fetchers, 10 media collections, 3 event catalogues, 6 AI providers); `plugin-vaults` (3) is the only module |

Live data checked read-only (`histmersion query`) against the constraints proposed below: 0 of 1 209 citekeys and
0 DOIs break their rule; all 467 article slugs and all 214 goal slugs match `[a-z0-9][a-z0-9_.-]*`. Place ids are
`<authority>_<id>` (`geonames_2911611`), event ids are slugs (`uluburun_shipwreck`), job ids are UUIDs.

A start already exists. `server/…/domain/{activity,jobs,personal,settings}` follow rule 13 roughly: there is a
trait, an `Implementation`, an `InMemory`, and controllers. They still use `() => Instant`, `DataSource` and
primitive limits. Slice 2 brings them in line, and they are the pattern the rest copies.

## 3. Feature map

A feature is a piece of functionality with its own data. It is not an endpoint, a tool or a command: one
endpoint may call several features (the About window reads `instance`, `plugins`, `acquisition` and
`maintenance`). The "tables" column says which feature owns which rows. Only the owner's repository writes them;
other features go through the owner's service.

### 3.1 Domain features (`com.histmersion.domain.<feature>`)

| feature | concepts | tables owned | today (main files) | lines |
|---|---|---|---|---:|
| `sources` | source record, metadata, OCR body, aliases, identifiers, rename, delete, source note export, passages | `sources`, `source_aliases`, `source_identifiers`, `source_chunks` | `Handlers` (source*, rename, delete, export, passages), `db/SourceRepo`, `SourceDeleteRepo`, `AliasRepo`, `api/Source*`, `files/Frontmatter`, `Export`, `core/PassageFinder` | 2 400 |
| `findings` | finding (claim, place, period, confidence), finding places | `findings`, `finding_places` | `Handlers.findings*`, `api/Finding*`, parts of `SourceRepo` | 130 + share |
| `citations` | typed edges, unresolved edges, refs, follow-ups, assessments, retractions, neighbours, graph, contradictions | `edges`, `unresolved_edges`, `assessments`, `retraction_watch` | `Handlers.neighbors/graph/contradictions/resolveEdges`, `AcquireHandlers.refs/followups/assess`, `api/Edge*`, `Neighbor*`, `Graph*` | 300 + share |
| `labels` | tags, topics, regions, authors as vocabularies; rename across the corpus; facets | — (columns of `sources`) | `Handlers.facets`, `rename_label`, `api/Label*`, `Facet*` | 400 |
| `search` | the filter, search, aggregate, histogram, geo-when, snippets | — (reads) | `db/Search`, `FilterSql`, `ListSql`, `Keyset`, `AggregateRepo`, `api/Search*`, `Filter*`, `core/Highlight` | 2 500 |
| `query` | read-only SQL and the schema listing | — | `db/QueryRepo`, `OpsHandlers.query/schemaTables`, `ReadOnlyPool` | 300 |
| `articles` | article, versions, links and backlinks, coverage filter and reasons, critique pairing, cited sources, article pages | `articles`, `article_aliases`, `article_links`, `article_versions`, `article_critique_reasons` | `Handlers` (article*), `db/ArticleRepo`, `CoverageRepo`, `CritiqueRepo`, `api/Article*`, `Coverage*`, `Critique*`, `server/Pages`, `PageBuilder`, `core/TextAnchor` | 3 600 |
| `articles.review` | review state, panel ledger, lessons | `review_ledgers` | `core/ReviewArticle` (types and rules), `ReviewLedger`, `ArticleReviewStatus`, `db/ArticleReviewRepo`, `ReviewLedgerRepo` | 3 700 |
| `articles.lint` | prose lint, format findings, fixes | — | `server/MarkdownLint`, `api/Format*` | 1 500 |
| `lint` | corpus health rules and safe repairs | — | `db/LintRepo`, `api/Lint`, `OpsHandlers.lint*` | 900 |
| `places` | gazetteer place, names, overrides, relations, footprints, hierarchy, geocoder, site types, point areas, land, place review, gazetteer importers | `places`, `place_*` (8), `gazetteer_sources`, `land` | `db/PlaceRepo` (1 710), `GazetteerRepo`, `PlaceOverrideRepo`, `PlaceReviewRepo`, `PointAreaRepo`, `Geocoder`, `TreeRepo`, `LandRepo`, `server/importers/*` (but ChronOntology), `api/Place*`, `Gaz*`, `Geocode`, `Lpf`, `SiteTypes`, `core/PlaceReview`, `PointAreas` | 7 400 |
| `periods` | named period, presets, PeriodO and ChronOntology import | `periods`, `seed_versions` | `db/PeriodRepo`, `PeriodSql`, `importers/ChronOntologyImporter`, `PeriodOFetch`, `api/Period*`, `PeriodO` | 600 |
| `events` | event, datings, edges, participants, aliases, catalogue import (host loop), leads, link candidates and checks | `events`, `event_*` (8) | `events/EventCatalogHandlers`, `db/EventRepo`, `EventCatalogRepo`, `api/Event*`, `core/CatalogMerge`, `EventMatcher` | 4 700 |
| `attestations` | a source's attestation of a finding, event, dating, place or edge | `attestations` | `db/AttestationRepo`, `api/Attestation*`, `Handlers` (attestation*) | 260 |
| `corrections` | a correction of an event or place | `corrections` | `db/CorrectionRepo`, `api/Correction*` | 270 |
| `media` | asset, asset link, media link and check, licence, credit, import host loop, figures, covers, IIIF, thumbnails | `assets`, `asset_links`, `media_links`, `media_checks` | `media/*` (host side), `db/MediaRepo`, `AssetRepo`, `AssetMetaRepo`, `AssetLinkRepo`, `Figures`, `server/IiifHandler`, `api/Media*`, `Asset*`, `DataCredits`, `core/MediaMatch`, `MediaLicence`, `FigurePick` | 4 600 after the plugins leave |
| `maptiles` | vector tiles of findings and places, undated sites, tile cache | `site_undated_build`, `site_undated_cells`, `map_changes` | `server/maptiles`, `MapTiles`, `MapTileService`, `TileCache`, `db/TileRepo`, `UndatedSiteRepo`, `core/maptiles`, `Tile*` | 1 200 |
| `basemap` | the base map's download, PMTiles, terrain, labels, GEBCO | — (files) | `server/basemap/*`, `api/Basemap*` | 5 200 |
| `ingest` | OCR converter, spool, ingest job, originals store, seed import, JATS conversion, chunking | — (writes through `sources`) | `IngestHandlers`, `ConverterClient`, `HttpConverterClient`, `ConverterState`, `JatsConverter`, `StoredOriginals`, `acquire/Spool`, `SkeletonIngest`, `QueuedIngest`, `db/Importer`, `Originals`, `core/Chunker` | 2 200 |
| `acquisition` | discovery (OpenAlex, Crossref, Wikipedia, Semantic Scholar), candidates, watches and sweeps, acquire checks and recheck, the fetch chain (host side), archive.org import, upstream quota, retractions update | `source_candidates`, `candidate_watches`, `acquire_checks`, `upstream_quota` | `acquire/*` (host side), `AcquireHandlers`, `agents/AcquireRecheckService`, `db/AcquireRepo`, `AcquireCheckRepo`, `CandidateRepo`, `UpstreamQuotaRepo`, `core/AcquireMatch`, `AcquireRecheck`, `IngestBacklog`, `CoreQuota` | 4 100 after the plugins leave |
| `calibre` | Calibre gaps, metadata for a push, the push record | — (columns of `sources`) | `AcquireHandlers.calibre*`, `api/Calibre*`, CLI `CalibreCmd` | 900 |
| `queues` | the acquire, tasks and log lists | `queue_items` | `OpsHandlers.queue*`, `db/QueueRepo`, `api/Queue` | 560 |
| `goals` | goal, links, schedules, focus, GOALS.md export | `goals`, `goal_aliases`, `goal_links`, `goal_schedules`, `goal_focus` | `Handlers` (goal*), `db/GoalRepo`, `GoalLinkRepo`, `FocusRepo`, `api/Goal*`, `core/GoalFocus`, `PlanGoal`, `WatchSchedules` | 2 200 |
| `agents` | runs, steps, messages, traces, leases, agent state, model chains, allowances, prices, plan pools and quotas, providers registry use, toolbox, definitions generator | `agent_*` (8), `plan_pools`, `plan_quota_*`, `agent_state` | `agents/*` (host side), `AgentsHandlers`, `db/AgentRepo`, `AgentStateRepo`, `ModelSettingsRepo`, `PlanQuotaRepo`, `api/Agents`, `PlanQuota`, `ModelChains`, `core/CompareRuns` | 14 000 |
| `agents.pipelines` | enrich-source, compile-article, review-article, propose-articles, plan-goal, currency check, acquisition sweep, infer-filter, hint effect | — | `agents/*Pipeline`, `core/EnrichSource`, `CompileArticle`, `PlanArticles`, `AgentProposals`, `AgentVerify`, `FilterInference` | 6 100 |
| `approvals` | approval, auto-approval, digest | `approvals` | `agents/Approvals`, `AutoApprovals`, `ApprovalDigestService`, `api/ApprovalDigest*` | 770 |
| `managers` | manager, hints, items, events, supervisor, seed, drain | `agent_managers`, `manager_*` | `agents/ManagerSupervisor`, `ManagersHandlers`, `db/ManagerRepo`, `core/ManagerPolicy`, `api/Managers` | 5 500 |
| `autopilot` | the autopilot's verdicts and runs | `agent_autopilot` | `agents/AutopilotService`, `db/AutopilotRepo`, `core/AutopilotPolicy`, `AutoEnrich` | 1 400 |
| `scheduler` | cron, scheduling policy, watch schedules, read schedules | — (reads `goal_schedules`, `jobs`) | `agents/SchedulerService`, `core/SchedulerPolicy`, `Scheduling`, `Cron`, `api/AgentScheduler*` | 1 700 |
| `jobs` | job, background runner, job control, stops | `jobs` | `BackgroundJob`, `Jobs`, `domain/jobs/*` (pilot), `db/JobRepo`, `api/Job*` | 860 |
| `activity` | the one activity list | — (reads) | `domain/activity/*` (pilot) | 450 |
| `backup` | `pg_dump`, restore, backups list, housekeeping | — | `BackupService`, `BackupJobs`, `db/BackupRepo`, `api/Backup*`, `Restore*` | 700 |
| `bundle` | `.hmx` export and import, bundle plan, deletions carried between databases | `tombstones`, `alias_tombstones` (written by triggers of every carried table) | `BundleHandlers`, `db/BundleRepo`, `BundleImport`, `BundleExport`, `core/BundlePlan`, `files/Bundle*` | 4 000 |
| `merge` | merge session, decisions, ledger, plan, apply, issues, 3-way text merge, uid redirects | `merges`, `merge_decisions`, `merge_ledger`, `merge_sessions`, `uid_aliases` | `MergeApplyJobs`, `db/MergeRepo`, `MergeState`, `core/MergePlanner` (2 425), `MergeFold`, `MergeText`, `SourceMatcher`, `Diff3`, `api/Merge*`, `Issue*` | 5 400 |
| `maintenance` | vacuum, reindex, storage report, the maintenance lock, PostgreSQL relocation | — | `VacuumJobs`, `Maintenance`, `StorageProbe`, `StorageCache`, `PgRelocation`, `db/VacuumRepo`, `StatsRepo`, `api/Storage*`, `Vacuum*` | 900 |
| `personal` | bookmarks, notes, read marks (pilot) | `bookmarks`, `notes`, `read_marks` | `domain/personal/*`, `api/personal/*` | 1 700 |
| `settings` | settings file edit, reload, restart (pilot) | — (the settings file) | `domain/settings/*`, `config/edit/*`, `ConfigViews`, `HotConfig` | 2 000 |
| `plugins` | the plugin generation (host glue), legacy settings, plugin set, relink tool | — | `server/plugins/*`, `api/Plugin*` | 1 100 |
| `instance` | the database's identity (origin uid, label), about, health, version, self-check, UI counters and notices, UI config | `instance`, `ui_seen` | `Handlers.about/health/version/uiCounters/uiConfig`, `db/UiRepo`, `TimeZoneCheck`, `api/About`, `Health`, `Ui*` | 850 |

Not features: the composition root and adapters stay in `com.histmersion.server`. That covers `Main`, `Bootstrap`,
the wiring (today `Deps`), the HTTP server (`HttpApi` reduced to the concatenation of the feature routes),
`McpTools` (likewise), `AuthHandler`, the hot graph (`server/hot`), `Supervisor` and the static assets.

### 3.2 Supporting areas (`com.histmersion.util.<area>`)

| area | holds | module |
|---|---|---|
| `util.database` | the pool, `Transactor`, Flyway runner, generic column codecs, `Jsonb`, `Keyset`, `Param`, `Sql`, `Vocab` | `db` |
| `util.json` | generic newtype codecs and schemas, `WireEnum`, `StrictJson`, `StrictJsonSchema`, JSON cursor helpers (`Read`) | `api` (JVM + JS) |
| `util.http` | the outbound client, policy, politeness, TLS, `Url` | `plugin-host` / server |
| `util.markdown` | flexmark flavour, Markdown → HTML, HTML atoms | server / `api` |
| `util.text` | `FoldedText` (Latin folding), `Plural`, `Csv`, `Diff3` | `api` / `core` |
| `util.images` | PNG, VP8L, Terrarium PNG encoders | server |
| `util.tiles` | MVT, PMTiles, the vector-tile writer | server / `core` |
| `util.files` | `SafePath`, directory size, the tar container | `files` |

Per-file assignments: [`files.md`](research-domain-model/files.md). `cli` and `web` keep their own trees
(`cli.<feature>`, `web.<feature>`; `web` is already mostly so) and are touched only at their edges (slice 12).

## 4. Newtype catalogue

### 4.1 Rules

- **Where a newtype lives.** It goes in `values` (JVM + JS, Iron only) when it crosses the wire or more than one
  feature uses it. It goes in `domain.<feature>` when only one feature's logic uses it. It goes in
  `config.<feature>` when it is a setting with no meaning outside configuration. `plugin-api` keeps its own
  copies (`Year`, `Doi`, …; SPI 2), which chimney-iron converts because the names and constraints are the same.
- **Shape.** Rule 1's `type X = X.T; object X extends RefinedType[Base, C]`. The constraint is `Pure` unless the
  code already rejects values today, so no valid input is refused that is accepted now. Where a value has a rule,
  that rule is the one the code already enforces (taken from the current regexes and checks). Where two rules
  disagree today, the stricter one consistent with live data wins and the change is listed (`Citekeys.valid` vs
  `Citekey`, three slug regexes).
- **Smart constructors.** `X.either(raw)` comes from Iron. `X.parse(raw): Either[String, X]` exists only where
  today's error message must be kept (`Citekey.parse`).
- **Closed vocabularies are enums, not newtypes.** 32 objects of `val X = "…"` constants (`GoalStatus`,
  `MediaRole`, `AttestationTarget`, `CorrectionTarget`, `Provenance`, `AssetKind`, `EventTier`, …) become
  `enum X(val wire: String)` with the existing `WireEnumValues` spelling table, so the strings on the wire and in
  columns stay the same.

### 4.2 Identifiers

"replaces" counts parameters and fields declared with these names (main code, every module; in brackets: without
`web`). The method and the full table are in `primitives.md`.

| newtype | base | constraint | lives in | replaces | notes |
|---|---|---|---|---:|---|
| `Citekey` | String | today's `CitekeyRule` | values (exists) | 426 (393) | `citekey`, `ck`, `citekeys`; `Citekeys.valid` (`[a-z][a-z0-9_]*`, used by the merge planner) goes; the merge planner's message changes accordingly (listed in slice 5) |
| `Slug` | String | `[a-z0-9][a-z0-9_.-]{0,120}` (today's `Articles.slugOf`) | values | 241 (198) | the article slug; `ArticleLinks` and `MergePlanner` use the same rule instead of their unbounded copies |
| `GoalSlug` | String | same rule as `Slug` (Q10) | values | 159 (143) | `goal`, `goals`; separate type so a goal slug never passes as an article slug |
| `SourceUid`, `ArticleUid`, `EventUid`, `GoalUid`, `AssetUid` | UUID | Pure | values | 157 | `uid`, `sourceUid`, `origin` (the merge's stable ids); today `UUID` and `String` both |
| `PlaceId` | String | `[a-z][a-z0-9]*_.+` | values | 89 | `placeId`, `place`, `parentId`; live ids are `<authority>_<id>` |
| `EventId` | String | `Slug`'s rule | values | 34 | |
| `JobId` | UUID-text String | UUID pattern | values | 40 | `jobId`, `job` |
| `ManagerId`, `RunId`, `StepId`, `ApprovalId` | String / Long (as stored) | Pure | domain.managers / domain.agents / domain.approvals | 53 / 48 / – / – | |
| `QueueItemId` | Int | `Positive` | domain.queues | 21 | the worked example (§6) |
| `AssetId`, `AssetName` | Long / String | Pure | domain.media | 4 + share of `id: String` | |
| `BookmarkId`, `NoteId`, `ItemKey`, `UserId` | | | exist (personal pilot) | — | kept |
| `WikidataId` | String | exists | values | 18 | `wikidata`, `qid` |
| `Doi` | String | exists | values | 129 (122) | |
| `ArxivId`, `Pmid`, `Pmcid`, `Isbn`, `ArchiveId` | String | Pure (today's checks where any) | values | share of 30 | `WorkRef`, `WorkKey` fields |
| `PluginId` | String | exists in cli; one in `values` | values | share of `id: String` | reserved names on its companion |
| `ModelRef`, `ProviderName`, `ModelId` | String | `ModelRef` exists (parsed); others Pure | domain.agents | 49 + 60 | |
| `UserId` | String | exists | values (moved from personal) | — | the multi-user design keys rows on it |

`id: String` (521 uses) is not counted under any one type: it is a different identifier in each feature, and each
slice types the uses in its own feature.

### 4.3 Values

| newtype | base | constraint | lives in | replaces | notes |
|---|---|---|---|---:|---|
| `Year` | Int | Pure (exists) | values | 450 (398) | `year`, `periodStart`/`periodEnd`, `from`/`to`, `start`/`end`, `asOf`. Columns stay `int8range`/`bigint`; the DB codec widens. `DeepTime.year(Long)` becomes `Year.fromLong` |
| `Latitude`, `Longitude` | Double | exist | values | 186 (160) | also `BBox`'s four edges |
| `Title`, `Summary`, `MarkdownBody`, `Claim`, `Venue`, `AuthorName` | String | Pure | values | 227 + 215 + 22 + 22 + 80 | one type per meaning, not one "Text" |
| `Tag`, `Topic`, `Region` | String | Pure | values | 191 | label vocabularies; `rename_label` works on them |
| `Licence` | String | Pure | values | 42 | stated licence id or URL; the classified licence is an enum in `domain.media` |
| `Sha256` | String | 64 lower-case hex | values | 39 | |
| `Url` | String | Pure (absolute-URL check where today's code checks) | values | 133 | `ServiceUrl` (config) becomes this |
| `CronExpression` | String | Pure; parsed by `Cron` | domain.scheduler | 26 | `Cron`'s functions go on it |
| `PageSize`, `Offset` | Int | `Positive` / `Not[Negative]` | values | 268 + 70 | `limit`, `max`, `pageSize`, `offset`; the default and cap stay where they are configured |
| `Timestamp` | Instant | — | values | 175 ISO strings + 71 epoch-millis `Long`s | the domain holds `Instant`; the wire keeps its ISO text through the codec (`createdAt`, `updatedAt`, `since`, `before`); epoch-millis fields keep their number on the wire |
| `Seconds`, `Minutes`, `Hours`, `Days`, `Percent`, `ItemCount`, `TokenCount`, `CitationCount`, `MatchScore`, `ApiKey`, `CommaList`, `RefreshIntervalMinutes` | | Pure (step 2) | **moved from config to values** | 33 (`timeoutSeconds` etc.) beyond config | their `ConfigReader`s move from the companions into `config`'s readers file (rule 3) |
| `ByteCount` | Long | `Not[Negative]` | values | 36 | `bytes`, `size` |
| `Zoom` | Int / Double | Pure | values | 98 | `z`, `maxZoom`, `zoom` |
| `Kilometres` | Double | `Not[Negative]` | values | 43 | `toleranceKm`, `radiusKm` |
| `CharCount` | Int | `Positive` | domain.ingest | 22 | `chunkChars`, `maxChars` |
| `ReviewRound`, `GraphDepth`, `Priority` | Int | Pure / `Positive` | their features | 20 / 40 / 20 | |
| `ImageSize` | (Int, Int) case class | — | domain.media | — | `object ImageSize` becomes the type |
| `FoldedText`, `Snippet` | String | — | util.text / domain.search | — | from `LatinFold`, `Highlight` |

Booleans stay `Boolean` with named parameters (Q8). The 13 config newtypes from step 2 keep their names.

### 4.4 Wire format and codecs

What must stay byte-identical, and how:

| surface | stays identical because | checked by |
|---|---|---|
| JSON (REST, MCP results) | a newtype's `Encoder` is its base type's encoder; `Decoder` is the base decoder followed by the constraint | the snapshot files (§9.1); round-trip tests per newtype |
| OpenAPI and MCP input schemas | `Schema[X]` is the base schema `.as[X]`: same `type`, no name, no validator, so no new `components` entry | OpenAPI and `tools/list` snapshots |
| agent toolbox schemas | derived from the same `Schema` | toolbox snapshot |
| DB columns | `DbCodec[X]` maps the base codec. Reads of a constrained type use the checked constructor and fail with the column and value (Q11); live data has 0 violations | DB suites; the slice's live check (`histmersion query`, read-only) |
| HOCON keys and env overrides | readers depend on field names only; package moves do not rename keys | `ConfigSuite`, the settings view snapshot |
| CLI flags | decline `Argument[X]` from the base argument | the CLI help snapshot (`tools/gen-cli-docs.sh` output) |

One given per library derives every newtype's instance from its base. Iron 3.4.1 exposes `RefinedType.Mirror[T]`
with `BaseType`, `ConstraintType` and `IronType` (checked in the 3.4.1 jar). iron-circe uses the same shape:

```scala
package com.histmersion.util.json        // api module, JVM + JS

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.{:|, Constraint, RefinedType}
import io.github.iltotore.iron.*
import sttp.tapir.Schema

object NewtypeCodecs:
  inline given encoder[T](using m: RefinedType.Mirror[T], base: Encoder[m.BaseType]): Encoder[T] =
    base.asInstanceOf[Encoder[T]]                                    // opaque: T erases to BaseType
  inline given decoder[T](using m: RefinedType.Mirror[T], base: Decoder[m.BaseType])(using
      inline c: Constraint[m.BaseType, m.ConstraintType]): Decoder[T] =
    base.emap(_.refineEither[m.ConstraintType]).asInstanceOf[Decoder[T]]
  inline given schema[T](using m: RefinedType.Mirror[T], base: Schema[m.BaseType]): Schema[T] =
    base.as[T]
```

The same shape covers `DbCodec` (`util.database.NewtypeColumns`), `ConfigReader` (config's lenient base readers,
so `"30"` from an environment variable still reads as an `Int`), decline's `Argument`, and Chimney (already
chimney-iron). These are casts over opaque types, not reflection, so native-image needs no metadata for them. Hand
instances stay only where a message must be kept (`Citekey.parse`, `BookmarkId.parse`); the snapshot test catches
any drift. The exact `Constraint` summoning is confirmed in slice 1. If it does not compile in this form, the
fallback is one explicit line per newtype on the same pattern; the result on the wire is the same either way.

**Where a raw value is parsed (Q1).** Today the endpoints take raw strings and the handlers produce the error text
(`invalid citekey 'X' — lower-case, …`). Tapir's default decode failure answers in plain text instead. To keep
error responses identical, **output records carry the constrained newtypes, and input records keep the raw
representation as a `Pure` newtype** (`CitekeyInput`, …). The controller is the one parse boundary, as rule 2
asks. Moving parsing into decoders, with a decode-failure handler that answers `ApiError(message)`, is a later,
visible change for the user to decide on.

## 5. Operations onto types (#7903, #7908)

### 5.1 Rules

| rule | a top-level object … | goes to | count |
|---|---|---|---:|
| O1 | holds functions about one type, often named as its plural (`ApprovalModes`, `Citekeys`, `Years`, `JobActions`) | that type's companion, or an extension in the module that may know it (rule 3) | 160 |
| O2 | holds `val X = "…"` constants of a closed vocabulary (`GoalStatus`, `MediaRole`, `AttestationTarget`) | an `enum` with `wire` strings | 32 |
| O3 | is a namespace for a feature's types (`api.Agents`, `api.Managers`, `core.ReviewArticle` with 36 nested types) | dissolves: the types become top-level in the feature package; its functions follow O1 | 51 |
| O4 | parses one upstream's JSON (`MetMuseum`, `Europeana`, `AcquireParse`, `PeriodO`, `Lpf`) | the companion of the record it produces (`MetObject.fromJson`), introduced where missing | in O1/O6 |
| O5 | holds a subdomain's rules over a "facts" record (`GoalFocus.Facts`, `AutopilotPolicy.*Facts`, `SchedulerPolicy.Scored`) | methods of that record, renamed to say what it is (`FocusFacts`) | in O3 |
| O6 | works on a value that has a meaning but no type (`Chunker`, `LatinFold`, `Highlight`, `SafePath`, `Frontmatter`, `ImageSize`, `ServerArgs`) | a new type that carries the functions | 37 |
| O7 | is a built-in plugin's code | its plugin module (§8) | 29 |

| rule | stays standalone because it is … | examples | count |
|---|---|---|---:|
| S1 | an entry point (now a `@main def`, §5.5) | — | — |
| S2 | a repository: the feature's store, package-private | 57 `*Repo` objects | 57 |
| S3 | a group of endpoint values, a CLI command group, a web view | `*Api` → `*Endpoints`, `*Cmd`, Laminar views | 11 + 28 + 166 |
| S4 | a given holder, a generic codec helper or a database primitive | `ValueCodecs`, `VocabularyCodecs`, `db/Codecs`, `Keyset`, `Param` | 11 |
| S5 | a codec of an external format with no value type of ours | `Png`, `Vp8l`, `TerrariumPng`, `Mvt`, `PmTiles`, `VectorTileWriter`, `Csv`, `Diff3`, `HtmlRender` | in 47 |
| S6 | a facade over several types that names the composition | `PluginHost`, `LegacyPlugins`, `ConfigLayers`, `SpiValues` | in 47 |

Totals: 645 objects; 320 stay. Every row is in [`objects.md`](research-domain-model/objects.md).

### 5.2 Notable cases

| object | today | destination | why it matters |
|---|---|---|---|
| `ApprovalModes` | `configured(raw) = ApprovalMode.parse(raw).getOrElse(Manual)` | `ApprovalMode.configured` | the user's own example |
| `Citekeys.valid` | `^[a-z][a-z0-9_]*$` | removed; `Citekey.parse` | disagrees with `Citekey` (no `.`/`-`): two rules for one type |
| `DeepTime` | `year(Long)`, `span(Long, Long)` | `Year.fromLong`, `Period.span` | about `Year`/`Period` |
| `api.Write` | error codes as `Int` constants and input checks | `enum WriteCode`; checks on `SourcePatch`, `FindingInput` | the codes are a vocabulary |
| `api.Wording` | 28 sentences about several types | each to the companion of the type it words | 235 lines of free functions |
| `api.Paging` | `Page` nested plus page arithmetic | `Page` top-level; arithmetic on `Page`/`PageSize` | cross-feature shared type |
| `core.MergePlanner` | 2 425 lines, 9 nested types, 67 functions | `domain.merge.plan`: `MergePlan.of`, `LedgerEntry`, `Row` companions | the largest object |
| `core.ReviewArticle` | 2 013 lines, 36 nested types | `domain.articles.review` types, rules on `Reviewer`, `Verdict`, `ReviewFinding` | the second largest |
| `server.MarkdownLint` | 1 336 lines | `domain.articles.lint`: `FormatFinding`, `Edit`, `LintContext` | prose lint is the article feature's |
| `*Markdown` (28) | one renderer object per area | a `MarkdownRender[A]` instance on the companion of the wire type it renders (Q14) | "a function about a type" |
| `Chunker` | `chunks(body)` | `Chunk.split(body: MarkdownBody, size: CharCount)` | O6 |
| `UserPaths` (config) | reads `user.home` | a `UserPaths` value built once in `Main` from the environment | rule 12 |

### 5.3 What is not an O1 move

An extension method, not a companion method, is used when the type lives in a module that must not know the
function's dependencies. Example: `ConfidenceStyle` styles `values.Confidence` for the UI, so it becomes an
extension in `api.ui`; `values` gets no UI code. A function that needs a service (a database read, an HTTP call)
is a service method, not a companion method.

### 5.4 Proposed AGENTS.md rule 14

> **14. No free-standing helper objects.** A function about a type is a method of the type or a member of its
> companion — an extension where the type's module must not know the function's dependencies. A closed set of
> strings is an `enum`. A top-level object without a same-named type is one of: a repository, a group of endpoints,
> CLI commands or views, a holder of givens, a codec of an external format, or a facade that names a composition.
> Entry points are `@main def`s.

### 5.5 Entry points (#7908)

| today | becomes | references to update |
|---|---|---|
| `com.histmersion.server.Main` (`object`, 350 lines: `main` + composition helpers) | `@main def histmersionServer(args: String*)` in `server/Main.scala`; the helpers go to `Composition` (the wiring) and `ServerArgs` (O6) | `build.sbt` `Compile / mainClass`, relink kit `main-class`, `PluginTool` default, `scripts/bundle/tests/{relink,kit-scripts}-test.sh`, `scripts/docs/screenshots.sh`, `PluginToolSuite`, `PluginKitSuite`, `docs/research-plugins.md` |
| `com.histmersion.cli.Main` | `@main def histmersionCli(args: String*)` | `build.sbt`, `tools/gen-cli-docs.sh`, `MainInitSuite`, the plugin template's docs |
| `com.histmersion.web.Main` (Scala.js) | `@main def histmersionWeb()`; `scalaJSUseMainModuleInitializer` stays, `Compile / mainClass` set explicitly | `build.sbt` |
| `server.plugins.PluginTool` | `@main def pluginTool(args: String*)` | `scripts/bundle/relink.sh`, `server.sh`, `server-jvm.sh` |
| `server.agents.GeneratePlugin` | `@main def generatePlugin(root: String)` | `justfile` (`just plugin`) |
| `pluginresolver.PluginResolver` | `@main def pluginResolver(args: String*)` | `scripts/bundle/kit-common.sh` |
| test tools `BasemapLabelsTool`, `BasemapExtractTool`, `PromptBudget` | `@main def`s | their scaladoc usage lines, `scripts/agents/claude-overhead.sh` |

Scala 3 names the generated class after the method. Renaming the classes is safe across upgrades because a relink
always uses the `main-class` file of the kit it came with (Q2). `api.generateApiIndex` is already a `@main def`
and stays.

## 6. Service shape

### 6.1 The template

| piece | where | visibility | owns |
|---|---|---|---|
| domain types, pure rules | `domain.<f>` (core module when other modules need them, else server) | public or `private[<f>]` | invariants (smart constructors), pure decisions |
| `<F>Service` trait | `domain.<f>` (server) | public | the feature's operations in domain types |
| `<F>Service.Implementation` | companion | `private final class` | transactions, calls of its repository and of other services |
| `<F>Service.InMemory` | companion | `final class` | the same behaviour over `AtomicReference`s |
| `<F>Repo` | `domain.<f>` (server) | `private[<f>] object` | SQL; every method takes `(using DbCon)`; never opens a transaction |
| `<F>Controllers` | `domain.<f>` (server) | public `final class` | parse raw inputs into domain values (the boundary), maintenance guard, call services, convert to wire records (Chimney) |
| `<F>Routes`, `<F>Tools` | `domain.<f>` (server) | public | bind the feature's Tapir endpoints and Chimp tools to controller methods; nothing else |
| `<F>Endpoints`, wire records, renderers | `api.<f>` | public | the contract |

`HttpApi` and `McpTools` become lists of the features' routes and tools, **in today's order**. Route matching and
`tools/list` both depend on that order.

Transactions go through one small type in `util.database`. It wraps Magnum's `connect`/`transact` over the pool
and keeps the read-only query pool apart:

```scala
trait Transactor:
  def read[A](work: DbCon ?=> A): A           // connect: one connection, autocommit, no transaction
  def transact[A](work: DbCon ?=> A): A       // one transaction; rolled back on an exception
```

Globals (rule 12) are passed in. The composition root builds `java.time.Clock` (Q3), `Environment` (the
environment map read once in `Main`) and `UserPaths`, and passes them to whatever needs them.
`Instant.now()` becomes `Instant.now(clock)`, `Thread.sleep` becomes a `Sleeper` passed in, and `UUID.randomUUID`
becomes an `IdSource`. Each slice removes the global reads of its feature (the counts are in §2).

### 6.2 Worked example: the queues

Today (`OpsHandlers.queue*` + `db/QueueRepo` + `api/Queue`): the repository takes a `DataSource` and opens its own
transactions; it returns `ApiError` (an api type) from `patch`/`delete`. The handler reads `LocalDate.now(UTC)` (a
global) to date a log item. Ids are `Int`, the list is a `String` parsed in two places, statuses are `String`
constants. The pure rules (`Queue.applyPatch`, `statusOf`, `render`, `parse`) live in an api namespace object.

Target:

```scala
package com.histmersion.domain.queues

enum QueueList(val wire: String, val file: String, val checkboxes: Boolean):     // was api.Queue.Kind
  case Acquire extends QueueList("acquire", "ACQUIRE.md", true)
  case Tasks   extends QueueList("tasks", "TASKS.md", true)
  case Log     extends QueueList("log", "LOG.md", false)

enum QueueStatus(val wire: String):                                              // was "open" | "done" | "note"
  case Open extends QueueStatus("open")
  case Done extends QueueStatus("done")
  case Note extends QueueStatus("note")

final case class QueueItem(
    id: QueueItemId, list: QueueList, text: QueueText, topic: Option[QueueTopic], status: QueueStatus,
    reason: Option[QueueReason], createdAt: Instant, doneAt: Option[Instant], goal: Option[GoalSlug],
    createdBy: Option[JobId], closedBy: Option[JobId]
):
  def patched(p: QueueItemPatch): QueueItem = …                                  // was Queue.applyPatch

enum QueueScope:
  case Open, All
  case ForGoal(goal: GoalSlug, scope: QueueScope)
  case ForJob(job: JobId)

trait QueueService:
  def list(list: QueueList, scope: QueueScope): List[QueueItem]
  def add(list: QueueList, item: NewQueueItem): QueueItem
  def patch(id: QueueItemId, patch: QueueItemPatch): Either[QueueError, QueueItem]
  def delete(id: QueueItemId): Either[QueueError, QueueItem]
  def replace(list: QueueList, items: List[NewQueueItem], mode: ImportMode): ImportCounts

object QueueService:
  def apply(db: Transactor): QueueService = Implementation(db)

  private final class Implementation(db: Transactor) extends QueueService:
    def list(list: QueueList, scope: QueueScope) = db.read(QueueRepo.list(list, scope))
    def add(list: QueueList, item: NewQueueItem) = db.transact(QueueRepo.insert(list, item))
    def patch(id: QueueItemId, p: QueueItemPatch) = db.transact:      // read, decide, write: one transaction
      QueueRepo.get(id).toRight(QueueError.NotFound(id)).map: current =>
        QueueRepo.update(current.patched(p))
        QueueRepo.get(id).get                                          // done_at as the database stamped it
    def delete(id: QueueItemId) = db.transact:
      QueueRepo.get(id).toRight(QueueError.NotFound(id)).map(item => { QueueRepo.delete(id); item })
    def replace(list: QueueList, items: List[NewQueueItem], mode: ImportMode) = db.transact:
      val removed = if mode == ImportMode.Replace then QueueRepo.deleteList(list) else 0
      items.foreach(QueueRepo.insert(list, _))
      ImportCounts(items.size, removed)

  /** Shares `PlanningState` with GoalService.InMemory: a goal's queue items follow its links, as in SQL.
    * `clock` stands in for the database's now() (created_at, done_at). */
  final class InMemory(state: AtomicReference[PlanningState], clock: Clock) extends QueueService:
    def add(list: QueueList, item: NewQueueItem) =
      state.updateAndGet(_.queueAdded(list, item, Instant.now(clock))).queue.last
    def list(list: QueueList, scope: QueueScope) = state.get.queueListed(list, scope)   // same order: by id
    …

private[queues] object QueueRepo:
  def get(id: QueueItemId)(using DbCon): Option[QueueItem] = …
  def list(list: QueueList, scope: QueueScope)(using DbCon): List[QueueItem] = …  // SQL unchanged
  def insert(list: QueueList, item: NewQueueItem)(using DbCon): QueueItem = …     // coalesce(created_at, now())
  def update(item: QueueItem)(using DbCon): Unit = …                // done_at = coalesce(done_at, now())
  def delete(id: QueueItemId)(using DbCon): Unit = …
  def deleteList(list: QueueList)(using DbCon): Int = …

final class QueueControllers(queues: QueueService, maintenance: MaintenanceLock, clock: Clock):
  def add(list: String, in: QueueAdd): Either[ApiError, api.queues.QueueItemView] =
    maintenance.guard("queue/add"):
      for
        l    <- QueueList.parse(list).left.map(ApiError(_))
        item <- NewQueueItem.from(l, in, LocalDate.now(clock)).left.map(ApiError(_))  // the log's dated topic
      yield queues.add(l, item).transformInto[api.queues.QueueItemView]
  …
```

The SQL text does not change. Two behaviours the snapshots cannot see are kept on purpose and covered by the
contract suite. The database still stamps `created_at` and `done_at` (`now()`), and `InMemory` stamps them from its
clock. A log item's topic is still today's UTC date, but the controller now takes the date from the clock passed in
instead of `LocalDate.now`.

### 6.3 Tests: InMemory is proven, not trusted

```scala
abstract class QueueServiceContract(make: Clock => (QueueService, GoalService)) extends munit.FunSuite:
  test("a goal's tasks include the items linked to it, open ones only by default"):
    val (queues, goals) = make(fixedClock)
    val g = goals.put(NewGoal(GoalSlug("sea-peoples"), …))
    val t = queues.add(QueueList.Tasks, NewQueueItem(QueueText("read Cline 2014")))
    goals.link(g.slug, GoalLink.QueueItem(t.id))
    assertEquals(queues.list(QueueList.Tasks, QueueScope.ForGoal(g.slug, QueueScope.Open)), List(t))

class QueueServiceInMemorySuite extends QueueServiceContract(clock =>
  val state = AtomicReference(PlanningState.empty)
  (QueueService.InMemory(state, clock), GoalService.InMemory(state, clock)))

class QueueServiceDbSuite extends QueueServiceContract(_ =>              // gated on HISTMERSION_TEST_DB_URL
  val db = TestDb.transactor()
  (QueueService(db), GoalService(db)))
```

Every service gets one contract suite run against both implementations. That is what makes "behaves exactly like
the database" a tested fact, as #7490 asks. Tests seed an `InMemory` by calling its own service methods
(`goals.put`, `queues.add`). There are no seeding back doors and no mocking library. Controllers are unit-tested
over `InMemory` services with no server and no database, as in `QueueControllersSuite`, which checks the dated log
topic with `Clock.fixed`.

Where real services share data, the `InMemory` ones share one state record per group of features, held in one
`AtomicReference` and updated through lenses (rule 8):

| state record | shared by |
|---|---|
| `CorpusState` | sources, findings, citations, labels, attestations, corrections |
| `PlanningState` | goals, queues, approvals, managers |
| `RunState` | agents, jobs, activity |
| `PlaceState` | places, periods, events |
| `PersonalState` | personal (exists) |

### 6.4 Controllers and the caller

Each controller method takes the request's caller as a parameter (`Caller(user: UserId, role: Role)`). Until
`docs/research-multi-user.md` is built, it is always `Caller.Local`. This is the one place the multi-user work will
change, as that design already assumes for `PersonalControllers`.

## 7. Module layout after the change

### 7.1 sbt projects

| project | holds (packages) | depends on | change |
|---|---|---|---|
| `values` (JVM + JS) | `values`: identifiers, values, vocabulary enums, generic quantities | iron | grows (§4) |
| `api` (JVM + JS) | `api` root: shared wire types (`ApiError`, `WriteCode`, `Page`, `Filter`, `BBox`, `TimeSpan`, Markdown primitives); `api.<feature>`: records, `<F>Endpoints`, renderers; `util.json` | values | `Api.all` = the features' lists in today's order |
| `config` | `config` root (`AppConfig`, loading, layers); `config.<feature>` settings records | values | flat package split by feature; step 2's quantities move to values |
| `files` | `files` (bundle format, notes, merge file, plugin set), `util.files` | api | unchanged in scope |
| `core` (JVM) | `domain` (shared domain types) and the pure part of `domain.<feature>` | values, files (and through `files` api, for the bundle records) | its logic stops using api's wire records, feature by feature (Q9) |
| `db` | `util.database` only: pool, `Transactor`, Flyway runner, generic codecs | config | **repositories, `FilterSql`, `PeriodSql`, `SiteTypesSql`, the bundle and merge stores and the seed importer leave** |
| `server` | `server` (composition root, adapters); `domain.<feature>` services, repositories, controllers, routes, tools; `resources/db/migration` (95 files) | core, db, api, plugin-host, `upstreams`; plugin modules **`% Runtime` only** | gains the repositories and the migrations |
| `upstreams` *(new)* | `upstreams`: parsers and clients of public upstreams (OpenAlex, Crossref, Unpaywall, CORE, DataCite, archive.org, Europe PMC, arXiv, Wikidata, Commons, JATS, landing-page rules) over `PluginHttp` | plugin-api, circe (plugin platform) | from `plugin-host/AcquireParse` & friends, `core/RepositoryLanding`, `Jats`, `Commons`, `Wikidata`, `server/acquire` clients |
| `plugin-api` | the SPI | sconfig, kindlings, iron | 9 defaulted additions (§8.3) |
| `plugin-host` | host side of the SPI | core, config, plugin-api, upstreams | its acquisition parsers move to `upstreams`; it uses the shared dating rules from there |
| `plugin-testkit` | unchanged API | plugin-host | its published dependency set shrinks with plugin-host's |
| `plugins/fetchers` *(new)* | `archive-org`, `oa-unpaywall`, `oa-core`, `oa-openalex`, `oa-landing`, `pmc`, `arxiv` | plugin-api, upstreams | from `server/acquire/fetchers` |
| `plugins/events` *(new)* | `events-gvp`, `events-noaa`, `events-wikidata` | plugin-api, upstreams | from `core/EventProviders`, `CatalogDating`, `server/events/BuiltinCatalogues` |
| `plugins/media` *(new)* | `media-wikidata`, `media-depicts`, `media-met`, `media-cleveland`, `media-aic`, `media-loc`, `media-pleiades`, `media-opencontext`, `media-smithsonian`, `media-europeana` | plugin-api, upstreams | from `server/media` collections and `core` parsers |
| `plugins/ai-http` *(new)* | `ai-anthropic`, `ai-openai`, `ai-local` | plugin-api, circe | from `server/agents/LlmClient` provider part |
| `plugins/ai-cli` *(new, Q5)* | `ai-claude-code`, `ai-codex`, `ai-opencode` | plugin-api | from `server/agents` runtimes |
| `plugins/vaults` | `keepassxc`, `1password`, `bitwarden` | plugin-api | directory moved from `plugin-vaults`; artifact name kept |
| `cli`, `web`, `plugin-resolver`, `plugin-fixtures/*`, `chimney-iron*` | | | unchanged except edges (slice 12) |

```
values       → iron
api          → values                       (JVM + JS)
config       → values
files        → api
core         → values, files
db           → config                        (no longer core)
plugin-api   → sconfig, kindlings, iron
upstreams    → plugin-api, circe
plugin-host  → core, config, plugin-api, upstreams
server       → core, db, plugin-host, upstreams;  plugins/* and plugin-vaults % Runtime
plugins/*    → plugin-api (+ upstreams)
cli          → api, config, files            (never core, db or server)
web          → api                           (JS)
```

### 7.2 Consequences

- The server depends on the plugin modules `% Runtime`, so no server code can import a plugin's class. That is
  #7911's "server depends on them only for bundling", enforced by the compiler. `server/nativeImage`, the relink
  kit and `pluginRepo` read `Runtime / fullClasspath` instead of `Compile / fullClasspath`. AGENTS.md already
  plans that change for chimney-iron.
- Migrations: the files move from `modules/db/src/main/resources/db/migration` to the same path in `server`,
  unchanged, so Flyway checksums and `Migrate.Replaced` stay valid. The index generator moves with them (Q12). Each
  table's owner is recorded in §3.1, not by splitting files: many migrations touch several features' tables, and
  applied migrations are never edited.
- Package moves rename classes, and `reachability-metadata.json` names classes. Every slice re-records the
  metadata with the pinned JDK (server `run` against an empty database, the CLI, and the plugin modules' own
  metadata files) and proves it with the bundle's `--self-check` and `smoke.sh`.
- `pluginRepo` also publishes `upstreams`, because plugin-host, which the testkit runs, uses its dating rules.

## 8. Plugin separation (#7911)

### 8.1 What each built-in needs from the server today (measured)

"Moves" counts the files and lines that leave the server, core and plugin-host. "Host symbols" counts the distinct
non-SPI types those files import from `server`, `domain`, `config`, `api`, `pluginhost`, `db` and `values`.

| family | moves | host symbols | what it needs that `plugin-api` does not offer |
|---|---|---:|---|
| fetchers (7) | 28 files, 2 351 lines | 21 | **F1** endpoints and landing rules from `acquire.*` (`AcquireConfig`, `LandingConfig`); **F2** the server's `Http` with JSON, status and headers (`Http.once`, `HttpAnswer`), not only `PluginHttp.get` bytes; **F3** clients the host also uses: `OpenAlexCrossref` (discovery), `ArchiveOrg` (`archive search/info/import`), `CoreApi` (discovery's CORE search); **F4** the CORE budget, which is server-wide state persisted in `upstream_quota` and spent by discovery too (`CoreBudget`); **F5** parsers in plugin-host and core (`AcquireParse`, `PmcRecord`, `EuropePmcHit`, `RepositoryLanding`, `Jats`, `CoreQuota`); **F6** registration state shared between plugins: `oa-landing` reads Unpaywall's and OpenAlex's records only while their plugins are registered (`OaIndex.unpaywallRegistered`, the `LandingGroup` carry rule) |
| events (3) | 5 files, 647 lines | 19 | **E1** `events.<catalogue>.*` settings, read at each import; **E2** `events rederive` re-parses a built-in's stored `raw` (a plugin's `raw` is never parsed); **E3** the dating rules (`CatalogDating`), which the host also applies to third-party records (`PluginEvents.derive`); **E4** the marker design: `HostEventCatalogue.read` is never called, and the GVP/NOAA/Wikidata branches live in `EventCatalogHandlers` |
| media (10) | 28 files, 2 426 lines | 27 | **M1** the database: `WikidataCommons`/`CommonsDepictions` run their own loop over `MediaRepo.Target`s and write through the store (`db.MediaRepo`, `Deps`); **M2** event targets: `media-depicts` asks about events, and the SPI asks about places only; **M3** roles: built-ins store `image`, `locator-map`, `plan`, `aerial-view`, `route-map`, `depiction`, but `MediaCollection.Roles` is `object`/`map`/`image` and a collection has one role; **M4** licences: built-ins build rights in host code (`MediaLicence` over Commons or museum metadata) and check them against their own allowlists (`media.<provider>.licences`), while a plugin states a licence that the host classifies and checks against `media.collections.licences` / `per-collection.<name>`; **M5** the match rule "the target's own item" (Wikidata pictures); **M6** `media.<provider>.*` settings, read at each import |
| ai-http (3) | 3 files, 219 lines + the provider part of the agent loop | 6 | **A1** the turn is sttp-ai's `ClaudeAgent`/`OpenAIAgent` in the host. A plugin must build the provider request itself over `AiHttp`, and sttp-ai is not in the plugin platform; **A2** `agents.providers.<name>.*` settings and API keys from the environment |
| ai-cli (3) | 17 files, 2 739 lines | 16 imported + 25 same-package (`AgentDefinitions`, `RunGrant`, `LlmClient`, `ToolStyle`, `PoolSink`, …); used from ~100 places outside | **C1** a run token bound to the step's tools and the agent MCP URL (`RunTokens`); **C2** generated agent definitions in each runtime's format (`AgentGenerator`, `AgentTrees`, `CodexHome`), also used by `just plugin`; **C3** plan pools and their vendor readers (`PlanPools`: `rate_limit_event`, `codex app-server`); **C4** process launch (`CliProcess`) and the quota-guard mod; **C5** `PlanQuotaRepo` (the database) |

### 8.2 How each gap is closed

Three mechanisms, chosen so that `plugin-api` gains interfaces and plain data only:

1. **Host-supplied settings (no SPI change).** The host gives each first-party plugin a fallback block built from
   the host keys the plugin used to read. The plugin's config is: the user's `histmersion.plugins.<id>` block,
   then the mapped host keys, then the plugin's defaults resource. Users keep writing `histmersion.acquire.unpaywall.url`
   and `HISTMERSION_ACQUIRE_…` exactly as today. One table in the host glue (`FirstPartySettings`: plugin id →
   host paths) drives both the mapping and the reload rule: a plugin is rebuilt when its block or one of its
   mapped host paths changes. That table replaces `Plugins.carried`'s `independent` set and the `LandingGroup`.
   The plugin report keeps listing only the plugin's own block, so it stays byte-identical (Q7).
2. **The `upstreams` library.** One library of upstream parsers and clients over `PluginHttp`, used by the
   plugins and by the host's discovery, archive.org import, JATS conversion and the browser-download landing
   rules. That avoids keeping two copies of a parser in step. It is not a plugin and registers nothing.
3. **Defaulted SPI additions** (§8.3) for what the host must lend: response status and headers, a quota, peers'
   registration, event targets and roles, re-reading a stored record, and, for ai-cli, a runtime interface.

| gap | closed by |
|---|---|
| F1, E1, M6, A2 | mechanism 1 |
| F2 | SPI-1 `PluginHttp.fetch` |
| F3, F5 | mechanism 2 |
| F4 | SPI-2 `PluginContext.quota("core")`; the host backs it with today's `CoreBudget` and `upstream_quota` row, shared with discovery |
| F6 | SPI-3 `FetchContext.registered(pluginId)`, answered per fetch from the generation the fetch is pinned to; the `LandingGroup` carry rule is no longer needed |
| E2 | SPI-7 `EventCatalogue.reread(raw)`; `events rederive` calls it for every catalogue that gives one |
| E3 | the plugin emits the four-point dating itself (`CatalogDating` moves to `upstreams`); the host's `derive` keeps a given span, so the stored result is the same |
| E4, M1 | the host's import loops (`EventCatalogHandlers.runImport`, `CollectionImport`) run first-party catalogues and collections exactly as they run third-party ones; the marker classes and `HostOwned` go |
| M2 | SPI-5 `MediaCollection.searchEvent` + `MediaEvent` |
| M3 | SPI-4 `MediaItem.role` and `MediaCollection.roles`; `Roles` widened to the stored set |
| M4 | the plugin states the licence exactly as Commons or the museum gives it, and the host classifies it with the same `MediaLicence` code. The host's allowlist lookup (`licencesFor(name)`) answers a first-party collection from its old key, `media.<provider>.licences`, so each built-in keeps its own allowlist and its documented key (Q13). The fixture suites decide equality: the same records stored and refused, with the same reasons |
| M5 | SPI-6 `MediaMatchRule.Own` |
| A1 | the plugin implements the Anthropic and OpenAI message APIs over `AiHttp` + circe (platform libraries). The host loop stays sttp-ai's through `PluginAiClient`, as for any plugin provider |
| C1–C5 | SPI-9 `AiRuntime` (Q5); generator, trees, run tokens and plan-pool storage stay host features |

### 8.3 The SPI additions

All additive and defaulted, so a plugin built against SPI 2 still compiles and links. `Spi.Version` stays 2, as it
did through the earlier additions. MiMa is still the pending step 9.

| # | addition | default |
|---|---|---|
| SPI-1 | `PluginHttp.fetch(url, headers): Either[String, PluginHttpResponse(status, headers, body)]` | from `get` with status 200 |
| SPI-2 | `PluginContext.quota(upstream: String): UpstreamQuota` with `take(purpose: String): Either[String, Unit]` and `observe(status, headers): Unit` | an unlimited quota |
| SPI-3 | `FetchContext.registered(pluginId: String): Boolean` | `false` |
| SPI-4 | `MediaItem.role: Option[String]` (with the old `apply` and constructor kept, as was done for `FetchedFile`), `MediaCollection.roles: Set[String]` | `None`, `Set(role)` |
| SPI-5 | `MediaCollection.searchEvent(event: MediaEvent, http)`, `MediaEvent(id, label, wikidata, period)`, `MediaCollection.asks: Set[MediaTarget]` | `Right(Nil)`, places only |
| SPI-6 | `MediaMatchRule.Own` | — |
| SPI-7 | `EventCatalogue.reread(raw: String): Option[CatalogueEvent]` | `None` |
| SPI-8 | `AiProvider` request options the built-ins use (prompt caching, strict tools), as found in slice 4 | off |
| SPI-9 | `AiRuntime` registry (ai-cli, Q5): `generate(definitions): Map[RelPath, String]`, `step(request, McpAccess(url, token), ProcessLauncher): Either[AiFailure, AiStepResult]`, `planReading(http): Option[PlanReading]` | refusing registry |

### 8.4 What stays true

- **One ServiceLoader type.** Each plugin module lists its providers in `META-INF/services/com.histmersion.plugin.Plugin`.
  `Plugins.builtins` and the `Builtin`/`BuiltinWith` wrappers go; discovery finds the first-party plugins as it
  finds vaults today.
- **Built in and switchable.** Their jars are in every image (runtime dependencies of the server). Their defaults
  resources move with them (`enabled = true`; `false` for the keyed `media-smithsonian`/`media-europeana`).
  `histmersion.plugins.<id>.enabled` and `HISTMERSION_PLUGIN_<ID>_ENABLED` work as before.
- **First-party status.** A plugin is first-party when its id is in the host's reserved-name table (names only,
  no code dependency) and it was loaded from the image's own class path, not the relink's plugin set. Reserved
  names still keep a third-party plugin off a built-in's name.
- **Order.** The fetch chain's default order (`histmersion.plugins.order`) and the report's order (by id) do not
  change; the plugin-report snapshot checks both.
- **Tests.** `Plugins.offline` (a test's generation) becomes discovery with fetchers excluded, as before. The
  testkit is unchanged, and the first-party plugins' own suites use it like any plugin author would.
- **AGPL.** Every new module inherits `ThisBuild / licenses`.

## 9. Slice plan

### 9.1 How equivalence is shown (every slice)

Slice 1 makes step 2's one-off comparison permanent, as `ContractSnapshotSuite` (server tests, no database; the
CLI help in the cli module's tests). It writes these to `src/test/resources/contract/` and fails on any difference:

| snapshot | source |
|---|---|
| `openapi.json` | the OpenAPI document of `Api.all` (tapir-openapi-docs as a test dependency) |
| `mcp-tools.json` | MCP `tools/list` over the in-process server |
| `toolbox.json` | every agent role's toolbox schemas |
| `cli-help.txt` | the decline help tree (the text `tools/gen-cli-docs.sh` renders) |
| `plugins.json` | the plugin report of the stock generation: ids, versions, provides, states, settings, order |
| `settings.json` | the settings view of `reference.conf` (keys, kinds, defaults) |

In addition, per slice:

- round-trip tests for every new newtype (`decode(encode(x)) == x`, and the JSON of a sample equals the old
  primitive's);
- the feature's service contract suite on `InMemory` and on the database;
- `just test-it`, with the DB suites run uncached (`testOnly *`);
- for slices that move SQL: `flyway validate` against a restored `*_test` copy of the live database, and the live
  constraint check (`histmersion query`, read-only);
- for plugin slices: the recorded HTTP traffic of the fixture suites, the same requests in the same order (testkit
  record mode, before and after);
- the native bundle with re-recorded metadata, `--self-check`, `scripts/bundle/smoke.sh`, then a deploy.

### 9.2 Slices

Sizes count the lines the slice moves or rewrites: S < 2 k, M 2–6 k, L 6–12 k, XL > 12 k.

| # | slice | scope | size | risk | the risk is … |
|---|---|---|---|---|---|
| 1 | **Groundwork** | `ContractSnapshotSuite`; `util.json`/`util.database` generic newtype instances; the identifier newtypes in `values` (unused yet); `Transactor`, `Clock`/`Environment`/`UserPaths` from `Main`; `@main def` entry points (#7908) and every reference; AGENTS.md rules 14 and the "adding an operation" steps rewritten for Controllers | M | low | the generic givens' form (fallback in §4.4); the entry-point rename in scripts (relink tests cover it) |
| 2 | **Queues and goals** (the template) | the §6 example for real; goals the same way; `PlanningState`; the four pilots (activity, jobs, personal, settings) brought in line (Clock, `Transactor`, newtypes, contract suites) | M (3 k) | low | small features with full suites |
| 3 | **Plugins I: fetchers and events** | `upstreams` lib; SPI-1, 2, 3, 7; `plugins/fetchers`, `plugins/events`; host settings mapping; `Builtin*`, `HostEventCatalogue` and `OaIndex` state go; the CORE quota behind SPI-2 | L (6 k) | high | identical requests in identical order (memo keys, landing pages, CORE pacing), `events rederive` results; gated by recorded traffic and the fixture suites (`OaFetchSuite`, `PmcFetchSuite`, `LandingFetchSuite`, `EventImportSuite`, `PluginCatalogueSuite`) |
| 4 | **Plugins II: media and ai-http** | SPI-4, 5, 6, 8; `plugins/media`, `plugins/ai-http`; the media marker classes go; Wikidata pictures and depictions run through the host's collection loop | L (5 k) | high | licences and allowlist (M4), per-item roles, the agent loop's wire requests; gated by the media fixture suites (`MediaImportJobSuite`, `MediaCollectionsJobSuite`, `CommonsDepictionsSuite`, `WikidataCommonsSuite`, …) and by request bodies captured on a local stub of each API |
| 5 | **Sources, findings, citations, labels, search, query, corpus lint** | the heart of `Handlers`; `Citekey`, `SourceUid`, `Doi`, `Year`, `Latitude`/`Longitude`, `Title`, … end to end; `CorpusState` | XL (≈ 10 k + 2 k of Handlers/McpTools) | high | the most used types; search SQL must not change (snapshot of generated SQL for the filter suite) |
| 6 | **Articles** | versions, links, coverage, critiques, pages; `articles.review` (`ReviewArticle`, `ReviewLedger`); `articles.lint` (`MarkdownLint`); `Slug` | L (9 k) | medium | the review panel's ledger rules: pure and well tested |
| 7 | **Places, periods, maptiles** | gazetteer, names, overrides, hierarchy, geocoder, site types, point areas, land, importers; periods; tiles and undated sites; `PlaceId`, `Kilometres`, `Zoom`; `PlaceState` | L (9 k) | medium | `PlaceRepo` (1 710 lines) and tile timing (performance suite at live volumes, per the "test at real volumes" rule) |
| 8 | **Events, attestations, corrections, media (host side)** | event store, datings, links, leads; the host's catalogue and collection loops; assets, IIIF, figures, covers, credits | L (10 k) | medium | already narrowed by slices 3–4 |
| 9 | **Ingest, acquisition, calibre, jobs, activity** | converter, spool, originals (`OriginalStore` service), discovery, candidates, recheck, quota store, calibre endpoints, background jobs | L (7 k) | medium | job lifecycle under restart; the converter's child process (`Supervisor`) |
| 10 | **Agents I: runtime host side, runs, approvals** | `agents` (runs, steps, traces, leases, state, chains, allowances, prices, plan pools, toolbox, generator), `approvals`; `RunId`, `ModelRef`; `RunState` | XL (14 k) | high | the largest server area; the toolbox snapshot and the scripted-model suites (`agent-testkit`) are the oracle |
| 11 | **Agents II: pipelines, managers, autopilot, scheduler** | the nine pipelines, managers and supervisor, autopilot, scheduler and cron; `CronExpression` | XL (15 k) | high | long-running supervisors; the same oracle plus the manager DB suites |
| 12 | **Operations and closing** | backup, bundle, merge (`MergePlanner`), maintenance, settings, plugins glue, instance/UI, basemap; `db` reduced to `util.database`; `Handlers`/`OpsHandlers` deleted; `core`'s logic off api records; `cli.<feature>` packages and typed client edges; web edges; ai-cli with SPI-9 (if Q5 says now); AGENTS.md module section rewritten | XL (18 k) | medium | many moves of self-contained code; merge has the strongest suites in the repo |

Order: groundwork, then the template on a small feature, then the plugins **before** the features they live in.
Doing the plugins first means their parsers move once, into their modules, and are not moved into
`domain.media`/`domain.events` and then out again. Each slice is deployed (bundle, self-check, smoke) before the
next starts. Each slice records its as-built section in this document, as the plugin and module-layout designs
did.

## 10. What this design does not change

- No endpoint, MCP tool, CLI command, config key, environment variable, table or column is renamed or removed.
- No migration is added for the refactor itself. A slice that finds one necessary stops and asks.
- The web page's behaviour; only its edges get types.
- The multi-user work (#7843) comes after; this design only leaves its seam (§6.4).

## 11. Open questions

| # | question | recommendation |
|---|---|---|
| Q1 | Parse raw inputs in the **controllers** (error texts unchanged; input records carry `Pure` "raw" newtypes) or in the **decoders** (constrained newtypes in input records, plus a decode-failure handler answering `ApiError(message)`, which changes some 400 texts)? | Controllers now; decoders as a separate, announced change later |
| Q2 | Entry-point classes: keep the names (`@main def Main(args: String*)` keeps `com.histmersion.server.Main`) or use Scala-style names (`histmersionServer`, `histmersionCli`, `pluginTool`, …) and update every reference (§5.5)? | Scala-style names; every reference is in this repo and each kit carries its own `main-class` |
| Q3 | The clock: `java.time.Clock` everywhere, or the pilots' `() => Instant`? | `java.time.Clock`: it also serves `LocalDate.now(clock)`, and `Clock.fixed` serves tests |
| Q4 | Plugin modules: one per family (6 + the `upstreams` library) or one per plugin (26)? | Families: 26 sbt projects would add build time for no isolation the switch does not already give |
| Q5 | ai-cli (`claude-code`, `codex`, `opencode`): build the `AiRuntime` SPI (SPI-9) and move them in slice 12, or keep them as the one host-owned exception? §22.7 of the plugins design advised against moving them; the measured coupling is 25 host types and ~100 call sites | Move them, last: design SPI-9 in slice 10, when the agents host side is restructured, and move in slice 12. Generator, trees, run tokens and plan-pool storage stay host features. If slice 10 shows SPI-9 would need more than the four members sketched, come back with numbers before slice 12 |
| Q6 | One shared `upstreams` library for the host and the plugins, or a copy of each parser on each side? | One library: discovery and the fetchers parse the same OpenAlex, Crossref and CORE records |
| Q7 | Show the mapped host settings in a first-party plugin's report? | No: the report lists the plugin's own block, as today (byte-identical) |
| Q8 | Flags: keep `Boolean` parameters (named) or newtype them too? | Keep `Boolean`; replace a Boolean with an enum only where two or more flags form a mode (`dryRun`/`apply`/`force`) |
| Q9 | Keep `core` as a separate pure module, or fold it into the server now that features live in the server? | Keep it: no database, fast tests, and plugin-host depends on it |
| Q10 | Goal slugs are unchecked today (all 214 live slugs pass the article rule). Apply `Slug`'s rule to `GoalSlug`? | Yes; a new goal with an invalid slug is then refused with the article slug's message |
| Q11 | Reading a constrained newtype from the database: checked (fail with the column and value) or unchecked? | Checked; live data has 0 violations, and a violation should be loud |
| Q12 | Migrations: one directory in the server (unchanged files) or split per feature? | One directory, with the owner of each table recorded in §3.1; splitting would mean editing applied migrations |
| Q13 | Media allowlists: built-ins check `media.<provider>.licences`, plugins `media.collections.licences` / `per-collection.<name>`. Keep both key families (first-party collections answered from their old keys), or fold the built-ins' keys into `per-collection.<name>` with a legacy mapping? | Keep both for this step: no key changes. Folding them is a separate, documented settings change |
| Q14 | Markdown rendering: a `MarkdownRender[A]` type class with instances on the api types' companions, or keep one renderer object per feature? | The type class: it is "a function about a type", and the CLI and MCP share it |
| Q15 | Plugins before the features they live in (slices 3–4 before 5–12)? | Yes, so plugin code moves once |
| Q16 | `cli` (76 files, flat) into `cli.<feature>` packages in slice 12, or leave the CLI's layout for later? | Slice 12: the move is cheap and the CLI help snapshot guards it |
