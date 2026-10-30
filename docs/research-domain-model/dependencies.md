# Dependencies between subdomains, measured

The evidence behind §3.2–§3.4 of `../research-domain-model.md`. Two measurements, both at master 58de5489.

## How it was measured

**References.** Every top-level type (class, trait, object, enum, type alias at column 0) in the `src/main` files of `server`, `core`, `db`, `api`, `config` and `files` belongs to the subdomain of its file (`files.md`; `AcquireRecheckService` counted where it is today, in automation). A reference is a file naming, outside comments and string literals, a type that belongs to another subdomain; it is counted once per (file, type). Names defined in more than one subdomain, and shared vocabulary (`Period`, `Filter`, `Paging`, `Label`, `Wikidata`, `ExternalId`, `Source`, `Target`, …), are not counted. Type names, not imports, are used because today's packages are layers (`com.histmersion.db.*` wildcards), so imports say nothing about features. The scan over-counts a little (a nested type named like another subdomain's top-level type) and misses SQL joins by table name (`personal` joins five tables; automation's repositories read four other subdomains' tables — §3.5 of the design measures those separately).

**Co-change.** `git log --name-only` on master, merges skipped. The reformat commit 4c1da9d1, the one-type-per-file split ba0a373d and every commit touching more than 60 files are left out (21 commits). Paths that no longer exist are classified by the same name rules as today's files, so history before the split counts. 408 commits touch main code of at least one subdomain. A pair's strength is Jaccard: commits touching both / commits touching either.

## References, the final grouping

Read as "row depends on column". Layers, bottom first: platform, catalogue, media, library, research, automation; exchange and operations at the top; personal beside.

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

546 cross-subdomain references; **121 point upward** (from a lower layer to a higher one, out of personal, or between exchange and operations). All of them, with their types and files — §3.4 of the design groups them and says what removes each group:

