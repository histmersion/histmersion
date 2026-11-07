# Obligations that must not get lost

## Validation moved from the API layer to the server (decision #8868)

Each check an API-layer test used to make must exist as a server test once the server compiles. A G8 row is not done until these exist.

- `TargetSuite`: a target with `lat: 91` is refused with a 400 where the server turns the API value into a domain value.
- plugin-api `ValuesSuite`: coordinate, DOI and Wikidata id checks (three expectations, from the handover).

## Audit before the refactor is called done: places the compiler cannot check

1. Generic calls across types: `Option.contains`, `indexOf`, `Set`/`Map` lookups, `==`, `getOrElse` widening to a union. Known: `MergeApplyJob.of` (`error.contains(JobState.Interrupted)`), `Storage.onVolume` (`e.volume.contains(volume)`).
2. Code changed to plain types or bridged with `X(y.value)`, where a swap no longer shows.
3. String interpolation of an enum or newtype, which prints `toString`. Known: `Edge.Kind` printed `Challenges` for `challenges`.

Suspected wrong merges to check: `PeriodO.int` returns `Year` for `yearPublished`; `Queue.createdAtOf` and `Tree.timeTime` carry mangled wrappers.

4. A newtype with its own codec can lose it to the generic `NewtypeCodecs` given, silently dropping normalisation (found: `ItemKey`'s trimming decoder in `Target`). Check every hand-written codec on a newtype is the one actually used.

## Behaviour changes made by repair agents, to review against 7e197c2b

- `BundleTar.parseHeader` reports a negative octal size as unreadable; master accepted it.
- `s2Next` uses `Offset.option`, so a negative value is `None` where it threw before.

## Types agents reverted to primitives on public signatures, to restore

`BundleEntities` (problems, `payloads`, `decode`), `SecretForm`, `Jats` (`runText`, attributes), `BundleReader.Loaded.problems`, `Compatible`, `checkFiles`, `ProviderKind.names`, `AboutMarkdown.age`.

Found and fixed, generic-call class: `RepositoryLanding` `g.contains(d)` compiled as a character test and was always false.
Review too: `StorageConfig.lowFreeBytes` uses `ByteCount.option(...).getOrElse(zero)`, so a negative setting silently becomes zero.

5. `X.option` / `X.either` on a type that lost its constraint now always succeeds, silently dropping validation (decision #8868's side effect). Found: `WorkLookup.key` with plugin-api's `Doi`; restored at the call. Check every such call against 7e197c2b: a check it relied on must move to the boundary.

Test input to review: `BundleTar.header(..., ByteCount.assume(-1L))` keeps the old `isLeft` assertion past the constraint; compare with the `parseHeader` behaviour note above.

## Regressions to restore in core

- Model answers (`EvidenceAnswer`, `SpotCheckAnswer`, `RejectionAnswer`): citekey fields became refined `Citekey`, so stripping `[[x]]`, trimming and lower-casing are gone and a bracketed citekey fails decoding. These answers are a boundary: accept raw text, normalise, then produce the `Citekey`. Check against 7e197c2b.
- `ArticleDraft.notUsed` holds `"ck: reason"` lines typed as `Citekey`, and `problems`, `section`, `text`, `filter` are wrapped the same way: give them real concepts.
- `MergePlanner` `matched0` went to `List[String]`; `AgentVerify` is reached through private String helpers in `ReviewArticle` (G3 will move these onto types).

Found and fixed, generic-call class: `Merged.deferred.contains(f.id)` was always false.

More in core, from round 2, to compare with 7e197c2b:
- `PlanGoal.WorkAnswer.doi` is a constrained `Doi`: same class as the model-answer citekeys. Every model-answer type needs its fields raw until normalised.
- `ManagerPolicy.released`: an `untilSource` that is not a valid citekey now counts as "source does not exist".
- `SourceMatcher.sha` uses `Sha256.option`: a malformed original sha no longer counts as a strong key.
- `seed:`/`watch:` manager-id format now exists twice (`ManagerRow.Id` and api `Goal.Ref`).
- `MergeFold.hull` picks min or max year instead of `math.min`/`max`.

More in core, from round 3, to compare with 7e197c2b:
- `Citekey.option` drops invalid citekeys: `SourcePlan` aliases can vanish; `CurrencyCheckRules` gives `Ignore` for an invalid citing key.
- `AcquireMatch.doiKeyOf`: a DOI trimmed to an empty suffix becomes `None`. `AgentVerify.linkProblems`: an invalid wikilink counts as unknown without asking `isCitekey`/`isSlug`.
- `CandidateDedupe` builds the arXiv key with `Doi.option`, so an id failing the DOI pattern is skipped.
- `AttestationPlan` lines 95-96: a `Local.Rev` passed as `ForeignRev` and a body sha as `leftRev`; check these against master rather than wrapping.
- Positional types kept because other files name them: `EntityOwnBundle`, `TombstoneOwnBundle`, `ChunkMarkdown`, `ChunkMaxChars`, `FoldS`, `TokensS`, `OfS`, `ValidCk`, `NormaliseS`, `NormaliseResult`.

Model-answer fields done right so far: `AcquisitionSweepRules.Pick.doi`, `AgentProposals.ProposalAnswer.citekey` (raw, parsed at the check). Found and fixed, generic-call class: `ActivityRules` `e.goal.contains(g)`, `e.by.contains(...)`.

From the core finisher, to compare with 7e197c2b: `MergePlanner` folds, renames and relinks go through `Citekey.option`, dropping a bundle citekey that fails `CitekeyRule` where master passed it through (bundles from another install could carry one: test this). The evidence check runs `Citekey.option` before `isCitekey`, so an invalid model citekey can get a different problem text.

## Blockers before any install

- Stored JSON must still load: `VerifiedFindings` lost its defaults ("checkpoint older than the counts"). Check every record stored as JSON (checkpoints, agent state, run outputs, bundles) that lost a default or gained a constraint in this migration, and test the decoders on real saved data in a restored copy.
- `parseRetractionWatch` no longer normalises DOIs, and a repair agent moved that into `AcquireSuite`. Restore it in main and remove it from the test: a retracted paper must not be missed.

Also check: `ModelChains.headroom` returns `Option[LimitState.FreesAt]` for a token headroom; `EventPrecision`, `Correction.Status`, `HintEffect` constants are plain strings; `AcquisitionControllers:357` has a mangled `Ident.doi(r.doi)`.

## From db and plugin-host, round 1 (to review against master before install)

- Reads through constrained types fail on rows the old code accepted: `.query[Citekey|Place.Id|Slug|Run.Id|Sha256]` in SourceRepo, PlaceRepo, PointAreaRepo, ArticleRepo, JobRepo, SourceDeleteRepo, SourceCheckRepo.stale, AcquireCheckRepo.heldByDoi, UndatedSiteRepo; goals, goal_links, goal_schedules slugs; `applyUnsafe` in SourceRepo.write, FindingHitRow.assignedPlaceIds, JobRepo.insertRun, PlaceRepo.rename; ApprovalRow.jobId as UUID.
- Dropped or changed: SourceCheckRepo.EdgeRows and SourceBundleRepo.unresolvedEdgesIn drop unknown edge kinds; Edge.Kind read with `getOrElse(References)`; SourceDeleteRepo no longer trims or filters blank sha256; neededBlobs drops malformed shas.
- SpiValues.latitude/longitude/area lost the range check by taking constrained types, but plugin values are unconstrained: the check must stay where a plugin's value enters the host, and the tests with 91.0 and 400 must keep passing.
- `math.max(1, limit)` became `limit.value` on PageSize: check nothing builds a PageSize with assume or from 0.
- AgentRepo reports using `api` types (`RunInfo`, `Goal.Slug`, ...) in rows: G7 review.
- Concepts split, bridged: Plugin.Info.Id vs util.plugins.Plugin.Id; Goal slug domain vs api; Job.Id / RunInfo.Id / Run.Id; Approval.State.Wire api vs domain; api Year vs DeepTime.SignedYear; HttpAnswer.Code vs HttpPolicy.StatusCode; AcquireConfig.UserAgent vs HttpPolicy.UserAgent; ProviderAsset.Bytes vs NewFigure.Bytes; ReportMaxItems vs Lint.ItemLimit; Place.Entry.SiteType vs SiteTypes.SiteType.Id; PlanQuotaRepo RefreshMode vs PlanQuota.RefreshMode.Wire; Pool.RefusalUntil vs EpochMillis; MediaGeoRow.Uid vs MediaItemRow.Uid.

## From db and plugin-host, round 2

- Dropped or changed: CitationRepo.graph drops a stored edge with an unknown kind; ImportRepo.targets/collectionTargets re-parse with `.option`, so an invalid trimmed id falls to the next branch instead of "No place ..."; ContentStore.hex throws on a digest that is not 32 bytes.
- Reads that can fail on stored data: Doi in CandidateRepo.dois, AcquireCheckRepo.heldByDoi; Citekey in BundleImport.citekeyOf/citekeyHere, CoverRepo; Run.Id in ManagerRepo.dispatchIn; Place.Id, Event.Id in ImportRepo, AssetLink, Tree; Latitude/Longitude in placeFacts.
- BlobSink.Streamed.StoreResult is Sha256 but carries an error message. BundleRepo.digest holds an md5.

## From the db finisher

- `NewtypeColumns.given` is now on 66 row classes that derive DbCodec, so constrained values (Citekey, Place.Id, Event.Id, Goal.Slug, Latitude, Longitude, ...) throw on read where these rows used to read raw strings. One bad stored value fails a whole list. Install blocker: run every row read against a restored *_test copy before deploying, and decide per column between lenient read and data repair.
- Hand-written stored reads use `.assume`: Goal.Slug in FocusRepo, GoalRepo; Place.Id in PlaceRepo, PlaceBundleRepo; Event.Id in EventRepo; Citekey in BundleImporter; Doi in OaFetcher.fetchWork. Review each.
- Reported mangled and fixed, to compare with master: EventRepo regional filter id; PlaceMergeRules renamedIds lookup; JobRepo limit; lens setTo values; SourceReferrers.Entry and Queue.QueueItem.Id in MergeFollowUps.
- server PluginCollection.place and SpiValuesSuite must pass spi.Latitude/Longitude.

## From the db test finisher

- SpiValuesSuite and PluginHarnessSuite lost direct coverage of trim and refuse on SpiValues.doi/wikidata and WorkKey.fromDoi, because those now take validated types; `Doi.assume` keeps the URL-stripping test running. Review whether SpiValues.doi, wikidata and workFromDoi should take unconstrained SPI text at the plugin edge, as latitude and longitude now do.
- Test inputs changed: fake hashes in AssetReconcileSuite became valid hex; `Place.Id.applyUnsafe` in GazetteerNames and RegionCleanup for ids like "levant" that fail the pattern. Stored place ids must load: covered by the stored-data check.

## From db test failures

- New silent class: an Option/List of a newtype in `sql"..."` without `NewtypeColumns.given` falls back to setObject and fails at run time (only a compiler warning: "Could not find given DbCodec ... Using PreparedStatement::setObject"). Make it an error or a check. Still warned at EventBundleRepo:340, PlaceBundleRepo:269, PointAreaRepo:240, SourceApprovalRepo:43 and some test lines: fix them.
- New silent class: a row class deriving DbCodec with an enum field and no vocabulary given uses Magnum's enum codec and misreads stored spellings. Audited for status, confidence, edge kind, precision, dating, time basis, neighbour target in db main; audit again in server.
- Strict Doi reads that throw on stored URL-form DOIs: SourceRepo.doiIndex, DoiKey, AcquireCheckRepo. The other sources.doi reads were loosened. Decide for all: lenient read or a data repair.
- Loosened to local unconstrained types: JobRow.sha256, AgentRepo.ApprovalJobId, FootprintRow's free-text citekey column (misnamed), BVersionRow.versionOf. Concept review.

## From server, wave 1

- New Left/None where old code accepted: PmTiles.parseHeader and decodeDirectory on negative offset or length; RangeHttp.contentRange on overflowing digits gives None instead of throwing.
- Plugins.pluginId: key under histmersion.plugins.<x> with an invalid <x> is now None, treated as a host key (a reload then restarts differently): compare with master.
- BundleControllers.applyUploaded now refuses a malformed plan hash with 400; merges and mergeLedger: a non-positive limit falls back to the default: compare with master. `id.trim` dropped on Job.Id.
- BundleImporter.apply's backup callback has Either sides swapped (BackupFile on the left, error on the right).
- BackupService.restore returned elapsed ms as ByteCount, now RestoreDuration: check `Report` text.

## From server, wave 2

- Stored values now dropped or refused instead of carried: CurrencyCheckPipeline.load and IngestQueue.matchAcquisitions drop a stored DOI failing the pattern; StoredOriginals.hashes drops names with a non-Sha256 prefix; SourcesData.Check.report counts an invalid link target as not stored; CandidateSearch.plan drops invalid citekeys and slugs in goal links; CandidateSearch.sweep skips watches whose subject is not a valid DOI. Decide per case, on the stored-data check.
- New refusals: LibraryRoutes neighbors, graph, contradictions give 400 for an invalid citekey (old: empty result); AcquisitionControllers.candidates 400 for an invalid goal slug; passages 404 for a bad key; CollectionsService.link/import 400 before touching the library; ImportTarget.citekeyFor errors on an invalid generated key; IngestQueue.decode errors on negative assetBytes. Compare with master.
- `ByteCount.assume` on config assetBytes breaks the assume rule. CalibreGapControllers.pushed passes the trimmed citekey where old code passed raw. Spool.write lost Citekey.parse: type guarantees it, check callers. AcquireRecheckService.attempt: non-Fetch records a Failed check. SourcesData.reindex ignores a malformed slice.after.
- api Year (Long) narrowed to domain Year (Int) with `.toInt`: domain and api differ.
- db JobRepo positional types (GetId, ListStatus, ...) and config's private[config] ConfigLayers.Problem block util/jobs and SettingsService.
- Api Acquire.Checks.RecheckStatus.Job is a job id type carrying a text line. OpenAlexIndex.authors turns author ids into AuthorName.

## Place.Id pattern widened (found in wave 3)

Master's `[a-z][a-z0-9]*_.+` refuses the seed's own bare ids (`anatolia`, `aegean`, `levant`, `mesopotamia`, `egypt`). Master rarely applied it because most code took String; now every read and request goes through it. Now `[a-z][a-z0-9-]*(_.+)?`, a superset. The `Place.Id.applyUnsafe("levant")` test wrappers still work and can become plain. Confirm against every place id on the restored copy. Check other constrained ids the same way (Event.Id, Citekey, Slug, Goal.Slug, Doi, Sha256, Run.Id): reading them from the seed files and the restored copy.

## From server, wave 3

- PlacesControllers.renamed 400 on a new id outside the pattern; merge refuses an invalid `into` as not found; PlaceService.InMemory.scopeFor drops invalid ids; CorrectionsTools refuses a malformed event id (old: matched nothing). PlacesRoutes: malformed id 404, and empty list for placeNames and placeOverrides. PlacesTools PlaceOverrideInput periodStart/periodEnd went Long to Int.
- GazetteerLoad.load records the file's lowercase sha rather than the manifest text. AcquisitionSweepPipeline's dry-run Ingested holds a non-citekey via Citekey.assume. AcquisitionControllers.updateRetractionsNow filter through Ident.doi removed to match master; check the normalisation note from core.
- db tests: four suites had "no-such-job" replaced with an all-zero UUID. Rerun db tests after server (JobRepo changed).

## From server, wave 3 (events)

- API defect: `EventsApi.corrections` input tuple types are in the wrong order against the wire order status, event, limit, offset (`Event.Id`, `Status`, `UnreadPage.Offset`, `EventList.Limit`), read by position in EventImportsRoutes: fix the API type. `UnreadPage.Offset` is used as an event list's offset in EventList.of.
- HeldEvent.pointOf: an out-of-range point is treated as no point. EventsRoutes delete and EventLinksRoutes: an invalid id gives 404. EventImportsRoutes corrections: negative offset 400, non-positive limit uses the default (old code let both reach the database). EventService.InMemory.put/patch drops invalid placeId, citekey, wikidata. AttestationsControllers.list: invalid citekey gives an empty list. rename: an id over the domain length gives 400.
- MapLayer.features takes MapTiles.TileKey, a wrong concept: check.

## From server, wave 4

- Primitives on public signatures, to restore: AgentGenerator returns Map[String,String], AgentGenerator.check takes Set[String], AgentTreeWriter.write takes Map[String,String] and returns Either[String,GenerateResult], ArticleFilterApproval applyProposal returns Either[String,String], MediaStep.each takes Builder[String,List[String]], AgentRuntime.parse takes Wire.
- New refusals to compare with master: ImportsControllers importMedia/importJob 404 on a malformed place or event id; Slug failures 404 on reads and 400 on writes; check_edits 400 on an invalid slug with a whole body (master accepted); WatchManagerKind.target new wording; ProposeArticlesPipeline.check drops wikilinks that are not valid slugs (case-sensitive).
- Stored or computed dropped: OwnImport.persist stores an invalid qid as no qid and skips commonsCategory; EnrichSourcePipeline.load invalid stored DOI becomes None; ArticleService.InMemory.refs no longer trims or lower-cases; SourcePictures.run with an unparseable citekey and an isbn hint returns no candidates (old tried the bogus key); AssetsControllers.decode clamps a negative asset-bytes setting to 0.
- Config: AgentReviewConfig.finalPanelMode calls a missing FinalPanel.parse.
- Two types, one concept: api vs domain Article.Body; CoverKeys.Isbn vs library.Isbn; MediaSources.Key vs Citekey; HeldCheck.* vs ImportService. Found and fixed silently-false: AgentTreeWriter `runtimes.contains(AgentRuntime.ClaudeCode.wire)`.

## From server, wave 5

- API endpoints with mistyped parameters (no run-time effect, wrong concept): AgentsApi runs and approvals `before` and `upTo` typed as RunInfo.Status/Pipeline and Approval.State.Wire/Kind.Wire; setFallthrough's role as PoolIdText; removeAllowance takes ReviewGoal.Slug; removeGoalLink takes a Citekey as link ref. Fix in api.
- AgentsTools input records are back to raw String/Boolean/Int/Long/Double, as model answers; handlers wrap. Check against G1: tool inputs are a boundary.
- Dropped or changed: ManagerService.InMemory.occupyingRuns drops unknown pipelines; RunService.row stores an invalid Source target as no citekey; AutoApprovals.judgeOwn gives Leave for an invalid goal-plan subject, slugExists false; GoalService.rename no longer trims (controller validates, 400 over 121 chars); PlanGoalPipeline fails on a non-slug target, applyApproved skips with a warning; OaBacklog.acquireOnce adds nothing with no goals.
- Bridges to merge: Run.Id vs Job.Id vs JobRunState.Id; plugin provider name as Plugin.Id, ProviderKind.Wire, ProviderKind.Plugin.Name; api vs domain Pipeline, AgentRole; Prompt.System vs LlmCall.System; Prompt.User, UserText, LlmCall.User; StepRecord.Turns, AgentDef.MaxTurns, LlmCall.MaxIterations; StepRecord.Seq, Engine.BeatSeq, ApprovalService.StepSeq. Prompt.sha256 returns String where Sha256 is declared. AutopilotPolicy.depth takes research.Slug but SeedManagerKind passes a goal slug.

## From server, wave 6

- Silent loss to restore or justify: Composition.build drops toolNames outside the ToolName pattern from the agents' placeholder set; Composition.acquireClients drops invalid fetcher keys and chainIds, so the "unknown id" note for a bad chain id no longer appears (keep the warning); McpTools ingest drops an unparseable doi where master passed it through (should refuse); QueueService.list empty list for an invalid scope id.
- PageSize.assume on unclamped limits breaks the positive guarantee db now relies on since `math.max(1, limit)` went: ApprovalService db methods (limit 0 or less no longer clamped), ManagerContext.Limit sites. Make these PageSize.option with the default.
- New refusals or changes: OpsRoutes.queueList 400 on a malformed goal or job filter (master: empty); McpTools rename_label malformed `from` refuses (master: not found); job_status, activity, merges: non-positive limit means default (master: passed through); events mapLimit clamped to at least 1; SeedNoteFiles.figures negative maxBytes gives none.
- MarkdownLint rebuilt from 7e197c2b on plain String internally. Workflows.parse takes String. ApprovalService rewritten: compare carefully.
- Bridges: Queue.Wire vs Queue.Kind.Label; Queue.RenderResult vs Queue.Export; AliasTable.Id to Event.Id, Goal.Slug, Alias via `.either(...).getOrElse(id)` (an invalid id stays); QueueScope.ForJob.Job, ForGoal.Goal vs Job.Id, Goal.Slug; db QueueRepo.QueueItemId alias.

## From server, wave 7

- ServerAutomationHost.runTarget, linkRef, runOptions refuse with the parser's message instead of the old not-found wording; articleStore.exists false for an unparseable slug. Unchecked bridges from config and env values: ServerConfig.Host from HISTMERSION_BIND, ListName in OpsRoutes, OpsTools, ManagerSupervisor.
- Visibility: ArticleService.citing takes private ArticleRepo.Limit; EventsControllers.mapView requires private EventRepo.GeoTolerance: public concepts needed.
- Hot reload's `changed` typed Def.Name holds config keys. RelinkPlugins.check had (problems, notes) swapped: fixed. PluginKit.KitFile.Sha256 (constrained) vs api.Sha256.

## From the server finisher

- EventImports drops out-of-range lat/lon and EventImportService drops malformed Q-ids (accepted before); ServerAutomationHost.workLookup drops an invalid citekey; MarkTarget year conversions use toInt with no range check.
- db changed after db tests passed: JobRepo, GeoTolerance, ArticleRepo.citing (PageSize). Rerun db tests.
- Positional leftovers still to name: files TextFiles.ReadResult; AutomationHost LinkRefResultPart2 and siblings; plugin-api Plugin.RegisterResultPart1 and the rest.

## API endpoints with mangled input types (a pass of their own, before install)

Reported by client and server agents; each is wrapped `X(v.value)` at the call site so the wire is unchanged, and each must be retyped in api with its callers: Api.assets, putAssets, assetDetail, patchAsset, assetLinks, mediaShow, periods, importPeriods, importPlaces, places, jobs, mergeLedger, exportBundle; EventsApi.corrections (order too); ManagersApi.items (offset and limit swapped by type), action (ActionPart1Part2); AgentsApi runs and approvals before/upTo, removeAllowance, setFallthrough, removeGoalLink; AcquireApi meta and wikirefs (Title). Check every Tapir endpoint's input and output types mechanically against its query and path names.

## From client wave 1

- Client-side checks gone, as decided (server answers 400): TargetFlags lat, lon, zoom; RenameCmd new citekey; DeleteCmd citekey. RenameCmd.pruneRenamed uses a filename guard in place of Citekey.valid.
- G24: cli PlacesCmd still imports the domain PlaceSeed. DirSize.Budget.of takes config types. NewtypeArguments has an asInstanceOf.
- Found and fixed silently-false: ActivityView `e.by.contains(Writer.Agent.wire)`.
- PluginOps.addFrom recomputes the index SHA because Installed.Sha256 is the constrained type, not api.Sha256.
- Article slug has five types in web (Article.Slug, ReviewGoal.Slug, ApiClient.ArticleSlug, Preview.Item.Slug, WindowKey.Article.Slug); citekey three; job id three; year three (Years.Astronomical, catalogue.Year, Long). Merge in the split-concepts pass.

## From client wave 2

- web component signatures back to plain String/Int/Boolean as master and tests have them (JobsView.Poll, ago, row; LayerToggle.apply; Latest; LatestOnly; ListFilterBar; Fmt.esc/clip; MapLabels internals). G1 review: UI internals or concepts?
- GoalsView.newArea lost the client-side slug check (server 400, as decided). GraphPanel.legend no longer drops unknown link kinds because GraphLink.kind is now an enum: check the decoder of an unknown kind.
- Two api types for one concept: AgentsMarkdown/IngestMarkdown take State.Wire, Status, JobStatus, JobAge where cli has ListFilters.Qualifier and OlderLine.Text; Client takes ReviewGoal.Slug for article slugs; Api.EventQuery.Kind/Tier vs TilesApi.TileQuery.Kind/Tier; EventGeo.Precision vs EventSummary.Precision; Api.Job.Id vs SpatialHierarchyResult.Job.Id; Wcag.Hex vs ConfidenceStyle.Rgb; UiLabels.Text vs Label.Text. `ui.CssClass.value` returns a positional ValueResult.

## From client wave 3 (must check: years)

- Astronomical vs historical years in web: TimelinePlayback wraps raw astronomical years as `Year(ph.value)` and Period.Start without conversion; PlaybackFeed, PlaybackBuckets.widen keep raw arithmetic. If astronomical year 0 is 1 BCE and catalogue.Year has no year 0, every BCE year shown or sent is off by one. Compare each conversion with 7e197c2b and with Years.parseIso's fromAstronomical; give the web one year type per meaning (Years.Astronomical for arithmetic, catalogue.Year only at display/API).
- MergeView: MergeResolution.Decision is typed Kind.Wire but should hold the full decision wire.
- Preview.creditLine had licence and URL swapped: fixed.

## From client wave 4

- Years: web passes the same raw numbers master did (UiState toFilter wraps Option[Long] as catalogue.Year, PeriodMarks, SavedViews, MapMenu, TimelinePlayback), so behaviour matches master; whether master's mix of astronomical and historical is right stays the open question above.
- Found and fixed silently-false: CitationCounts.answering compared Edge.Kind and Direction with strings.
- SavedViews.current uses Latitude.option/Longitude.option on unconstrained api types, which always succeed (class 5). DataSection.disk used/free went Long to ByteCount.
- ApiClient (web) still has positional types and mangled calls: managerItems swaps limit and offset, parseResolutions uses Merge.Id(yaml.value), managerAction uses ActionPart1Part2.
- Bridges: api.Geo.Latitude vs api.catalogue.Latitude; ShapesId/ShapeSetId/NoMap.Shapes.Id/ShapeHit.Set/Id; ShapeStyle.Colour vs ShapeFeature.Colour; MapEngine.MaxZoom/PixelRatio/Problem vs MapSurface.MaxZoom/NoMapSurface.Why; ApiClient.Citekey vs api.library.Citekey.

## G2 correction (user, 2026-11-07): raw values only in the companion or at a boundary

The rules let agents put primitives in private helpers, loops, renderers, binary codecs, tool inputs and web components. That was never agreed. A pass after compile: every `.value`, primitive constructor call and plain-typed parameter outside a newtype's companion and outside a boundary file is a finding (mechanical check: the G2 `unwrap` rule extended to src/test helpers and to plain-typed defs whose values come from or go to a newtype). Boundaries move to lifted type classes (Kindlings + Iron, NewtypeCodecs, NewtypeColumns, Tapir codecs, chimney-iron) so they need no hand `.value` either. Known areas: web components (JobsView, LayerToggle, Latest, LatestOnly, ListFilterBar, Fmt, MapLabels, ContextMenu, ModelsView helpers, MobileChrome), MarkdownLint, Vp8l/PmTiles/Png/GeoTiffGrid/Mvt parsers, AgentsTools/McpTools inputs, AgentGenerator/AgentTreeWriter, Pages, the table's 574 server + 19 plugin + client `native` decisions that were not predicates.

## From client wave 5

- G44: ApiClient `All` and `Runs` became plain Boolean (decision 2026-11-06: Boolean parameters keep their types); TimelineView bucketSizeFor/hoverAt/realign take Double; UploadView.validate returns (Field, String).
- Years in web: MapView and TimelineView spans are (Years.Astronomical, Years.Astronomical); Year(a.value) bridges astronomical to Year without conversion at Fmt.yr, Period, GeoPick.resolve, UiState.from/to, ResultsPaging.span, PeriodMarks, EventMarkers.load: same numbers as master, but now the types claim a conversion that is not done. Settle in the years item.
- ApiClient wraps slugs, queue names and ids inside because endpoint types are mislabelled (API-endpoint pass).
- Api.Merge.Id is used for a bundle, an apply job and the resolutions YAML text (API-endpoint pass). ApiClient queueList/queueForGoal keep `all: Boolean` (G44).

## Tool bug found: apply.py "move onto the real owner" matched by short name across modules

The client table's new concepts were placed on same-named owners in other modules (domain.research.Article, Goal, Manager, Edge, Job, plugin.FetchedFile, server.Static, util.storage.Store ...), shadowing and unused: undone. Stage 4 reported 1 such move: check it. Fix apply.py to stay within the module set before reuse.

## Server tests that will fail at run time because validation moved to the route (rewrite to call the route with raw API values, assertions unchanged)

- MediaSourceBoundarySuite: invalid source identifiers fail before absence; parser errors reach the media boundary; lookup and missing-source errors retain the input.
- SourcesControllersSuite: a put under a citekey that is not one is refused.
- AgentEngineSuite compare: comma-separated ids (splitting moved to AgentsControllers.runIds); the test passes `Run.Id.assume("a,b")`.
- ArticleServiceContract and ArticlesControllersSuite: `Slug("ZR_WALLS")` pre-normalised with Slug.parse in the test (test took over behaviour): send the raw upper-case value through the boundary; ArticleRepo.refs lowercasing now unreachable.
- MergeApplyJobs planHash went from free String to Sha256: confirm against master that the plan hash is a SHA-256 hex.
- SpoolSuite "an escaping citekey writes NOTHING, anywhere": Spool.write takes Citekey, so the second, path-level guard is no longer exercised. It is defence in depth against path traversal: keep it, and test it with `Citekey.assume` of a traversal string (a deliberate bypass to reach the second layer).
- Ordering for newtypes: tests write `sortBy[Long](_.value)`, `.value >= ...` (Wcag.Ratio, tile ids, MessageInfo.Seq, slugs, citekeys). Lift Ordering once (G45).
- "object X does not take parameters" for classes with a companion (Client, SourceProposals, PeriodImports, Prompts) worked around with `new`: find the real cause.
- Public String left in src/main: Bootstrap.PeriodSeed, AcquireConfig.agent.

## From server tests, wave 3

- Split concepts in server main that tests bridge: BuiltinMedia EntryKey vs EntryName, MediaCollectionsKey vs OrderedRegistered, ReservedName vs Entry.Name; Registries *Key types vs CatalogProviderInfo.Id vs CatalogRecord.Provider; Registries.SourceFetchersKey vs util.plugins.Plugin.Id; Engine.outputCapMessage takes String while capKey returns CapKey; ToolResultCap.compactSource returns String where source takes Markdown; SpatialHierarchyResult.Job.Id is the api job id type (positional name); RefreshGate.Instant vs RefreshInfo.LastAttempt; ModelRef.Wire vs ProbeRequest.Model vs ModelOverride.Model; research.Slug vs ReviewLedger.Ledger.Slug; AssetUid vs Known.Uid; ProviderKey vs ProviderAsset.Key.
- Server tests now feed whitespace, blank or wrong-shaped values via `.assume` (Doi " 10.1000/XYZ.1 ", Slug "Not A Slug", Run "run-1", Citekey "a", Sha256 "sha1") in ArticlesControllersSuite, GoalServiceContract, QueueServiceContract, SourceServiceContract, AcquireCheckServiceContract, ImportServiceContract. Where a suite expected trimming or refusal, move the input to the route with the raw API value; the rest become valid values.
- BuiltinMedia names unified on Registries.MediaCollectionsKey.

## plugin-api is the third-party SPI: its positional leftovers are a published contract

The sample plugins (plugin-fixtures/hello, hello-ai) show what a plugin author now writes: `AiProvider.TitleResult(...)`, `AiCapabilities.Tools(true)`, `Plugin.Context.SecretResultPart2`, `Plugin.Log.InfoMessage(...)`, `Registry.AddId(...)`, `Http.GetHeadersPart1/2`. Rename every plugin-api positional type to a real concept (and consider plain-literal-friendly constructors) before plugins are rebuilt; the testkit API snapshot then follows.

## From server test failures (1628/1632 pass)

- New silent class, data-corrupting: Magnum `sql"..."` writes an interpolated enum as its toString ("References", "High") instead of its wire spelling, ignoring imported codecs. Fixed in SourceRepo.insertEdges and SourceBundleRepo.findingsIn; others may remain. Mechanical check needed: any enum-typed value interpolated in `sql"..."` in db and server. Also check the live data on the restored copy for capitalised edge kinds or confidences.
- Silent class in tests: `String.contains(newtype)` always false (ModelChainRunSuite, BundleHttpSuite): fixed; part of the always-false check.
- Remaining 3 failures, need decisions: (1) ArchitectureSuite direction: 423 new violations, mostly shared newtypes (Citekey, Title, UnreadPage, Year) now used across domains: resolve in G11/G12 (feature boundaries), no new exceptions. (2) ContractsSuite: contracts.tsv refinement scoreboard has 1386 new and 320 gone rows: regenerate at the inventory step, after review. (3) PluginTreeSuite: plugin/skills/scalafix is tracked without a source in skills/; copying it broke AutomationOracle, AgentGenerator, NativeMetadata: the user's scalafix skill, ask.
- `AgentGetSourceInput` decodes a model-supplied citekey with assume (a test requires leniency): make it parse at the boundary instead.
- Contract snapshots regenerated: only int32/int64 format hints changed (Year and ByteCount now Long; ProbeAnswer.year kept int32 for the recorded traffic). Two regressions fixed: CandidateSweep.skipped and CollectionEntry.identifiers Map schemas.
- BundleHttpSuite "review draft" is not idempotent against a reused test database (leaves a merge_sessions row).
