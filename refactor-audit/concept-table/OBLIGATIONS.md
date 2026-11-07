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