```
UP platform->library 21: {'AcquireRecheck': 2, 'AcquireChecks': 2, 'Edge': 2, 'BuiltinFetchers': 2, 'Ingest': 1, 'Frontmatter': 1, 'Hit': 1, 'SourceRepo': 1} | files {'Plugins': 7, 'LintRepo': 3, 'JobKind': 2, 'LegacyPlugins': 2, 'Queue': 2, 'JobActions': 1, 'JobDetail': 1, 'FormatSeverity': 1}
UP research->automation 13: {'ReviewGoal': 3, 'ReviewApproval': 3, 'ReviewFinding': 2, 'ReviewRound': 2, 'GoalStages': 1, 'ReviewLedger': 1, 'GoalRow': 1} | files {'ArticleReviewView': 4, 'ArticleReviewStatus': 4, 'ArticleReviewRepo': 3, 'Pages': 2}
UP platform->catalogue 12: {'Place': 2, 'EventImportReport': 1, 'SkippedPeriod': 1, 'EventRederiveReport': 1, 'SiteTypes': 1, 'Geocoder': 1, 'Gazetteer': 1, 'PlaceRepo': 1} | files {'LintRepo': 5, 'JobDetail': 3, 'JobKind': 1, 'AliasKind': 1, 'AliasRepo': 1, 'Plugins': 1}
UP catalogue->library 11: {'Edge': 1, 'Finding': 1, 'PassageFinder': 1, 'Chunk': 1, 'SourceRepo': 1, 'BucketSample': 1, 'Aggregate': 1, 'Bucket': 1} | files {'EventCatalogHandlers': 3, 'Tree': 3, 'AttestationTarget': 2, 'ImportRunner': 2, 'PlaceRepo': 1}
UP library->research 10: {'ArticleLinks': 4, 'Citation': 2, 'ArticleRepo': 2, 'CitationCount': 1, 'TocEntry': 1} | files {'SourceRepo': 3, 'AcquireMarkdown': 1, 'AcquireDefaults': 1, 'BrowserDownload': 1, 'CandidateSearch': 1, 'Rename': 1, 'SourceDelete': 1, 'SourceDeleteRepo': 1}
UP library->automation 10: {'ListFilters': 2, 'ManagerPolicy': 1, 'FollowupsInput': 1, 'GoalLinkRepo': 1, 'GoalRepo': 1, 'GoalPlans': 1, 'Goal': 1, 'Scheduling': 1} | files {'CandidateSearch': 5, 'IngestHandlers': 2, 'IngestBacklog': 1, 'AcquireMcp': 1, 'IngestMarkdown': 1}
UP platform->operations 8: {'BasemapFetch': 2, 'Vacuum': 2, 'VacuumPlan': 1, 'ReindexResult': 1, 'Backup': 1, 'ConfigFileInfo': 1} | files {'JobDetail': 2, 'JobKind': 2, 'JobActions': 1, 'LintRepo': 1, 'Maintenance': 1, 'PluginsMarkdown': 1}
UP catalogue->media 6: {'MediaRepo': 3, 'MediaThumb': 2, 'MediaGeoHit': 1} | files {'EventDetail': 1, 'EventRepo': 1, 'TileFeatures': 1, 'TileRepo': 1, 'MapTileService': 1, 'PlaceDetail': 1}
UP platform->research 6: {'CritiquePairing': 2, 'CoverageEdge': 2, 'Articles': 1, 'ResolvedArticle': 1} | files {'Lint': 3, 'LintRepo': 2, 'ResolvedId': 1}
UP platform->automation 4: {'Goal': 2, 'BuiltinAi': 1, 'Scheduling': 1} | files {'AliasKind': 1, 'AliasRepo': 1, 'Plugins': 1, 'QueueRepo': 1}
UP platform->exchange 3: {'InitPlan': 1, 'MergeApplyJob': 1, 'MergeApplyResult': 1} | files {'JobRepo': 2, 'JobDetail': 1}
UP media->library 3: {'SourceCoverParams': 2, 'Finding': 1} | files {'AssetLinkTarget': 1, 'MediaHandlers': 1, 'SourcePictures': 1}
UP library->exchange 2: {'Bundle': 1, 'BundleFormat': 1} | files {'HttpPolicy': 1, 'Originals': 1}
UP exchange->operations 2: {'MergeSettingsViews': 1, 'Backup': 1} | files {'BundleHandlers': 1, 'MergeApplyJobs': 1}
UP personal->automation 2: {'Goal': 2} | files {'ItemKind': 1, 'ItemTitles': 1}
UP operations->exchange 2: {'MergeSettings': 1, 'MergeConfig': 1} | files {'MergeSettingsViews': 2}
UP automation->operations 1: {'BackupState': 1} | files {'AutopilotService': 1}
UP media->research 1: {'Coverage': 1} | files {'OpenContext': 1}
UP personal->catalogue 1: {'Place': 1} | files {'ItemKind': 1}
UP platform->media 1: {'BuiltinMedia': 1} | files {'Plugins': 1}
UP platform->personal 1: {'Note': 1} | files {'Queue': 1}
UP library->operations 1: {'ReindexResult': 1} | files {'SourceRepo': 1}
```

## References, the starting proposal

The same scan over the starting proposal: corpus (sources, findings, citations, attestations, search, query, lint), geography (places, periods, events, corrections, map tiles), writing (articles and their review), media, acquisition (discovery, candidates, fetching, ingest, Calibre), automation (jobs included), operations (backup, bundle, merge, maintenance, settings, plugins host, instance); basemap left out.

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

622 references, 135 upward. The ones the user's rule forbids outright — a core subdomain (corpus, geography, writing, media, acquisition) depending on automation or operations — are **71** (55 into automation, mostly jobs and the queues; 16 into operations). In the final grouping the same measure (catalogue, media, library, research into automation, exchange or operations) is **26**: library → automation 10, research → automation 13, library → exchange 2, library → operations 1, each with a named fix. The proposed `operations` referenced the research subdomains 146 times, 138 of them from `bundle` and `merge`.

