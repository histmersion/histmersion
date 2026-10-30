# Dependencies between domains and supporting areas, measured

The evidence behind §3 of `../research-domain-model.md`. All measurements at master 58de5489.

## How it was measured

**References.** Every top-level type (class, trait, object, enum, type alias at column 0) in the `src/main` files of `server`, `core`, `db`, `api`, `config` and `files` belongs to the domain or supporting area of its file (`files.md`, the engine's and data management's mixed files counted where they are today). A reference is a file naming, outside comments and string literals, a type that belongs elsewhere, counted once per (file, type). Names defined in more than one place and shared vocabulary (`Period`, `Filter`, `Paging`, `Label`, `Wikidata`, `ExternalId`, `Source`, `Target`, …) are not counted. Type names are used rather than imports because today's packages are layers with wildcard imports. The scan over-counts a little (a nested type named like a top-level one elsewhere) and does not see SQL joins by table name — those are measured separately (back doors, below).

**Co-change.** `git log --name-only` on master, merges skipped. The reformat commit 4c1da9d1, the one-type-per-file split ba0a373d and commits touching more than 60 files are left out (21 commits). Old paths are classified by the same name rules as today's files. A pair's strength is Jaccard: commits touching both / commits touching either.

**Per-kind share of a mechanism.** For a file of a generic mechanism (the agent engine, bundle and merge, lint, export, jobs), each top-level block (`def`, `val`, class, enum) is assigned to the domain whose words it uses most (`source`, `citekey`, `finding` → library; `article`, `slug`, `version`, `coverage` → research; `event`, `place`, `period` → catalogue; `asset`, `thumb` → media), or to "generic" when it uses none. It is an estimate of how much of the file is a rule about one kind of record; the slices measure exactly as they move code.

**File-system access.** Occurrences of `Files.*`, `Path.of`, `Paths.get`, `java.nio.file`, `java.io.File`, `FileChannel`, `RandomAccessFile`, `File…Stream`, `MappedByteBuffer`, `StandardCopyOption`, `ProcessBuilder` outside comments.

## References, the final layout

Read as "row depends on column". Layers, bottom first: the supporting areas (`util.*`), then the domains automation (the engine), catalogue, media, library, research; personal beside them.

| from \ to | automation | catalogue | library | media | personal | research | util.basemap | util.data | util.identity | util.jobs | util.plugins | util.settings |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **automation** | · | 3 | 74 | 0 | 1 | 43 | 0 | 1 | 2 | 6 | 0 | 0 |
| **catalogue** | 0 | · | 11 | 6 | 0 | 0 | 0 | 2 | 4 | 6 | 0 | 0 |
| **library** | 84 | 32 | · | 15 | 0 | 10 | 0 | 8 | 2 | 16 | 2 | 0 |
| **media** | 0 | 11 | 3 | · | 0 | 1 | 0 | 0 | 0 | 4 | 0 | 0 |
| **personal** | 2 | 1 | 0 | 0 | · | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| **research** | 60 | 7 | 34 | 1 | 0 | · | 0 | 14 | 0 | 0 | 0 | 0 |
| **util.basemap** | 0 | 0 | 2 | 0 | 0 | 0 | · | 3 | 0 | 5 | 0 | 0 |
| **util.data** | 11 | 25 | 27 | 5 | 0 | 13 | 2 | · | 7 | 10 | 0 | 1 |
| **util.identity** | 2 | 2 | 1 | 0 | 0 | 1 | 0 | 0 | · | 0 | 0 | 0 |
| **util.jobs** | 0 | 4 | 4 | 0 | 0 | 0 | 2 | 6 | 0 | · | 0 | 0 |
| **util.plugins** | 1 | 1 | 9 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | · | 1 |
| **util.settings** | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 1 | · |

616 references across places; **264 point upward** — from a supporting area into a domain, from a lower domain into a higher one, or out of personal. They are exactly the code the design moves into contributions, so the number measures the work, not a failure of the layout:

