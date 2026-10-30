# Top-level objects without a same-named type: where each goes

Measured on master 58de5489: 1281 top-level `object`s in `modules/*/src/main`, of which **645** have no class, trait, enum or type of the same name in the same package (the brief counted 644 of 1313 with a slightly different scan). This file lists all 645 with the destination the design gives each. Rules O1–O7 and S1–S6 are in `../research-domain-model.md` §5; package names follow the subdomains of §3 (`domain.<subdomain>.<feature>`).

The *kind* column is the scan's first classification (name patterns, `val X = "…"` counts, nested types); "decided per object" rows were placed by hand. Destinations are proposals for the slice that touches the feature; a slice may refine one, recording why in its as-built section.

| outcome | objects |
|---|---|
| stays standalone | 320 |
| onto an existing type (companion, extension, instance) | 160 |
| dissolves into a package (functions to companions) | 51 |
| onto a new or renamed type | 37 |
| becomes an enum | 32 |
| moves to a plugin module | 29 |
| becomes a Controllers class | 10 |
| becomes @main def | 6 |

| module | package | object | lines | kind | destination | reason |
|---|---|---|---:|---|---|---|
| api | `api` | `AboutMarkdown` | 50 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `Acquire` | 305 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `AcquireApi` | 104 | endpoint group | stays: api.<subdomain>.<feature>.<Feature>Endpoints | a group of Tapir endpoint values, not about one type |
| api | `api` | `AcquireChecks` | 98 | plural helper | companion of AcquireCheck | helper named after the type it works on |
| api | `api` | `AcquireChecksMarkdown` | 39 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `AcquireMarkdown` | 259 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `Activity` | 73 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `ActivityMarkdown` | 53 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `AgentScheduler` | 111 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `AgentSchedulerMarkdown` | 65 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `Agents` | 473 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `AgentsApi` | 176 | endpoint group | stays: api.<subdomain>.<feature>.<Feature>Endpoints | a group of Tapir endpoint values, not about one type |
| api | `api` | `AgentsMarkdown` | 514 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `Aggregate` | 91 | decided per object | dissolve: `Counting` → own file; `AggregateRow.of` on the row type | functions about the aggregate row |
| api | `api` | `Aliases` | 17 | decided per object | `AliasKind` companion | checks an alias kind |
| api | `api` | `Api` | 1307 | endpoint group | stays: api.<subdomain>.<feature>.<Feature>Endpoints | a group of Tapir endpoint values, not about one type |
| api | `api` | `ApiIndex` | 36 | decided per object | stays: `@main def generateApiIndex` + `ApiIndex` renderer of `Api.all` | an entry point and its renderer |
| api | `api` | `ApprovalDigest` | 122 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `ApprovalDigestMarkdown` | 37 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `ArticleCoverage` | 190 | decided per object | `Coverage` companion (api.writing.articles) | computes over Coverage |
| api | `api` | `ArticleEdits` | 209 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `ArticleLinks` | 124 | plural helper | companion of ArticleLink | helper named after the type it works on |
| api | `api` | `ArticleMarkdown` | 142 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `ArticlePlans` | 68 | plural helper | companion of ArticlePlan | helper named after the type it works on |
| api | `api` | `Articles` | 166 | plural helper | companion of Article | helper named after the type it works on |
| api | `api` | `AssetKind` | 9 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `AssetLinkTarget` | 9 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `AssetLinks` | 112 | plural helper | companion of AssetLink | helper named after the type it works on |
| api | `api` | `AssetRelation` | 8 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `AttestationTarget` | 13 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `Autopilot` | 139 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `AutopilotMarkdown` | 102 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `Backup` | 137 | decided per object | dissolve: `PgTarget` top-level; functions to `BackupInfo` companion (api.operations.backup) | namespace + functions about BackupInfo |
| api | `api` | `BaseMapSource` | 15 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `BaseStyles` | 13 | decided per object | `Style` top-level in api.operations.basemap; list on its companion | namespace of one type |
| api | `api` | `Basemap` | 170 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `BasemapApi` | 76 | endpoint group | stays: api.<subdomain>.<feature>.<Feature>Endpoints | a group of Tapir endpoint values, not about one type |
| api | `api` | `BasemapMarkdown` | 72 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `Bundle` | 21 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `BundleEventSets` | 11 | decided per object | `BundleEvent` companion | about bundle events |
| api | `api` | `BundleFiles` | 42 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `BundleMarkdown` | 67 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `Candidates` | 129 | plural helper | companion of Candidate | helper named after the type it works on |
| api | `api` | `CandidatesMarkdown` | 34 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `ConfidenceStyle` | 49 | decided per object | `Confidence` extension in api (style is a wire/UI concern of Confidence) | about Confidence |
| api | `api` | `ConfigEdit` | 102 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `ConfigEditApi` | 35 | endpoint group | stays: api.<subdomain>.<feature>.<Feature>Endpoints | a group of Tapir endpoint values, not about one type |
| api | `api` | `ConfigEditMarkdown` | 55 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `CorrectionStatus` | 11 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `CorrectionTarget` | 8 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `Corrections` | 51 | plural helper | companion of Correction | helper named after the type it works on |
| api | `api` | `CorrespondingSource` | 12 | decided per object | `SourceRef` companion | about a source reference |
| api | `api` | `CritiquePairing` | 170 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `Critiques` | 191 | decided per object | `CritiquePair` companion (api.writing.articles) | 12 of 24 functions take CritiquePair |
| api | `api` | `Csv` | 41 | decided per object | stays: util.text.Csv | a format writer over any rows |
| api | `api` | `DataCredits` | 142 | plural helper | companion of DataCredit | helper named after the type it works on |
| api | `api` | `DatingMethod` | 4 | decided per object | becomes an enum (wire strings kept) | closed vocabulary |
| api | `api` | `EventFields` | 50 | decided per object | `CatalogRecord`/`EventPatch` companions | field lists of those types |
| api | `api` | `EventGeometry` | 41 | decided per object | new type `EventGeometry` (wire string) in api.catalogue.events | helpers on a WKT string |
| api | `api` | `EventKind` | 14 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `EventLeads` | 70 | plural helper | companion of EventLead | helper named after the type it works on |
| api | `api` | `EventLinksMarkdown` | 74 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `EventPrecision` | 53 | decided per object | becomes an enum `EventPrecision` with its rules | closed vocabulary + rules |
| api | `api` | `EventScale` | 86 | decided per object | becomes an enum `EventScale` | closed vocabulary + rules |
| api | `api` | `EventTaxonomy` | 130 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `EventTier` | 6 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `EventsApi` | 87 | endpoint group | stays: api.<subdomain>.<feature>.<Feature>Endpoints | a group of Tapir endpoint values, not about one type |
| api | `api` | `FilterDocs` | 102 | decided per object | `Filter` companion (`Filter.docs`) | describes Filter |
| api | `api` | `FormatMarkdown` | 32 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `Gazetteer` | 272 | decided per object | dissolve into api.catalogue.places: `GazRecord.fromJson` etc. on the record companions | parsers of gazetteer records |
| api | `api` | `Geocode` | 299 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `GoalPlans` | 119 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `GoalStages` | 14 | decided per object | `GoalStatus` enum (stages are its ordering) | about goal status |
| api | `api` | `GoalsExport` | 97 | decided per object | `GoalView` companion (`render`) | renders goal views |
| api | `api` | `HtmlAtoms` | 30 | decided per object | stays: util.markdown.HtmlAtoms | HTML escaping primitives |
| api | `api` | `Iiif` | 111 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `ImageSize` | 46 | decided per object | new type `ImageSize` (width,height) with these functions | functions over a width/height pair |
| api | `api` | `Ingest` | 22 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `IngestMarkdown` | 66 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `JobActions` | 15 | plural helper | companion of JobAction | helper named after the type it works on |
| api | `api` | `Label` | 4 | decided per object | `Label` newtype (tag/topic/region label) | about a label |
| api | `api` | `LabelRenames` | 18 | plural helper | companion of LabelRename | helper named after the type it works on |
| api | `api` | `LatinFold` | 84 | decided per object | new type `FoldedText` in util.text (`FoldedText.of`) | normalised text has meaning |
| api | `api` | `Lint` | 478 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `ListFilters` | 241 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `Lpf` | 341 | decided per object | dissolve into api.catalogue.places: `LpfDataset.fromJson`, `PlaceInput.fromLpf` | parser of Linked Places Format |
| api | `api` | `Managers` | 533 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `ManagersApi` | 177 | endpoint group | stays: api.<subdomain>.<feature>.<Feature>Endpoints | a group of Tapir endpoint values, not about one type |
| api | `api` | `ManagersMarkdown` | 165 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `MapStack` | 26 | decided per object | `Pane` top-level (web/api.ui) | namespace of one type |
| api | `api` | `Markdown` | 913 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `MediaProvider` | 59 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `MergeIssueDetail` | 101 | decided per object | `MergeIssue` companion | describes a merge issue |
| api | `api` | `MergeMarkdown` | 201 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `MergeResolutionsText` | 67 | decided per object | `MergeResolutions` companion (`render`/`parse`) | text form of MergeResolutions |
| api | `api` | `ModelChains` | 308 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `OpsApi` | 106 | endpoint group | stays: api.<subdomain>.<feature>.<Feature>Endpoints | a group of Tapir endpoint values, not about one type |
| api | `api` | `Paging` | 89 | decided per object | dissolve: `Page` top-level; functions on `Page`/`PageSize` companions | namespace + page arithmetic |
| api | `api` | `Palette` | 46 | decided per object | stays: api.ui.Palette (CSS token names) | a table of token names, AGENTS.md styling rule |
| api | `api` | `PassagesMarkdown` | 32 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `PeriodO` | 202 | decided per object | dissolve into api.catalogue.periods: `PeriodODataset.fromJson` | parser of PeriodO JSON |
| api | `api` | `PeriodsMarkdown` | 52 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `PlaceEdits` | 97 | decided per object | `PlaceAdd`/`ManualPlaceName` companions | checks on those inputs |
| api | `api` | `PlaceOverrides` | 84 | plural helper | companion of PlaceOverride | helper named after the type it works on |
| api | `api` | `PlaceShape` | 14 | decided per object | becomes an enum `PlaceShape` | closed vocabulary |
| api | `api` | `PlanQuota` | 328 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `PluginsMarkdown` | 38 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `Plural` | 8 | decided per object | stays: util.text.Plural | English word forms, no domain type |
| api | `api` | `PresetMenu` | 39 | decided per object | `PresetRegion` companion | builds the preset regions menu |
| api | `api` | `Provenance` | 4 | decided per object | becomes an enum `Provenance` (wire strings kept) | closed vocabulary |
| api | `api` | `Query` | 217 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `Queue` | 323 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `ReloadMarkdown` | 22 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `Rename` | 31 | decided per object | `RenameRequest` companion | checks a rename request |
| api | `api` | `RepairAcquire` | 63 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `RunCompare` | 102 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `SiteTypes` | 338 | plural helper | companion of SiteType | helper named after the type it works on |
| api | `api` | `SourceDelete` | 99 | decided per object | `SourceDeletion` companion | about a source deletion |
| api | `api` | `SourceKinds` | 53 | decided per object | becomes an enum `SourceKind` | closed vocabulary |
| api | `api` | `SourceSection` | 14 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `Stalls` | 174 | plural helper | companion of Stall | helper named after the type it works on |
| api | `api` | `StallsMarkdown` | 18 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `Storage` | 94 | decided per object | `StorageReport` companion | computes the report |
| api | `api` | `StorageAdvice` | 23 | decided per object | `StorageReport` companion | advice is derived from the report |
| api | `api` | `StorageMarkdown` | 26 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `StrictJson` | 76 | decided per object | stays: util.json.StrictJson | JSON-schema tightening for any schema |
| api | `api` | `TileProps` | 49 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| api | `api` | `TilesApi` | 66 | endpoint group | stays: api.<subdomain>.<feature>.<Feature>Endpoints | a group of Tapir endpoint values, not about one type |
| api | `api` | `Tree` | 279 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `UiApi` | 31 | endpoint group | stays: api.<subdomain>.<feature>.<Feature>Endpoints | a group of Tapir endpoint values, not about one type |
| api | `api` | `UiNotices` | 59 | namespace object | dissolves: nested types become top-level in package api.<subdomain>.<feature> | a namespace object stands in for a package |
| api | `api` | `UpstreamQuotaMarkdown` | 13 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| api | `api` | `Vacuum` | 39 | decided per object | `VacuumPlan` companion | plans a vacuum |
| api | `api` | `ValueCodecs` | 30 | given holder | stays: given holder in util.json / util.database | type class instances for several types |
| api | `api` | `Version` | 8 | decided per object | `BuildStamp` companion | version text of the build |
| api | `api` | `VocabularyCodecs` | 93 | given holder | stays: given holder in util.json / util.database | type class instances for several types |
| api | `api` | `Wcag` | 24 | decided per object | stays: util.ui.Wcag (contrast maths) | colour maths, no domain type |
| api | `api` | `WebPaths` | 17 | decided per object | stays: api.ui.WebPaths (URL builders of the web UI) | a route table |
| api | `api` | `WireEnum` | 13 | decided per object | stays: util.json.WireEnum (derives codecs for wire enums) | codec derivation helper |
| api | `api` | `Wording` | 235 | decided per object | split: each sentence to the companion of the type it words (`AgentMode`, `SourceStatus`, …) | functions about those types |
| api | `api` | `Write` | 241 | decided per object | dissolve: `Write.Code*` → enum `WriteCode`; checks → `SourcePatch`/`FindingInput` companions | namespace of codes + input checks |
| api | `api` | `YearFmt` | 34 | decided per object | `Year` extension in api (`year.show`) | formats a Year |
| api | `api` | `Years` | 54 | plural helper | companion of Year | helper named after the type it works on |
| api | `api.personal` | `PersonalApi` | 100 | endpoint group | stays: api.<subdomain>.<feature>.<Feature>Endpoints | a group of Tapir endpoint values, not about one type |
| api | `api.personal` | `PersonalIds` | 9 | decided per object | `ItemKey`/`PageSize` companions | limits and id rules |
| api | `api.personal` | `PersonalMarkdown` | 51 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| cli | `cli` | `AcquireCmd` | 258 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `ActivityCmd` | 41 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `AgentCmd` | 403 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `AgentStateCmd` | 35 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `ArticleCli` | 431 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `ArticlePatches` | 141 | plural helper | companion of ArticlePatch | helper named after the type it works on |
| cli | `cli` | `AssetLinksCmd` | 47 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `AttestationsCmd` | 55 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `BasemapCmd` | 102 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `BookmarksCmd` | 51 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `BundleCmd` | 113 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `CalibreAudit` | 79 | decided per object | `AuditRecord` companion (cli.library) | audits a book |
| cli | `cli` | `CalibreCmd` | 563 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `CalibreJson` | 115 | decided per object | dissolve: nested types top-level in cli.library; parsers on their companions | namespace |
| cli | `cli` | `CandidatesCmd` | 52 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `ConfigCmd` | 56 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `DeleteCmd` | 73 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `EventsCmd` | 276 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `ExitCode` | 16 | decided per object | becomes an enum `ExitCode` | closed set of codes |
| cli | `cli` | `FileFormat` | 22 | decided per object | becomes an enum `FileFormat` | closed vocabulary |
| cli | `cli` | `FilterFlags` | 57 | decided per object | stays: decline options group (cli.search) | a CLI option group |
| cli | `cli` | `GoalsCmd` | 144 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `IngestCmd` | 141 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `JobFollow` | 70 | decided per object | `JobInfo` extension in cli (`follow`) | polls a job |
| cli | `cli` | `KindSources` | 427 | decided per object | `PluginKind` companion (template sources per kind) | about PluginKind |
| cli | `cli` | `LabelRenameCmd` | 34 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `ListOpts` | 63 | decided per object | stays: decline options group | a CLI option group |
| cli | `cli` | `LocalStorage` | 61 | decided per object | `About` extension in cli | reads the local storage of an About |
| cli | `cli` | `Main` | 640 | entry point | top-level @main def | rule #7908 |
| cli | `cli` | `ManagerCmd` | 388 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `MediaCmd` | 188 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `MediaCovers` | 103 | decided per object | `CalibreBook` extension in cli.library | covers for a book |
| cli | `cli` | `MergeCmd` | 249 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `Notes` | 93 | decided per object | `Note` companion (cli) | about notes |
| cli | `cli` | `NotesCmd` | 86 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `Ops` | 306 | decided per object | stays: cli command group | a CLI command group |
| cli | `cli` | `Output` | 26 | decided per object | stays: cli edge (the one place that prints) | AGENTS.md: printing only in cli Main |
| cli | `cli` | `PageCmd` | 23 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `Patches` | 144 | decided per object | `SourcePatch`/`EdgeInput` companions (cli parsing of flags) | builds those inputs |
| cli | `cli` | `PeriodsCmd` | 116 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `PlacesCmd` | 435 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `PluginArtifacts` | 26 | plural helper | companion of PluginArtifact | helper named after the type it works on |
| cli | `cli` | `PluginAuthoring` | 126 | decided per object | `PluginId` companion + `TemplateFile` companion | about plugin ids/templates |
| cli | `cli` | `PluginCmd` | 234 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `PluginTemplate` | 441 | decided per object | `PluginTemplate.Request` top-level; renders on its companion | namespace + one type |
| cli | `cli` | `RenameCmd` | 54 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `Settings` | 19 | decided per object | stays: cli command group | a CLI command group |
| cli | `cli` | `SettingsCmd` | 168 | CLI command group | stays: decline command group (cli.<feature>) | a CLI command group, not about one type |
| cli | `cli` | `TargetFlags` | 59 | decided per object | stays: decline options group | a CLI option group |
| cli | `cli` | `WorkingDir` | 31 | decided per object | stays: cli edge (the one place that reads user.dir, passed in) | rule 12 boundary |
| config | `config` | `ConfigLayers` | 172 | decided per object | stays: config root (layering of HOCON files) | works on sconfig Config, no type of ours |
| config | `config` | `ConfigReload` | 57 | decided per object | `ReloadReport` companion (config side) or `LoadedConfig.reload` | reloads a LoadedConfig |
| config | `config` | `EnvOverrides` | 172 | decided per object | `LoadedConfig` companion (`LoadedConfig.envOverrides`) | part of loading |
| config | `config` | `HotPlan` | 74 | decided per object | `HotNode` companion | plans over hot nodes |
| config | `config` | `UserPaths` | 72 | decided per object | new type `UserPaths` (case class of the resolved paths, built once in Main) | rule 12: no user.home reads below Main |
| config | `config.edit` | `ConfigTextEdit` | 183 | decided per object | `EditedText` companion | edits a config text |
| config | `config.edit` | `SettingPolicy` | 54 | decided per object | `SettingEdit` companion | checks an edit |
| config | `config.edit` | `SettingsReader` | 70 | decided per object | `SettingReading` companion | reads a setting |
| core | `domain` | `AcquireMatch` | 188 | decided per object | `AcquireMatch.Decision` top-level; functions on its companion (domain.library.acquisition) | decides a match |
| core | `domain` | `AcquireRecheck` | 220 | decided per object | dissolve into domain.library.acquisition.recheck: functions to `LastCheck`/`Candidate` companions | namespace of 9 types |
| core | `domain` | `AgentProposals` | 164 | decided per object | `Proposal` companion (domain.automation.pipelines) | about proposals |
| core | `domain` | `AgentVerify` | 208 | decided per object | `QuoteMatch`/`ToolUse` companions | verifies quotes and tool uses |
| core | `domain` | `ApprovalModes` | 7 | plural helper | companion of ApprovalMode | helper named after the type it works on |
| core | `domain` | `ArtInstitute` | 62 | decided per object | moves to plugins/media: `AicObject.fromJson` | an upstream parser |
| core | `domain` | `ArticleReviewStatus` | 160 | decided per object | `ReviewState` companion (domain.research.articles): the article keeps its review state; the ledger is automation's | status derived from the ledger |
| core | `domain` | `AutoEnrich` | 60 | decided per object | `PriorRun` companion (domain.automation.autopilot) | decides on prior runs |
| core | `domain` | `AutopilotPolicy` | 445 | decided per object | dissolve into domain.automation.autopilot: verdicts on `PlannedFacts`/`DraftFacts`/`GoalPlanFacts` | rules about those facts |
| core | `domain` | `BundlePlan` | 182 | decided per object | `BundlePlan.Decision` top-level; plan on its companion (domain.exchange.bundle) | namespace |
| core | `domain` | `CatalogDating` | 76 | decided per object | moves to `upstreams` (library): the dating rules the events plugin and the host's `PluginEvents.derive` share | dating of catalogue records |
| core | `domain` | `CatalogMerge` | 136 | decided per object | `Placement` companion (domain.catalogue.events) | places a record |
| core | `domain` | `CatalogProvider` | 48 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| core | `domain` | `Chunker` | 96 | decided per object | `util.ocr.Chunker` (the mechanical split); `Chunk` stays the library's stored chunk | produces Chunks |
| core | `domain` | `Citekeys` | 10 | plural helper | companion of Citekey | helper named after the type it works on |
| core | `domain` | `ClevelandArt` | 71 | decided per object | moves to plugins/media: `ClevelandObject.fromJson` | an upstream parser |
| core | `domain` | `CodexRetention` | 43 | decided per object | moves with ai-cli: `FileInfo` companion | retention of Codex files |
| core | `domain` | `Commons` | 177 | decided per object | moves to `upstreams` (library): `CommonsFile.fromJson` + rights | Commons API parser shared by media plugins |
| core | `domain` | `CommonsDepicts` | 66 | decided per object | moves to plugins/media | an upstream query builder |
| core | `domain` | `CompareRuns` | 378 | decided per object | dissolve into domain.automation.agents: functions on `Run`/`Step` companions | namespace of 14 types |
| core | `domain` | `CompileArticle` | 432 | decided per object | dissolve into domain.automation.pipelines.compile: `Claim`/`Evidence` companions | namespace of 10 types |
| core | `domain` | `CoreQuota` | 188 | decided per object | dissolve into domain.library.acquisition.fetching: `QuotaState` (was State) with its methods | namespace; methods about the state |
| core | `domain` | `DeepTime` | 17 | decided per object | `Year`/`Period` companions in values (`Year.fromLong`, `Period.span`) | about Year/Period |
| core | `domain` | `Diff3` | 118 | decided per object | stays: util.text.Diff3 | a text algorithm, no domain type |
| core | `domain` | `EnrichSource` | 603 | decided per object | dissolve into domain.automation.pipelines.enrich: `StatedPeriod`/`FindingProposal` companions | namespace of 19 types |
| core | `domain` | `Europeana` | 222 | decided per object | moves to plugins/media: `EuropeanaItem.fromJson`, `Exclusions` companion | an upstream parser |
| core | `domain` | `EventMatcher` | 108 | decided per object | `NearFinding` companion (domain.catalogue.evidence) | scores near findings |
| core | `domain` | `EventProviders` | 467 | decided per object | moves to plugins/events: `GvpRecord`/`NoaaRecord`/`WikidataEvent` parsers | upstream parsers |
| core | `domain` | `ExternalIds` | 30 | plural helper | companion of ExternalId | helper named after the type it works on |
| core | `domain` | `FigurePick` | 116 | decided per object | `Figure` companion (domain.media) | picks figures |
| core | `domain` | `FilterInference` | 102 | decided per object | `FilterAnswer` companion (domain.automation.pipelines) | reads an answer into a filter |
| core | `domain` | `FreeTextDates` | 69 | decided per object | `StatedYear` companion (values/domain) — `StatedYear.parse(text)` | parses free-text dates |
| core | `domain` | `GoalFocus` | 459 | decided per object | dissolve into domain.automation.goals.focus: `FocusFacts` (was Facts) methods | 17 of 28 functions take Facts |
| core | `domain` | `Highlight` | 123 | decided per object | new type `Snippet` (domain.library.search) with `Snippet.highlight` | produces highlighted snippets |
| core | `domain` | `HintProposals` | 121 | plural helper | companion of HintProposal | helper named after the type it works on |
| core | `domain` | `HttpPolicy` | 130 | decided per object | `OutboundPolicy` type in util.http (Step/Trust top-level) | policy of outbound HTTP |
| core | `domain` | `IngestBacklog` | 167 | decided per object | `BacklogEntry` companion (domain.library.acquisition) | orders the backlog |
| core | `domain` | `Jats` | 378 | decided per object | moves to `upstreams` (library): `JatsDocument.parse` (host converter also uses it via the lib) | a document format parser |
| core | `domain` | `LibraryOfCongress` | 101 | decided per object | moves to plugins/media: `LocItem.fromJson` | an upstream parser |
| core | `domain` | `ManagerPolicy` | 617 | decided per object | dissolve into domain.automation.managers: `ManagerDefaults`/`ManagerConfig` companions, `CronStep` top-level | rules about managers |
| core | `domain` | `MediaLicence` | 104 | decided per object | becomes the shared `Licence` type in `com.histmersion.domain` with `classify`/`ofUrl`/`allowed` on its companion (§3.7) | classifies licences |
| core | `domain` | `MediaMatch` | 150 | decided per object | `PlaceInfo` companion → `MediaMatch` becomes a type (rule × basis) | matches records to places |
| core | `domain` | `MediaRoles` | 19 | decided per object | `MediaRole` enum (values) | about MediaRole |
| core | `domain` | `MediaText` | 51 | decided per object | `MediaRecord` companion | text of a media record |
| core | `domain` | `MergeFold` | 513 | decided per object | dissolve into domain.exchange.merge: `PlanOutcome`/`ArticleOutcome` companions | namespace |
| core | `domain` | `MergePlanner` | 2425 | decided per object | dissolve into domain.exchange.merge.plan: `MergePlan.of(...)` + per-entity companions (`LedgerEntry`, `Row`) | 2425 lines, 9 nested types |
| core | `domain` | `MergeText` | 299 | decided per object | `AuthorAgreement`/`MinHash` top-level in domain.exchange.merge | namespace |
| core | `domain` | `MetMuseum` | 64 | decided per object | moves to plugins/media: `MetObject.fromJson` | an upstream parser |
| core | `domain` | `OpenContext` | 215 | decided per object | moves to plugins/media: `OpenContextItem.fromJson` | an upstream parser |
| core | `domain` | `PassageFinder` | 244 | decided per object | `Passage` companion (domain.library.sources) | finds passages |
| core | `domain` | `PlaceReview` | 221 | decided per object | dissolve into domain.catalogue.places.review: `Evidence`/`Proposal` companions | namespace |
| core | `domain` | `PlanArticles` | 161 | decided per object | `ArticlesAnswer` companion (domain.automation.pipelines) | reads an answer |
| core | `domain` | `PlanGoal` | 189 | decided per object | `PlanAnswer` companion (domain.automation.goals) | reads an answer |
| core | `domain` | `PlanReadSchedule` | 28 | decided per object | `ReadSchedule` companion | plans a read schedule |
| core | `domain` | `PleiadesLinks` | 83 | decided per object | moves to plugins/media: `PleiadesLink` companion | an upstream parser |
| core | `domain` | `PointAreas` | 100 | decided per object | `PointArea` type (was Listed/Group) companion (domain.catalogue.places) | about point areas |
| core | `domain` | `Read` | 31 | decided per object | stays: util.json.Read (cursor helpers for parsers) | JSON cursor helpers, no domain type |
| core | `domain` | `RepositoryLanding` | 324 | decided per object | moves to `upstreams` (library): `LandingCandidate` companion, `LandingRules` | landing-page rules (fetcher + BrowserDownload) |
| core | `domain` | `ReviewArticle` | 2013 | decided per object | dissolve into domain.automation.review: `Reviewer`, `Verdict`, `ReviewFinding` companions | 2013 lines, 36 nested types |
| core | `domain` | `ReviewLedger` | 601 | decided per object | dissolve into domain.automation.review: `Ledger` methods | 17 of 49 functions take Ledger |
| core | `domain` | `SchedulerPolicy` | 458 | decided per object | dissolve into domain.automation.scheduler: `Scored`/`SchedulerSettings` methods | rules about scored candidates |
| core | `domain` | `Scheduling` | 218 | decided per object | `Pick`/`Ranking` companions (domain.automation.scheduler) | namespace |
| core | `domain` | `Smithsonian` | 155 | decided per object | moves to plugins/media: `SmithsonianObject.fromJson` | an upstream parser |
| core | `domain` | `SourceMatcher` | 240 | decided per object | `SourceMatch` type (was Pair/Evidence/Outcome) companions (domain.exchange.merge) | matches sources |
| core | `domain` | `TextAnchor` | 95 | decided per object | `Anchor` top-level with methods (domain.research.articles) | about an anchor |
| core | `domain` | `TileFeatures` | 90 | decided per object | `TileCell` companion (domain.catalogue.maptiles) | features of a cell |
| core | `domain` | `VectorTileWriter` | 76 | decided per object | stays: util.tiles.VectorTileWriter | MVT encoding, an external format |
| core | `domain` | `WatchSchedules` | 97 | decided per object | `GoalSchedule` companion (domain.automation.goals) | plans schedules |
| core | `domain` | `Wikidata` | 73 | decided per object | moves to `upstreams` (library): `WikidataItem`/`Statement` companions | Wikidata entity parser |
| core | `domain.automation.activity` | `ActivityRules` | 100 | decided per object | `ActivityQuery`/`Activity.Entry` companions (domain.automation.activity) | pilot code brought in line |
| core | `domain.platform.jobs` | `JobControlRules` | 41 | decided per object | `JobAction` companion (domain.platform.jobs) | pilot code brought in line |
| core | `domain.catalogue.maptiles` | `UndatedCells` | 64 | decided per object | `GridCell` companion (domain.catalogue.maptiles) | about grid cells |
| db | `db` | `AcquireCheckRepo` | 87 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `AcquireRepo` | 234 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `AgentRepo` | 950 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `AgentStateRepo` | 33 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `AggregateRepo` | 59 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `AliasRepo` | 46 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `ArticleRepo` | 466 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `ArticleReviewRepo` | 99 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `AssetLinkRepo` | 51 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `AssetMetaRepo` | 85 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `AssetRepo` | 188 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `AttestationRepo` | 101 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `AutopilotRepo` | 126 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `BackupRepo` | 18 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `BundleExport` | 108 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `BundleImport` | 1151 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `BundleRepo` | 1015 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `CandidateRepo` | 148 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `CorrectionRepo` | 121 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `CoverageRepo` | 50 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `CritiqueRepo` | 94 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `Db` | 223 | database primitive | stays: com.histmersion.util.database (db module) | database primitive shared by every repository |
| db | `db` | `EventCatalogRepo` | 558 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `EventRepo` | 542 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `Figures` | 78 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `FilterSql` | 590 | database primitive | stays: com.histmersion.util.database (db module) | database primitive shared by every repository |
| db | `db` | `FocusRepo` | 181 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `GazetteerRepo` | 537 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `Geocoder` | 258 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `GoalLinkRepo` | 228 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `GoalRepo` | 158 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `Importer` | 69 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `JobRepo` | 271 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `Jsonb` | 30 | database primitive | stays: com.histmersion.util.database (db module) | database primitive shared by every repository |
| db | `db` | `Keyset` | 65 | database primitive | stays: com.histmersion.util.database (db module) | database primitive shared by every repository |
| db | `db` | `LandRepo` | 56 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `LintRepo` | 337 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `ListSql` | 49 | database primitive | stays: com.histmersion.util.database (db module) | database primitive shared by every repository |
| db | `db` | `ManagerRepo` | 1138 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `MediaRepo` | 986 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `MergeRepo` | 225 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `MergeState` | 401 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `Migrate` | 192 | database primitive | stays: com.histmersion.util.database (db module) | database primitive shared by every repository |
| db | `db` | `ModelSettingsRepo` | 64 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `PeriodRepo` | 103 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `PeriodSql` | 91 | database primitive | stays: com.histmersion.util.database (db module) | database primitive shared by every repository |
| db | `db` | `PlaceOverrideRepo` | 232 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `PlaceRepo` | 1710 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `PlaceReviewRepo` | 224 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `PlanQuotaRepo` | 118 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `PointAreaRepo` | 246 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `QueryRepo` | 141 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `QueueRepo` | 137 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `ReviewLedgerRepo` | 28 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `Search` | 140 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `SiteTypesSql` | 47 | database primitive | stays: com.histmersion.util.database (db module) | database primitive shared by every repository |
| db | `db` | `SourceDeleteRepo` | 235 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `SourceRepo` | 994 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `StatsRepo` | 67 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `TileRepo` | 232 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `TreeRepo` | 167 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `UiRepo` | 74 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `UndatedSiteRepo` | 128 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `UpstreamQuotaRepo` | 49 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `VacuumRepo` | 43 | repository | stays: repository, `private[<feature>]` in domain.<subdomain>.<feature> (server module) | a repository is the feature's store, not a function about one type |
| db | `db` | `Vocab` | 18 | database primitive | stays: com.histmersion.util.database (db module) | database primitive shared by every repository |
| files | `files` | `ArticleNote` | 155 | decided per object | `ArticleNote` type (the note file) companion in files | the article note file |
| files | `files` | `BundleFormat` | 152 | decided per object | `BundleManifest` companion | manifest format |
| files | `files` | `BundleReader` | 108 | decided per object | `LoadedBundle` (was Loaded) companion | reads a bundle |
| files | `files` | `BundleTar` | 182 | decided per object | stays: util.files.BundleTar (tar container) | an archive format |
| files | `files` | `CoverChoice` | 23 | decided per object | `CoverPick` (was Pick) top-level | namespace of one type |
| files | `files` | `DirSize` | 104 | decided per object | `DirSize.Result` → `DirectorySize` type with `measure` | about a directory size |
| files | `files` | `Export` | 71 | decided per object | `SourceNote` companion | writes the source note |
| files | `files` | `Frontmatter` | 189 | decided per object | new type `Frontmatter` (parsed YAML map) with read/write | a document header |
| files | `files` | `MergeResolutionsFile` | 100 | decided per object | `MergeResolutions` file companion | file form |
| files | `files` | `OpenLibrary` | 36 | decided per object | `CoverPick` companion | cover URLs |
| files | `files` | `PlaceSeed` | 39 | decided per object | `PlaceInput` companion (files) | reads place seeds |
| files | `files` | `PluginKit` | 319 | decided per object | dissolve: `KitManifest`, `PluginSet` companions | namespace |
| files | `files` | `SafePath` | 48 | decided per object | new type `SafePath` (a relative path that cannot escape) | a checked path is a type |
| plugin-api | `plugin` | `PluginConfig` | 26 | decided per object | stays: plugin-api readers (SPI surface) | given holder for plugin config |
| plugin-api | `plugin` | `Spi` | 16 | decided per object | stays: plugin-api `Spi.Version` | SPI constant |
| plugin-fixtures | `fixtures.hellolib` | `Shout` | 7 | decided per object | stays (test fixture) | fixture |
| plugin-host | `pluginhost` | `AcquireParse` | 390 | decided per object | moves to `upstreams` (library), split per upstream: `UnpaywallRecord`, `OpenAlexWork`, `CoreWork`, `ArchiveItem`, `EuropePmcHit` companions | 35 parsers of 7 upstreams |
| plugin-host | `pluginhost` | `Challenge` | 26 | decided per object | `HttpAnswer` companion (`isChallenge`) | about a response |
| plugin-host | `pluginhost` | `MediaSettings` | 40 | decided per object | `MediaConfig` view companion (plugin-host) | reads media settings |
| plugin-host | `pluginhost` | `OutboundTls` | 134 | decided per object | `TlsConfig` companion → util.http | builds TLS from config |
| plugin-host | `pluginhost` | `PluginAi` | 27 | decided per object | `AiProvider` host-side extension (plugin-host) | adapts a provider |
| plugin-host | `pluginhost` | `PluginEvents` | 162 | decided per object | `CatalogueEvent` host-side extension (plugin-host) | checks and dates SPI events |
| plugin-host | `pluginhost` | `PluginHost` | 688 | decided per object | stays: plugin-host facade (discover/build) | the host entry, over several types |
| plugin-host | `pluginhost` | `PluginMedia` | 104 | decided per object | `MediaItem` host-side extension (plugin-host) | checks SPI media items |
| plugin-host | `pluginhost` | `Politeness` | 36 | decided per object | `HttpAnswer` companion (`retryAfter`) | about a response |
| plugin-host | `pluginhost` | `ReservedNames` | 40 | decided per object | `PluginId` newtype companion (`reserved`) | names a plugin may not take |
| plugin-host | `pluginhost` | `SpiValues` | 115 | decided per object | stays: plugin-host SPI↔host value conversions (chimney-iron where possible) | conversions between two layers |
| plugin-host | `pluginhost` | `Url` | 11 | decided per object | new type `Url` (util.http) with builder | a URL is a type |
| plugin-host | `pluginhost` | `WorkLookup` | 66 | decided per object | `WorkRef` companion | looks a work up |
| plugin-resolver | `pluginresolver` | `LibDirectory` | 60 | decided per object | `LibDirectory` type (the lib dir) companion | about the lib directory |
| plugin-resolver | `pluginresolver` | `PluginResolver` | 151 | decided per object | `@main def` + `ResolveRequest` companion | entry point |
| plugin-resolver | `pluginresolver` | `Pom` | 77 | decided per object | new type `Pom` (parsed POM) with readers | about a POM |
| plugin-resolver | `pluginresolver` | `Resolution` | 80 | decided per object | `ModuleCoordinates` companion | resolves coordinates |
| plugin-testkit | `plugin.testkit` | `Recordings` | 15 | decided per object | stays: testkit API (published contract) | plugin testkit surface |
| plugin-testkit | `plugin.testkit.internal` | `ProjectPaths` | 15 | decided per object | stays: testkit API (published contract) | plugin testkit surface |
| plugin-vaults | `plugins.vaults` | `KeePassXcSession` | 46 | decided per object | `KeePassXcVault` companion | about the vault |
| plugin-vaults | `plugins.vaults` | `Subprocess` | 174 | decided per object | stays: plugins/vaults util (process runner) | process I/O, no domain type |
| plugin-vaults | `plugins.vaults` | `Vaults` | 54 | decided per object | stays: vault ids table of the vaults plugin | constants of one plugin |
| plugin-vaults | `plugins.vaults` | `YubiKey` | 114 | decided per object | `Slot` top-level with methods | about a slot |
| server | `domain.automation.activity` | `ActivityRepo` | 138 | decided per object | stays: repository (private[activity]) | pilot repo |
| server | `domain.personal` | `BookmarkRepo` | 97 | decided per object | stays: repository (private[personal]) | pilot repo |
| server | `domain.personal` | `ItemTitles` | 45 | decided per object | `ItemKind` companion | titles by item kind |
| server | `domain.personal` | `NoteRepo` | 84 | decided per object | stays: repository (private[personal]) | pilot repo |
| server | `domain.personal` | `PersonalRules` | 83 | decided per object | `Target`/`BookmarkLabel` companions | pilot code brought in line |
| server | `domain.personal` | `PersonalTools` | 67 | handler / tools | becomes <Feature>Controllers (class) + endpoint wiring | rule 13: controllers take services |
| server | `domain.personal` | `ReadingRepo` | 141 | decided per object | stays: repository (private[personal]) | pilot repo |
| server | `domain.settings` | `SettingsViews` | 60 | decided per object | `SettingsSnapshot` companion | views of a snapshot |
| server | `server` | `Article` | 357 | decided per object | server.web page builder → `ArticlePage` companion | renders an article page |
| server | `server` | `BackgroundJob` | 182 | decided per object | `BackgroundJob` becomes a class (runner) with `Slot` top-level (domain.platform.jobs) | runs jobs |
| server | `server` | `ConfigViews` | 37 | decided per object | `UiSettings`/`PluginsReport` companions (server edge, Chimney transformers) | config → wire views |
| server | `server` | `HtmlRender` | 100 | decided per object | stays: util.markdown.HtmlRender | HTML rendering of Markdown |
| server | `server` | `IiifHandler` | 176 | handler / tools | becomes <Feature>Controllers (class) + endpoint wiring | rule 13: controllers take services |
| server | `server` | `ListQueries` | 58 | decided per object | `ApprovalFilter`/`RunFilter` companions | parses list filters |
| server | `server` | `Main` | 350 | entry point | top-level @main def | rule #7908 |
| server | `server` | `MapTiles` | 51 | decided per object | `TileLayer` companion (domain.catalogue.maptiles) | about tile layers |
| server | `server` | `MarkdownFlavor` | 25 | decided per object | stays: util.markdown.MarkdownFlavor | flexmark configuration |
| server | `server` | `MarkdownHtml` | 12 | decided per object | stays: util.markdown.MarkdownHtml | Markdown → HTML |
| server | `server` | `MarkdownLint` | 1336 | decided per object | dissolve into domain.research.articles.lint: `FormatFinding` (was Finding)/`Edit` companions, `LintContext` type | 1336 lines; rules about findings and edits |
| server | `server` | `McpOut` | 28 | decided per object | stays: server MCP adapter (ApiError → tool result) | adapter |
| server | `server` | `MergeSettingsViews` | 8 | decided per object | `MergeConfig` → Chimney transformer given (server edge) | a conversion |
| server | `server` | `OpsRoutes` | 25 | handler / tools | becomes <Feature>Controllers (class) + endpoint wiring | rule 13: controllers take services |
| server | `server` | `OpsTools` | 225 | handler / tools | becomes <Feature>Controllers (class) + endpoint wiring | rule 13: controllers take services |
| server | `server` | `Pages` | 291 | decided per object | server.web: `PageBuilder` companion | renders pages |
| server | `server` | `PgRelocation` | 113 | decided per object | `PgRelocation.Result` → `Relocation` type (domain.operations.maintenance) | about relocation |
| server | `server` | `ServerArgs` | 24 | decided per object | new type `ServerArgs` (parsed CLI args of the server) | arguments are a type |
| server | `server` | `Static` | 15 | decided per object | stays: server.web static assets route | adapter |
| server | `server` | `StorageProbe` | 225 | decided per object | `StorageLocation` (was Location) companion (domain.operations.maintenance) | probes a location |
| server | `server` | `StoredOriginals` | 66 | decided per object | `OriginalStore` service (domain.library.ingest) | a store, trait + Implementation + InMemory |
| server | `server` | `Supervisor` | 335 | decided per object | `Layout` top-level + `Supervisor` class (server) | supervises child processes |
| server | `server` | `TimeZoneCheck` | 68 | decided per object | `Probe` top-level (domain.operations.instance self-check) | a self-check |
| server | `server.acquire` | `Clients` | 118 | decided per object | `AcquireClients` companion | builds upstream clients |
| server | `server.acquire` | `FetcherGeneration` | 33 | decided per object | `Generation` extension (plugin host glue) | about a generation |
| server | `server.acquire` | `Indexes` | 35 | decided per object | `ScholarlyIndex` companion | lists the indexes |
| server | `server.acquire` | `NoAlerts` | 12 | decided per object | `CitationAlerts.None` (companion value) | an implementation of CitationAlerts |
| server | `server.acquire` | `OaFetch` | 28 | decided per object | host fetch wiring → `FetchChain` companion | builds the chain |
| server | `server.acquire` | `Spool` | 47 | decided per object | `util.ocr.Spool` (the converter's working files) | a store |
| server | `server.acquire.fetchers` | `BuiltinFetchers` | 82 | built-in plugin list | moves to its plugin module (plugin slice) | plugin separation |
| server | `server.acquire.fetchers` | `Fetching` | 32 | decided per object | moves to plugins/fetchers (`FetchedFile` extension) | fetch helpers of the plugins |
| server | `server.agents` | `AgentGenerator` | 400 | decided per object | `AgentDefinitions` companion / `Defs` top-level (domain.automation.agents) | renders definitions |
| server | `server.agents` | `AgentsRoutes` | 37 | handler / tools | becomes <Feature>Controllers (class) + endpoint wiring | rule 13: controllers take services |
| server | `server.agents` | `AgentsTools` | 816 | handler / tools | becomes <Feature>Controllers (class) + endpoint wiring | rule 13: controllers take services |
| server | `server.agents` | `AnswerSchemas` | 60 | decided per object | `ResponseSchema` companion | schema constants of answers |
| server | `server.agents` | `BuiltinAi` | 48 | built-in plugin list | moves to its plugin module (plugin slice) | plugin separation |
| server | `server.agents` | `ClaudeCode` | 428 | decided per object | moves to plugins/ai-cli (see plugin slice) — `ClaudeLaunch` companion | a runtime |
| server | `server.agents` | `ClaudeCodeEnv` | 15 | decided per object | moves to plugins/ai-cli | a runtime |
| server | `server.agents` | `CliOutcomes` | 75 | decided per object | `CliOutcome` companion | about outcomes |
| server | `server.agents` | `CliProcess` | 229 | decided per object | moves to plugins/ai-cli (process runner) or util.process | process I/O |
| server | `server.agents` | `Codex` | 125 | decided per object | moves to plugins/ai-cli | a runtime |
| server | `server.agents` | `CodexHome` | 149 | decided per object | moves to plugins/ai-cli | a runtime |
| server | `server.agents` | `CompareRunsViews` | 21 | decided per object | `RunDetail` Chimney transformer (server edge) | a conversion |
| server | `server.agents` | `GeneratePlugin` | 52 | decided per object | `@main def generatePlugin` (dev tool) | entry point |
| server | `server.agents` | `ManagerDefaultsViews` | 16 | decided per object | Chimney transformer givens (server edge) | a conversion |
| server | `server.agents` | `ManagersRoutes` | 41 | handler / tools | becomes <Feature>Controllers (class) + endpoint wiring | rule 13: controllers take services |
| server | `server.agents` | `Opencode` | 92 | decided per object | moves to plugins/ai-cli | a runtime |
| server | `server.agents` | `Pipelines` | 30 | decided per object | `PipelineDef` companion | lists pipelines |
| server | `server.agents` | `PluginModel` | 125 | decided per object | `PluginAiClient` companion | adapts a plugin model |
| server | `server.agents` | `QuotaGuard` | 56 | decided per object | `AgentDefinitions` companion (renders the guard mod) | about generated definitions |
| server | `server.agents` | `StrictJsonSchema` | 130 | decided per object | stays: util.json.StrictJsonSchema | schema transformation for any schema |
| server | `server.agents` | `ToolResultCap` | 95 | decided per object | `ToolResult` new type with `cap` | about a tool result |
| server | `server.agents` | `Trace` | 35 | decided per object | `ConversationHistory` extension | about a conversation |
| server | `server.basemap` | `BasemapFetch` | 424 | decided per object | dissolve into domain.operations.basemap: `FetchPlan` (was Plan) companion | plans a fetch |
| server | `server.basemap` | `BasemapLabels` | 360 | decided per object | `BasemapLabel` (was Label) companion | about labels |
| server | `server.basemap` | `Mvt` | 343 | decided per object | stays: util.tiles.Mvt | an external file format |
| server | `server.basemap` | `PmTiles` | 303 | decided per object | stays: util.tiles.PmTiles (Header/Entry top-level) | an external file format |
| server | `server.basemap` | `Png` | 141 | decided per object | stays: util.images.Png | an external file format |
| server | `server.basemap` | `TerrainResample` | 59 | decided per object | `TerrainGrid` type (domain.operations.basemap) | resamples a grid |
| server | `server.basemap` | `TerrariumPng` | 142 | decided per object | stays: util.images.TerrariumPng | an external file format |
| server | `server.basemap` | `Vp8l` | 509 | decided per object | stays: util.images.Vp8l | an external file format |
| server | `server.events` | `BuiltinCatalogues` | 67 | built-in plugin list | moves to its plugin module (plugin slice) | plugin separation |
| server | `server.events` | `EventCatalogRoutes` | 24 | handler / tools | becomes <Feature>Controllers (class) + endpoint wiring | rule 13: controllers take services |
| server | `server.events` | `EventCatalogTools` | 107 | handler / tools | becomes <Feature>Controllers (class) + endpoint wiring | rule 13: controllers take services |
| server | `server.importers` | `AwmcImporter` | 80 | decided per object | `GazetteerImporter` implementation (class) in domain.catalogue.places.gazetteer | an importer |
| server | `server.importers` | `ChronOntologyImporter` | 118 | decided per object | `PeriodImporter` implementation (domain.catalogue.periods) | an importer |
| server | `server.importers` | `CliopatriaImporter` | 107 | decided per object | `GazetteerImporter` implementation | an importer |
| server | `server.importers` | `GazetteerLoad` | 503 | decided per object | `GazetteerLoadService` (trait + Implementation + InMemory) | a service |
| server | `server.importers` | `ImportRunner` | 96 | decided per object | `GazetteerImporter` companion (`run`) | runs an importer |
| server | `server.importers` | `NaturalEarthLand` | 63 | decided per object | `LandImporter` (domain.catalogue.places) | an importer |
| server | `server.importers` | `PeriodOFetch` | 12 | decided per object | `PeriodImporter` companion | fetches PeriodO |
| server | `server.media` | `BuiltinMedia` | 202 | built-in plugin list | moves to its plugin module (plugin slice) | plugin separation |
| server | `server.media` | `KeyRefusal` | 11 | decided per object | moves to plugins/media (`KeyedConfig` companion) | about keyed collections |
| server | `server.media` | `MediaImporters` | 104 | decided per object | `MediaImporter` companion | resolves importers |
| server | `server.media` | `MediaStep` | 304 | decided per object | `MediaVerdict` (was Verdict) companion (domain.media) | one import step |
| server | `server.plugins` | `LegacyPlugins` | 128 | decided per object | stays: plugin host glue (maps old settings, a facade over Config) | maps a former plugin's keys |
| server | `server.plugins` | `PluginSetResource` | 47 | decided per object | `PluginSet` companion (`fromClassLoader`) | reads a PluginSet |
| server | `server.plugins` | `PluginTool` | 312 | decided per object | `@main def pluginTool` (relink-kit entry) | entry point |
| server | `server.plugins` | `Plugins` | 153 | decided per object | stays: plugin host glue (`Plugins.load`) → `PluginGeneration` service in domain.platform.plugins | composition of the generation; not about the SPI `Plugin` |
| values | `values` | `Ascii` | 15 | decided per object | `FoldedText`/util.text | text folding |
| values | `values` | `EventEdgeKind` | 20 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| values | `values` | `EventLinkStatus` | 8 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| values | `values` | `GoalStatus` | 10 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| values | `values` | `Historicity` | 10 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| values | `values` | `MediaRole` | 39 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| values | `values` | `PlaceKind` | 36 | String vocabulary | becomes an enum (wire strings kept) in values/api | a closed vocabulary held as String constants |
| web | `web` | `AboutView` | 231 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ActivityView` | 308 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ApiClient` | 399 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `App` | 274 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ApprovalsView` | 622 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ArticleLink` | 27 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ArticleModal` | 41 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ArticlesTab` | 108 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Auth` | 111 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `BaseMaps` | 716 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `CardIcons` | 71 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Chip` | 39 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ChipInput` | 238 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ConfidenceBadge` | 34 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `CreditsView` | 51 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Diagnostics` | 47 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `DotCluster` | 146 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ErrorLine` | 11 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `EventLayer` | 166 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `EventMarker` | 40 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `EventMarkers` | 55 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `EventPlayback` | 85 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `EventsTab` | 103 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `FadingLayer` | 17 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `FilterBar` | 330 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `FilterChips` | 66 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Fmt` | 69 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Glyphs` | 94 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `GoalPage` | 394 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `GoalsView` | 683 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `GraphPanel` | 376 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `IconSelect` | 208 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `IdentLink` | 45 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `InPage` | 62 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `JobsView` | 333 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `LabelMotion` | 102 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `LabelPlacement` | 516 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `LayerFilters` | 58 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `LayerToggle` | 21 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `LicenseName` | 44 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Lightbox` | 131 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ListFilterBar` | 158 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ListPaging` | 91 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Main` | 6 | entry point | top-level @main def | rule #7908 |
| web | `web` | `ManagerPolicyText` | 14 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ManagersHandlersText` | 25 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ManagersView` | 1583 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `MapFold` | 59 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `MapHandle` | 120 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `MapKey` | 93 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `MapLabels` | 565 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `MapLists` | 178 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `MapRegions` | 80 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `MapRegionsView` | 268 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `MapView` | 856 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `MediaFilters` | 60 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `MediaPopup` | 53 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `MergeView` | 918 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `MobileChrome` | 222 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ModelsView` | 475 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `NewTabLink` | 19 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Notices` | 129 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `PageTools` | 56 | handler / tools | becomes <Feature>Controllers (class) + endpoint wiring | rule 13: controllers take services |
| web | `web` | `Pager` | 46 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Pages` | 310 | decided per object | server.web: `PageBuilder` companion | renders pages |
| web | `web` | `PinRules` | 23 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `PlaceHover` | 260 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `PlacesInput` | 554 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `PlaybackAreas` | 31 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `PlaybackBuckets` | 26 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `PlaybackFeed` | 47 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `PointKinds` | 212 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `PointMarker` | 23 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Poll` | 20 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Preview` | 965 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `QueueItemLink` | 16 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `QueueItemRef` | 160 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `QueueView` | 772 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `RangeLabels` | 50 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ResearchOverview` | 327 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ResultDetail` | 127 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ResultsList` | 348 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ResultsPaging` | 66 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ResultsPanel` | 248 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ResultsWidth` | 35 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `RouteLink` | 12 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Router` | 108 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `RunsView` | 606 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `SourceEditor` | 354 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `SourceLink` | 38 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `StandalonePage` | 39 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `StatusBadge` | 13 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Theme` | 80 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `TileDecode` | 99 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `TilePlaceholder` | 20 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `TimelineCurve` | 126 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `TimelinePlayback` | 221 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `TimelineView` | 1076 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `TopBar` | 177 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `TreeTab` | 274 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `UiState` | 469 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `UploadView` | 401 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `Viewport` | 83 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `ViewportToggle` | 245 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `WinLink` | 20 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `WindowManager` | 450 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web` | `WriteButton` | 21 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.library` | `AddSources` | 81 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.library` | `ArticleOpening` | 33 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.library` | `DoiForm` | 94 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.library` | `DoiIngest` | 23 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.library` | `LibraryAddress` | 44 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.library` | `LibraryLists` | 78 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.library` | `LibraryPage` | 158 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.library` | `LibraryTable` | 120 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.library` | `MyLibrary` | 83 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.library` | `ReadingPane` | 299 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.library` | `ShelfList` | 178 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.library` | `TableKeys` | 20 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.map` | `ClusterMotion` | 66 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.map` | `Clustering` | 125 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.map` | `Engines` | 24 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.map` | `MapStart` | 41 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.map` | `MotionPref` | 72 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.map` | `NoMap` | 36 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.map` | `ShapeHits` | 45 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.map` | `Spiderfy` | 51 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.map` | `TheMap` | 40 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.map.maplibre` | `MapLibre` | 137 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.map.maplibre` | `MapLibreEngine` | 57 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.map.maplibre` | `MapLibreStyles` | 279 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `Ago` | 21 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `ItemActions` | 60 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `ItemMenu` | 38 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `MapMenu` | 137 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `MyItems` | 111 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `NoteDrafts` | 28 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `NoteEditor` | 158 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `NoteList` | 40 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `NoteMarkdown` | 83 | renderer | renderer: per rendered type into a Markdown[A] instance in api.<subdomain>.<feature> | a rendering is a type class instance of the wire type |
| web | `web.marks` | `NoteMarkers` | 85 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `NoteView` | 27 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `OpenBack` | 59 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `PeriodMarks` | 110 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `RowActions` | 18 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.marks` | `SavedViews` | 115 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `AdvancedSection` | 94 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `AgentsSection` | 55 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `BackupsSection` | 89 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `CalibreSection` | 33 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `DataSection` | 266 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `DownloadJobs` | 61 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `FindersSection` | 49 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `GeneralSection` | 133 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `KeysSection` | 203 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `PeopleSection` | 28 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `PluginsSection` | 66 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `SettingControl` | 106 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `SettingsForm` | 74 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `SettingsPages` | 46 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `SettingsStore` | 108 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.settings` | `StartPlace` | 134 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.setup` | `SetupPage` | 451 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.setup` | `SetupPlan` | 41 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.ui` | `Clipboard` | 31 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.ui` | `ContextMenu` | 173 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.ui` | `Flash` | 27 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.ui` | `GraphLayout` | 15 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.ui` | `Layout` | 136 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
| web | `web.ui` | `ThemeTokens` | 64 | web view | stays: Laminar view/component (web.<feature>) | a UI component; rule applies to model helpers only |