```
UP acquisition->automation 27: {'JobInfo': 4, 'Queue': 4, 'JobState': 3, 'QueueRepo': 3, 'JobKind': 2, 'ListFilters': 2, 'ManagerPolicy': 1, 'FollowupsInput': 1} | files {'CandidateSearch': 7, 'IngestHandlers': 7, 'IngestMarkdown': 3, 'AcquireChecks': 2, 'AcquireHandlers': 2, 'AcquireChecksMarkdown': 1, 'AcquireMatch': 1, 'AcquireRecheck': 1}
UP geography->corpus 18: {'Attestation': 2, 'AttestationTarget': 2, 'AliasKind': 2, 'AliasRepo': 2, 'Lint': 2, 'AttestationPut': 1, 'Chunk': 1, 'AttestationRepo': 1} | files {'EventCatalogHandlers': 5, 'Tree': 3, 'PlaceRepo': 3, 'EventMatcher': 2, 'EventRepo': 2, 'EventLinksResult': 1, 'PointAreas': 1, 'PointAreaRepo': 1}
UP writing->automation 12: {'ReviewGoal': 3, 'ReviewApproval': 3, 'GoalStages': 1, 'AgentProposals': 1, 'CompareRuns': 1, 'ReviewArticle': 1, 'AgentVerify': 1, 'GoalRow': 1} | files {'ReviewLedger': 4, 'ArticleReviewRepo': 3, 'Pages': 2, 'ArticleReviewView': 2, 'ArticleReviewStatus': 1}
UP corpus->writing 12: {'ArticleLinks': 4, 'CoverageEdge': 2, 'CritiquePairing': 2, 'Articles': 1, 'ResolvedArticle': 1, 'ArticleRepo': 1, 'TocEntry': 1} | files {'Lint': 3, 'SourceRepo': 3, 'LintRepo': 2, 'Rename': 1, 'ResolvedId': 1, 'SourceDelete': 1, 'SourceDeleteRepo': 1}
UP automation->operations 11: {'SettingSource': 2, 'Vacuum': 2, 'DriftKind': 1, 'BackupState': 1, 'BundleRange': 1, 'InitPlan': 1, 'VacuumPlan': 1, 'MergeApplyJob': 1} | files {'JobDetail': 3, 'JobRepo': 2, 'ReviewArticle': 1, 'ModelChains': 1, 'ModelPicker': 1, 'AutopilotService': 1, 'BundleGoal': 1, 'JobKind': 1}
UP geography->media 6: {'MediaRepo': 3, 'MediaThumb': 2, 'MediaGeoHit': 1} | files {'EventDetail': 1, 'EventRepo': 1, 'TileFeatures': 1, 'TileRepo': 1, 'MapTileService': 1, 'PlaceDetail': 1}
UP geography->automation 6: {'JobInfo': 2, 'JobDetail': 1, 'JobRepo': 1, 'BackgroundJob': 1, 'JobKind': 1} | files {'EventCatalogHandlers': 5, 'EventsApi': 1}
UP corpus->automation 6: {'Goal': 2, 'ListFilters': 2, 'QueueRepo': 1, 'Queue': 1} | files {'SourceDeleteRepo': 2, 'AliasKind': 1, 'AliasRepo': 1, 'Paging': 1, 'ListSql': 1}
UP writing->operations 5: {'DriftKind': 3, 'HtmlAtoms': 2} | files {'ArticleCoverage': 1, 'Coverage': 1, 'CoverageDrift': 1, 'HtmlRender': 1, 'Pages': 1}
UP corpus->media 5: {'AssetLinks': 2, 'MediaThumb': 1, 'AssetInput': 1, 'MediaProvider': 1} | files {'SourceWithFindings': 1, 'DeletePart': 1, 'NoteImport': 1, 'SourceCoverParams': 1, 'SourceDeleteRepo': 1}
UP acquisition->operations 4: {'Bundle': 1, 'PluginStatus': 1, 'Plugins': 1, 'BundleFormat': 1} | files {'HttpPolicy': 1, 'FetcherGeneration': 1, 'OaFetch': 1, 'Originals': 1}
UP media->automation 4: {'JobKind': 1, 'JobDetail': 1, 'JobInfo': 1, 'BackgroundJob': 1} | files {'MediaHandlers': 4}
UP geography->operations 4: {'OpenSide': 3, 'Validity': 1} | files {'Gazetteer': 1, 'Place': 1, 'PlaceName': 1, 'PlaceRelation': 1}
UP corpus->acquisition 2: {'RetractionNotice': 1, 'CalibreEntryCheck': 1} | files {'Assessment': 1, 'SourceDelete': 1}
UP corpus->operations 2: {'ReindexResult': 2} | files {'LintRepo': 1, 'SourceRepo': 1}
UP personal->automation 2: {'Goal': 2} | files {'ItemKind': 1, 'ItemTitles': 1}
UP geography->acquisition 2: {'GazetteerImporter': 1, 'CliopatriaImporter': 1} | files {'ImportRunner': 2}
UP writing->media 1: {'AssetLinks': 1} | files {'Pages': 1}
UP geography->writing 1: {'PassageFinder': 1} | files {'EventCatalogHandlers': 1}
UP media->operations 1: {'BundleRange': 1} | files {'BundleAsset': 1}
UP media->writing 1: {'Coverage': 1} | files {'OpenContext': 1}
UP personal->geography 1: {'Place': 1} | files {'ItemKind': 1}
UP personal->operations 1: {'Maintenance': 1} | files {'PersonalControllers': 1}
UP automation->personal 1: {'Note': 1} | files {'Queue': 1}
```