| group | references | removed by |
|---|---:|---|
| engine → contributing domains (automation → library 74, research 43, catalogue 3) | 120 | workflow, tool and manager-kind contributions (§3.5) |
| `util.data` → domains (per-kind bundle, merge, lint, export, storage rules) | 81 | data-management contributions (§3.6) |
| `util.plugins` → domains (built-ins named in the plugin glue) | 12 | the plugin slices |
| `util.jobs` → domains (job kinds and their detail rendering) | 8 | `JobKinds` contributions |
| `util.identity` → domains (the alias mechanism switching over owners' tables) | 6 | `AliasTables` contributions |
| `util.basemap` → library (a name collision: `SeaFloorSource`) | 2 | nothing |
| between domains (catalogue → library 11, catalogue → media 6, library → research 10, media → library 3, media → research 1) | 31 | `MapLayer`, `PassageSource`, `SourceReferrer`, covers and figures in library ingest (§3.8) |
| personal → automation, catalogue (`ItemKind`, `ItemTitles`); automation → personal (a name collision) | 4 | `Referable` (§3.9) |

Every upward reference, with its types and files:

```
UP automation->library 74: {'Query': 8, 'EnrichSource': 6, 'Ingest': 4, 'Search': 4, 'AcquireChecks': 4, 'Acquire': 4, 'AgentVerify': 3, 'AgentProposals': 3} | files {'AgentsHandlers': 9, 'Toolbox': 8, 'ManagersHandlers': 7, 'ManagerSupervisor': 5, 'Scheduling': 4, 'AgentsConfig': 3, 'CompareRuns': 3, 'AgentsMarkdown': 2}
UP automation->research 43: {'ArticleFilter': 5, 'ReviewArticle': 5, 'ArticlePlans': 3, 'CompileArticle': 3, 'CompileArticlePipeline': 3, 'AgentInferFilterConfig': 2, 'InferFilterPipeline': 2, 'FilterInference': 2} | files {'ManagerSupervisor': 7, 'AgentsHandlers': 6, 'AnswerSchemas': 4, 'AgentsConfig': 3, 'ManagerPolicy': 3, 'AgentsMarkdown': 2, 'AgentRepo': 2, 'AgentsSettings': 2}
UP util.data->library 27: {'Originals': 5, 'Finding': 3, 'Edge': 3, 'SourceMatch': 3, 'MergeText': 3, 'SourceRepo': 2, 'FindingMatch': 2, 'InstanceLabel': 1} | files {'MergePlanner': 6, 'BundleImport': 4, 'LintRepo': 3, 'BundleHandlers': 2, 'MergeMarkdown': 2, 'MergeFold': 2, 'MergeState': 2, 'BundleExport': 1}
UP util.data->catalogue 25: {'Place': 4, 'PlaceRepo': 3, 'Attestation': 3, 'SiteTypes': 2, 'Corrections': 2, 'MatchCounts': 2, 'PeriodSql': 1, 'EventRepo': 1} | files {'BundleImport': 5, 'LintRepo': 5, 'BundleEntities': 4, 'MergePlanner': 3, 'BundleFiles': 2, 'MaintenanceConfig': 1, 'StorageProbe': 1, 'MergeMarkdown': 1}
UP util.data->research 13: {'ArticleVersion': 3, 'CoverageEdge': 2, 'CritiquePairing': 2, 'LedgerRow': 2, 'ArticleRepo': 1, 'Articles': 1, 'ArticleLinks': 1, 'TextAnchor': 1} | files {'Lint': 3, 'MergePlanner': 3, 'BundleImport': 2, 'LintRepo': 2, 'BundleEntities': 1, 'MergeLedger': 1, 'MergeRepo': 1}
UP catalogue->library 11: {'Finding': 1, 'Edge': 1, 'Chunk': 1, 'SourceRepo': 1, 'PassageFinder': 1, 'Aggregate': 1, 'BucketSample': 1, 'Bucket': 1} | files {'EventCatalogHandlers': 3, 'Tree': 3, 'AttestationTarget': 2, 'ImportRunner': 2, 'PlaceRepo': 1}
UP util.data->automation 11: {'Goal': 3, 'Queue': 3, 'GoalRepo': 1, 'Decided': 1, 'QueueRepo': 1, 'AgentTrees': 1, 'GoalPlans': 1} | files {'BundleImport': 5, 'MergePlanner': 2, 'BundleEntities': 1, 'StorageProbe': 1, 'MergeIssueDetail': 1, 'MergeFold': 1}
UP library->research 10: {'ArticleLinks': 4, 'Citation': 2, 'ArticleRepo': 2, 'CitationCount': 1, 'TocEntry': 1} | files {'SourceRepo': 3, 'AcquireMarkdown': 1, 'AcquireDefaults': 1, 'BrowserDownload': 1, 'CandidateSearch': 1, 'Rename': 1, 'SourceDelete': 1, 'SourceDeleteRepo': 1}
UP util.plugins->library 9: {'BuiltinFetchers': 2, 'OaIndex': 1, 'HttpPlugin': 1, 'OpenAlexCrossref': 1, 'SttpHttp': 1, 'CoreBudget': 1, 'ArchiveOrg': 1, 'Http': 1} | files {'Plugins': 7, 'LegacyPlugins': 2}
UP catalogue->media 6: {'MediaRepo': 3, 'MediaThumb': 2, 'MediaGeoHit': 1} | files {'EventDetail': 1, 'EventRepo': 1, 'TileFeatures': 1, 'TileRepo': 1, 'MapTileService': 1, 'PlaceDetail': 1}
UP util.data->media 5: {'AssetLink': 3, 'AssetLinks': 2} | files {'MergePlanner': 2, 'BundleImport': 1, 'BundleEntities': 1, 'MergeFold': 1}
UP util.jobs->library 4: {'AcquireRecheck': 2, 'AcquireChecks': 1, 'Ingest': 1} | files {'JobKind': 2, 'JobActions': 1, 'JobDetail': 1}
UP util.jobs->catalogue 4: {'SkippedPeriod': 1, 'EventImportReport': 1, 'EventRederiveReport': 1, 'SiteTypes': 1} | files {'JobDetail': 3, 'JobKind': 1}
UP automation->catalogue 3: {'Geocode': 1, 'Tree': 1, 'PeriodSql': 1} | files {'AgentsSettings': 1, 'CliProcess': 1, 'GoalRepo': 1}
UP media->library 3: {'SourceCoverParams': 2, 'Finding': 1} | files {'AssetLinkTarget': 1, 'MediaHandlers': 1, 'SourcePictures': 1}
UP util.basemap->library 2: {'SeaFloorSource': 2} | files {'BasemapBathymetryConfig': 1, 'BasemapService': 1}
UP personal->automation 2: {'Goal': 2} | files {'ItemKind': 1, 'ItemTitles': 1}
UP util.identity->catalogue 2: {'Place': 2} | files {'AliasKind': 1, 'AliasRepo': 1}
UP util.identity->automation 2: {'Goal': 2} | files {'AliasKind': 1, 'AliasRepo': 1}
UP media->research 1: {'Coverage': 1} | files {'OpenContext': 1}
UP personal->catalogue 1: {'Place': 1} | files {'ItemKind': 1}
UP util.plugins->catalogue 1: {'BuiltinCatalogues': 1} | files {'Plugins': 1}
UP util.plugins->media 1: {'BuiltinMedia': 1} | files {'Plugins': 1}
UP util.plugins->automation 1: {'BuiltinAi': 1} | files {'Plugins': 1}
UP automation->personal 1: {'Note': 1} | files {'Queue': 1}
UP util.identity->library 1: {'ResolvedSource': 1} | files {'ResolvedId': 1}
UP util.identity->research 1: {'ResolvedArticle': 1} | files {'ResolvedId': 1}
```

## The previous layouts, for comparison

**Second draft** (automation on top as a domain; platform, exchange and operations as domains): 546 references, 121 upward.

| from \ to | catalogue | automation | exchange | library | media | operations | personal | platform | research |
|---|---|---|---|---|---|---|---|---|---|
| **catalogue** | · | 0 | 0 | 11 | 6 | 0 | 0 | 12 | 0 |
| **automation** | 7 | · | 0 | 80 | 0 | 1 | 0 | 43 | 56 |
| **exchange** | 19 | 8 | · | 18 | 5 | 2 | 0 | 16 | 6 |
| **library** | 27 | 10 | 2 | · | 15 | 1 | 0 | 25 | 10 |
| **media** | 11 | 0 | 0 | 3 | · | 0 | 0 | 4 | 1 |
| **operations** | 5 | 4 | 2 | 5 | 0 | · | 0 | 17 | 0 |
| **personal** | 1 | 2 | 0 | 0 | 0 | 0 | · | 1 | 0 |
| **platform** | 12 | 4 | 3 | 21 | 1 | 8 | 1 | · | 6 |
| **research** | 7 | 13 | 0 | 20 | 1 | 0 | 0 | 13 | · |

**Engine with goals in research** instead of automation (otherwise the final layout): 226 upward against 186 for the same layout with goals in automation, before data management was dissolved — goals are used by the engine's managers, autopilot, scheduler and agents far more than by any domain (goals use agents 12, worklists 5, managers 4, autopilot 4; are used by workflows 15, agents 13, managers 7, autopilot 6, scheduler 5, acquisition 4).

**Starting proposal** (corpus, geography, writing, media, acquisition, automation, operations): 622 references, 135 upward; 71 of them from a core subdomain into automation or operations.

| from \ to | acquisition | automation | corpus | geography | media | operations | personal | platform | writing |
|---|---|---|---|---|---|---|---|---|---|
| **acquisition** | · | 27 | 46 | 5 | 10 | 4 | 0 | 5 | 4 |
| **automation** | 53 | · | 46 | 12 | 0 | 11 | 1 | 0 | 60 |
| **corpus** | 2 | 6 | · | 28 | 5 | 2 | 0 | 0 | 12 |
| **geography** | 2 | 6 | 18 | · | 6 | 4 | 0 | 0 | 1 |
| **media** | 0 | 4 | 3 | 11 | · | 1 | 0 | 9 | 1 |
| **operations** | 17 | 45 | 24 | 39 | 13 | · | 0 | 0 | 8 |
| **personal** | 0 | 2 | 0 | 1 | 0 | 1 | · | 0 | 0 |
| **platform** | 3 | 0 | 0 | 0 | 0 | 0 | 0 | · | 0 |
| **writing** | 0 | 12 | 38 | 8 | 1 | 5 | 0 | 0 | · |

## Back doors from the engine into domain data

| | before (second draft, automation as a domain) | after the workflows move to their domains | after the design |
|---|---:|---:|---:|
| calls into other domains' repositories | 36 in 15 files (`QueueRepo` 13, `ArticleRepo` 7, `AcquireCheckRepo` 7, `PeriodSql` 5, others 4) | 10 in 3 files: `PeriodSql` 5 (`GoalRepo`), `ReviewLedgerRepo` 2 and `ArticleRepo` 3 (`ManagerSupervisor`, `ManagersHandlers`) | 0: the period filter uses `util.database`'s range SQL; the review manager is research's contribution |
| SQL in engine repositories over domain tables | 19 reads (`articles` 10, `article_versions` 1, `sources` 3, `queue_items` 5) + `jobs` 36 | 14 reads (`articles` 10, `article_versions` 1, `sources` 3) in `ManagerRepo`, `GoalLinkRepo`, `AgentRepo`; `jobs` is `util.jobs`', reached through its service | 0: goal links show titles through `Referable`; managers' article queries go with the review manager kind to research |
| writes through `Handlers` (public operations) | 11 + the toolbox | — | the same calls on the owners' services with `Caller.Automation(run)`; the toolbox's tools are contributed by their owners |

## Per-kind share of today's generic mechanisms

| mechanism | lines | generic | library | research | catalogue | media | automation |
|---|---:|---:|---:|---:|---:|---:|---:|
| bundle and merge (19 files) | 7 756 | 3 445 | 2 244 (+539 `SourceMatcher`, `MergeText`, about sources and authors by meaning) | 877 | 854 | 194 | 142 |
| of which `MergePlanner` | 2 426 | 680 | 1 052 | 315 | 288 | 20 | 71 |
| lint (`LintRepo`, `api/Lint`, `OpsHandlers`) | 1 105 | 371 | 461 | 105 | 36 | 1 | 131 (queue handlers in `OpsHandlers`) |
| export to the Markdown mirror (`Export`, `ArticleNote`, `Frontmatter`) | 418 | 198 | 113 | 68 | 39 | — | — |
| backup | 476 | 359 | 5 | — | 31 | — | 81 (job kinds) |
| storage and maintenance | 606 | 422 | — | — | 54 | — | 100 (job kinds) |
| jobs | 645 | 382 | 71 | — | 19 | 3 | 169 (run-as-job) |
| the engine's 32 mixed files (managers, agents, autopilot, scheduler, approvals, toolbox, …) | 14 822 | ≈ 9 400 | ≈ 5 400 for library and research together; upper bound — words like "review" or "acquire" also name engine concepts | | | | |

## File-system access by place (main code)

* ## util.basemap: 119 uses in 11 files
   files: {'server/basemap/BasemapService.scala': 35, 'server/basemap/DiskLru.scala': 19, 'server/basemap/BasemapFetch.scala': 14, 'server/basemap/GebcoSeaFloor.scala': 13, 'server/basemap/OnDemandTiles.scala': 11, 'server/basemap/PmTilesWriter.scala': 10, 'server/basemap/PmTilesFile.scala': 8, 'server/basemap/JavaRangeHttp.scala': 6, 'server/basemap/BasemapLabels.scala': 1, 'server/basemap/GebcoPart.scala': 1, 'server/basemap/RangeHttp.scala': 1}
   kinds: {'Files.*': 80, 'FileChannel': 14, 'java.nio.file': 14, 'StandardCopyOption': 9, 'Path.of': 2}
* ## automation: 100 uses in 10 files
   files: {'server/agents/AgentTrees.scala': 20, 'server/agents/CliProcess.scala': 20, 'server/agents/CodexHome.scala': 18, 'server/agents/GeneratePlugin.scala': 12, 'server/agents/PlanPools.scala': 8, 'server/agents/CodexExecClient.scala': 6, 'server/agents/AgentDoctor.scala': 5, 'server/agents/ClaudeCodeClient.scala': 5, 'server/agents/Prompts.scala': 4, 'server/agents/AgentsHandlers.scala': 2}
   kinds: {'Files.*': 61, 'java.nio.file': 17, 'Path.of': 9, 'ProcessBuilder': 7, 'StandardCopyOption': 3, '.toFile': 2, 'java.io.File': 1}
* ## util.data: 91 uses in 8 files
   files: {'server/BackupService.scala': 28, 'server/BundleHandlers.scala': 25, 'server/PgRelocation.scala': 11, 'files/DirSize.scala': 10, 'server/StorageProbe.scala': 7, 'db/BundleExport.scala': 4, 'db/MergeState.scala': 4, 'db/BundleImport.scala': 2}
   kinds: {'Files.*': 58, 'java.nio.file': 11, 'StandardCopyOption': 7, 'Path.of': 7, 'ProcessBuilder': 4, '.toFile': 3, 'java.io.File': 1}
* ## library: 51 uses in 9 files
   files: {'server/IngestHandlers.scala': 21, 'db/Originals.scala': 9, 'server/StoredOriginals.scala': 7, 'files/Frontmatter.scala': 4, 'server/acquire/AcquireHandlers.scala': 3, 'db/Importer.scala': 3, 'server/acquire/QueuedIngest.scala': 2, 'server/acquire/IngestJobs.scala': 1, 'server/acquire/SkeletonIngest.scala': 1}
   kinds: {'Files.*': 28, 'java.nio.file': 10, 'StandardCopyOption': 4, '.toFile': 4, 'java.io.File': 3, 'Path.of': 2}
* ## util.plugins: 36 uses in 3 files
   files: {'server/plugins/PluginTool.scala': 28, 'server/plugins/PluginSetResource.scala': 6, 'files/PluginKit.scala': 2}
   kinds: {'Files.*': 15, 'Path.of': 13, 'StandardCopyOption': 3, 'File(': 2, 'java.nio.file': 2, '.toFile': 1}
* ## server (adapter): 32 uses in 5 files
   files: {'server/Supervisor.scala': 18, 'server/Bootstrap.scala': 6, 'server/ServerGraph.scala': 4, 'server/Deps.scala': 2, 'server/Main.scala': 2}
   kinds: {'Files.*': 13, 'java.nio.file': 7, 'Path.of': 7, 'ProcessBuilder': 4, '.toFile': 1}
* ## catalogue: 31 uses in 2 files
   files: {'server/importers/GazetteerLoad.scala': 27, 'files/PlaceSeed.scala': 4}
   kinds: {'Files.*': 23, 'StandardCopyOption': 3, 'Path.of': 3, 'java.nio.file': 2}
* ## util.settings: 30 uses in 7 files
   files: {'config/edit/ConfigFileWriter.scala': 22, 'config/edit/ConfigFileState.scala': 2, 'domain/settings/SettingsService.scala': 2, 'config/UserConfigFile.scala': 1, 'config/edit/WriteOutcome.scala': 1, 'domain/settings/SettingsSnapshot.scala': 1, 'server/ConfigViews.scala': 1}
   kinds: {'Files.*': 13, 'java.nio.file': 8, 'StandardCopyOption': 6, 'Path.of': 2, '.toFile': 1}
* ## config root: 17 uses in 3 files
   files: {'config/UserPaths.scala': 10, 'config/EnvOverrides.scala': 4, 'config/AppConfig.scala': 3}
   kinds: {'Path.of': 9, 'java.nio.file': 5, 'Files.*': 2, '.toFile': 1}
* ## util.ocr: 9 uses in 4 files
   files: {'server/acquire/Spool.scala': 5, 'server/JatsConverter.scala': 2, 'server/ConverterClient.scala': 1, 'server/HttpConverterClient.scala': 1}
   kinds: {'java.nio.file': 4, 'Files.*': 3, 'Path.of': 2}
* ## SPLIT across features (Controllers): 6 uses in 1 files
   files: {'server/Handlers.scala': 6}
   kinds: {'Path.of': 2, 'java.nio.file': 1, 'Files.*': 1, 'java.io.File': 1, '.toFile': 1}
* ## api root (Api.all, split per feature): 6 uses in 1 files
   files: {'api/ApiIndex.scala': 6}
   kinds: {'java.nio.file': 3, 'Files.*': 2, 'Path.of': 1}
* ## media: 6 uses in 2 files
   files: {'db/Figures.scala': 5, 'server/media/MediaHandlers.scala': 1}
   kinds: {'Files.*': 4, 'java.nio.file': 2}
* ## util.files: 5 uses in 1 files
   files: {'files/SafePath.scala': 5}
   kinds: {'Paths.get': 4, 'java.nio.file': 1}

Outside the six measured modules: `cli` 227 (a host-side client: Calibre, plugin authoring — not the server, out of the rule), `plugin-resolver` 32, `plugin-testkit` 21, `plugin-vaults` 14, `plugin-host` 8 (plugins and their tools run with their own file rules).

## Co-change, the final layout

commits used 402 (skipped 21: the reformat, the split and >60 files)
| | library | automation | catalogue | research | util.data | util.basemap | media | util.plugins | util.jobs | util.settings | personal | util.identity |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **library** | **159** | 66 | 42 | 46 | 30 | 8 | 15 | 7 | 13 | 3 | 1 | 1 |
| **automation** | 66 | **147** | 25 | 43 | 24 | 4 | 10 | 4 | 10 | 3 | 1 | 1 |
| **catalogue** | 42 | 25 | **122** | 24 | 30 | 5 | 18 | 4 | 9 | 2 | 1 | 1 |
| **research** | 46 | 43 | 24 | **74** | 21 | 3 | 7 | 1 | 6 | 1 | 1 | 0 |
| **util.data** | 30 | 24 | 30 | 21 | **58** | 3 | 9 | 1 | 11 | 1 | 1 | 1 |
| **util.basemap** | 8 | 4 | 5 | 3 | 3 | **41** | 3 | 1 | 1 | 1 | 1 | 0 |
| **media** | 15 | 10 | 18 | 7 | 9 | 3 | **31** | 4 | 4 | 2 | 1 | 0 |
| **util.plugins** | 7 | 4 | 4 | 1 | 1 | 1 | 4 | **20** | 0 | 1 | 0 | 0 |
| **util.jobs** | 13 | 10 | 9 | 6 | 11 | 1 | 4 | 0 | **18** | 0 | 0 | 0 |
| **util.settings** | 3 | 3 | 2 | 1 | 1 | 1 | 2 | 1 | 0 | **9** | 0 | 0 |
| **personal** | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | **3** | 0 |
| **util.identity** | 1 | 1 | 1 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | **2** |
| automation | library | 0.28 | 66 | 147 / 159 |
| library | research | 0.25 | 46 | 159 / 74 |
| automation | research | 0.24 | 43 | 147 / 74 |
| catalogue | util.data | 0.20 | 30 | 122 / 58 |
| research | util.data | 0.19 | 21 | 74 / 58 |
| catalogue | library | 0.18 | 42 | 122 / 159 |
| util.data | util.jobs | 0.17 | 11 | 58 / 18 |
| library | util.data | 0.16 | 30 | 159 / 58 |
| catalogue | research | 0.14 | 24 | 122 / 74 |
| catalogue | media | 0.13 | 18 | 122 / 31 |
| automation | util.data | 0.13 | 24 | 147 / 58 |
| media | util.data | 0.11 | 9 | 31 / 58 |

`util.data` (bundle, merge, lint, backup, maintenance) changes with library in 30 commits and catalogue in 30, research in 21 — a new kind of record means touching the generic code today, which is what per-kind contributions end. Feature pairs that decided placements:

| feature | strongest partners (Jaccard, commits together) | reading |
|---|---|---|
| merge | bundle 0.50 (16), sources 0.23 (13), articles 0.21 (14), goals 0.19 (11), events 0.15 (10) | one exchange mechanism whose rules are per kind |
| article review ledger | pipelines 0.44 (15), agents 0.15, articles 0.12 | a workflow; contributed by research with its ledger |
| ingest | jobs 0.23 (8), sources 0.17 (10), acquisition 0.14 (12) | library's, running as a job |
| jobs | ingest 0.23, worklist 0.14, merge 0.13, maintenance 0.12, bundle 0.11 | used by everything: `util.jobs` |
| events | media 0.18 (13), places 0.16 (19), merge 0.15, sources 0.14 | catalogue, with pictures as a map layer |
| goals | managers 0.22 (20), agents 0.18 (21), autopilot 0.18, approvals 0.18 | the engine's objectives |
| basemap | instance 0.18 (15) | a supporting area with no research data |