## An intermediate step: evidence in the library

With attestations, event link candidates and checks and leads in the library instead of the catalogue (everything else as final), catalogue → library was 46 references (`EventCatalogHandlers` 18, `EventsApi` 5, `EventCatalogRepo` 5, `EventDetail` 4, …) and the upward total 160. Moving evidence into the catalogue brought them to 11 and 125; making OCR a supporting area and naming research then gave the final 121.

## Co-change, the final grouping

commits used 408 (skipped 21: the reformat, the split and >60 files)
| | automation | library | catalogue | operations | platform | research | exchange | media | personal |
|---|---|---|---|---|---|---|---|---|---|
| **automation** | **152** | 52 | 25 | 24 | 27 | 31 | 18 | 10 | 1 |
| **library** | 52 | **145** | 41 | 33 | 31 | 37 | 20 | 15 | 1 |
| **catalogue** | 25 | 41 | **122** | 20 | 28 | 24 | 17 | 18 | 1 |
| **operations** | 24 | 33 | 20 | **95** | 15 | 16 | 7 | 8 | 1 |
| **platform** | 27 | 31 | 28 | 15 | **65** | 14 | 12 | 10 | 1 |
| **research** | 31 | 37 | 24 | 16 | 14 | **55** | 16 | 7 | 1 |
| **exchange** | 18 | 20 | 17 | 7 | 12 | 16 | **32** | 6 | 1 |
| **media** | 10 | 15 | 18 | 8 | 10 | 7 | 6 | **31** | 1 |
| **personal** | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | **3** |
| library | research | 0.23 | 37 | 145 / 55 |
| exchange | research | 0.23 | 16 | 32 / 55 |
| automation | library | 0.21 | 52 | 152 / 145 |
| catalogue | library | 0.18 | 41 | 122 / 145 |
| automation | research | 0.18 | 31 | 152 / 55 |
| catalogue | platform | 0.18 | 28 | 122 / 65 |
| library | platform | 0.17 | 31 | 145 / 65 |
| library | operations | 0.16 | 33 | 145 / 95 |
| catalogue | research | 0.16 | 24 | 122 / 55 |
| automation | platform | 0.14 | 27 | 152 / 65 |
| exchange | platform | 0.14 | 12 | 32 / 65 |
| catalogue | media | 0.13 | 18 | 122 / 31 |
| platform | research | 0.13 | 14 | 65 / 55 |
| exchange | library | 0.13 | 20 | 32 / 145 |

At the level of subdomains every pair is weak (Jaccard ≤ 0.23): features change together inside a subdomain more than across. Feature pairs with at least 4 commits together decided individual placements:

| feature | strongest partners (Jaccard, commits together) | reading |
|---|---|---|
| merge | bundle 0.50 (16), sources 0.23 (13), articles 0.21 (14), goals 0.19 (11), events 0.15 (10) | merge is about research records, with bundle: `exchange`, not operations |
| bundle | merge 0.50, sources 0.26, articles 0.18, events 0.12, goals 0.12 | same |
| article review ledger | pipelines 0.44 (15), agents 0.15, articles 0.12 | the review panel is automation; the article keeps only its review state |
| ingest | jobs 0.23 (8), sources 0.17 (10), acquisition 0.14 (12) | ingest is the library's, and runs as a platform job |
| jobs | ingest 0.23, worklist 0.14, merge 0.13, maintenance 0.12, bundle 0.11 | used by every subdomain that runs long work: platform |
| events | media 0.18 (13), places 0.16 (19), merge 0.15, sources 0.14 | catalogue, with pictures as a map layer |
| search | articles 0.29 (24), instance 0.21, sources 0.20, places 0.15 | the library's read side, used by every page |
| lint | places 0.12 (11), sources 0.07, media 0.07, events 0.06 | rules per subdomain, one runner |
| basemap | instance 0.18 (15) | a UI-facing feature without research data: operations |
| goals | managers 0.22 (20), agents 0.18 (21), autopilot 0.18, approvals 0.18, merge 0.19 | workflow state today (Q24) |
| converter | ingest 0.07, acquisition 0.04 | a mechanism the ingest uses: `util.ocr` |

