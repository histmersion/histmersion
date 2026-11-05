package com.histmersion.cli

import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.tapir.client.sttp4.{SttpClientInterpreter, SttpClientOptions}
import sttp.tapir.PublicEndpoint
import sttp.model.Uri
import com.histmersion.api.{Api, Dataset, OpsApi, Paging, SpatialHierarchyResult}
import com.histmersion.config.*
import com.histmersion.api.aliases.{InstanceInfo, LabelRename, LabelRenamed}
import com.histmersion.api.automation.{AgentStateDeleted, AgentStateEntry, AgentStatePut, Goal, ListFilters, Queue}
import com.histmersion.api.backup.{RestoreRequest, RestoreResult}
import com.histmersion.api.basemap.BasemapApi
import com.histmersion.api.catalogue.{
  Attestation,
  EventDeleted,
  EventList,
  EventPatch,
  EventPut,
  HistoricalPeriod,
  ManualPlaceName
}
import com.histmersion.api.checks.{FormatCheck, FormatReport, Lint}
import com.histmersion.api.exchange.{
  MergeApplyJob,
  MergeApplyRequest,
  MergeDetail,
  MergeImportRequest,
  MergeInfo,
  MergeLedger,
  MergePlan
}
import com.histmersion.api.instance.{About, Health}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.library.{
  ContradictionsResult,
  EdgesResolved,
  Facet,
  Filter,
  GeoHit,
  GeoHitPage,
  InstanceLabel,
  NeighborRow,
  NoteImport,
  PassageResult,
  Query,
  SearchHit,
  SearchResult,
  SeedRequest,
  SeedResult,
  TimeHistogram
}
import com.histmersion.api.maintenance.ReindexResult
import com.histmersion.api.media.{
  AssetDetail,
  AssetInfo,
  AssetInput,
  AssetLink,
  AssetPatch,
  AssetsStored,
  FigureLicenceFill,
  MediaCoversReport,
  MediaCoversRequest,
  MediaFacets,
  MediaImportReport,
  MediaImportRequest,
  MediaItem,
  MediaList,
  SourceCoverParams
}
import com.histmersion.api.research.Coverage
import com.histmersion.api.settings.{ConfigEdit, ConfigEditApi, ReloadReport}
import com.histmersion.api.views.{EventDetail, PlaceDetail, QueueListing}
import com.histmersion.api.backup.Backup
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.instance.Version
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Rename
import com.histmersion.api.library.Source
import com.histmersion.api.maintenance.Vacuum
import com.histmersion.api.media.MediaProvider
import com.histmersion.api.research.Article

/** Typed HTTP client over the `Api` contract: each call is derived from the same Tapir endpoint the server interprets,
  * so request/response shapes cannot drift. Sync (java.net.http), native-safe. `token` is the
  * `Authorization: Bearer <key>` value for authenticated servers; `None` = local.
  */
final class Client(val serverUrl: Client.ServerUrl, token: Option[Client.Token] = None):
  private val base = Uri.unsafeParse(serverUrl)

  // HTTP/1.1, explicitly. sttp's sync backend is java.net.http, whose default HTTP/2 client reuses a
  // connection in a way the server's netty backend mishandles for body-carrying requests: the SECOND
  // upload on a reused connection arrives with an EMPTY body and is rejected. `histmersion ingest` sends
  // exactly such requests. (Same reason the server pins 1.1 when calling the OCR sidecar.)
  private val backend = HttpClientSyncBackend.usingClient(
    java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build()
  )
  private val interp = SttpClientInterpreter()

  // `private[cli]` so the acquisition client (stream 2.6) reuses this one transport rather than
  // opening a second backend with its own error handling.
  private[cli] def call[I, O](ep: PublicEndpoint[I, Api.Error, O, Any])(in: I): Either[Cli.Error, O] =
    // No client-side read timeout: the server bounds every request itself
    // (`server.request-timeout-seconds`), and sttp's 1-minute default is shorter than a backup.
    send(authed(interp.toRequest(ep, Some(base)).apply(in).readTimeout(scala.concurrent.duration.Duration.Inf)))

  /** The bearer token on a request. EVERY request goes through here — the typed calls and the two file downloads alike
    * (#767: `downloadBackup` was built by hand and sent none).
    */
  private def authed[T](req: Request[T]): Request[T] = token.fold(req)(t => req.auth.bearer(t))

  /** One place turns a response into a result. Decoding does NOT throw: an answer the endpoint does not describe (the
    * auth gate's 401 is not in any endpoint's error output) used to surface as an exception and was then reported as
    * "cannot reach … is the server running?". Only a failure to TALK to the server is that; everything else says what
    * the server answered.
    */
  private def send[O](req: Request[sttp.tapir.DecodeResult[Either[Api.Error, O]]]): Either[Cli.Error, O] =
    scala.util.Try(req.send(backend)) match
      case scala.util.Failure(e) =>
        Left(
          Cli.Error(
            s"cannot reach Histmersion at $serverUrl (${e.getClass.getSimpleName}: ${e.getMessage}) — is the server running?"
          )
        )
      case scala.util.Success(resp) =>
        resp.body match
          case sttp.tapir.DecodeResult.Value(Right(o)) => Right(o)
          case sttp.tapir.DecodeResult.Value(Left(e))  =>
            // a refusal of the request itself (a collection's citekey collision) exits as a bad command line does
            val code =
              if e.code.contains(com.histmersion.api.library.CollectionsApi.Usage) then ExitCode.Usage
              else ExitCode.Refused
            Left(Cli.Error(e.message, exitCode = code))
          case _ =>
            Left(
              Cli.Error(
                Client.unexpected(resp.code.code, serverUrl, Client.isWrite(req.method)),
                exitCode = ExitCode.Refused
              )
            )

  def version: Either[Cli.Error, Version.Info] = call(Api.version)(())
  def health: Either[Cli.Error, Health] = call(Api.health)(())
  def about: Either[Cli.Error, About] = call(Api.about)(())
  def search(q: Option[com.histmersion.cli.Client.SearchQ], limit: Option[com.histmersion.cli.Client.SearchLimit]): Either[Cli.Error, List[SearchHit]] =
    call(Api.search)((q, limit))
  def sources(f: Filter): Either[Cli.Error, SearchResult] = call(Api.sources)(f)
  def facets(field: com.histmersion.cli.Client.FacetsField, prefix: Option[com.histmersion.cli.Client.FacetsPrefix], f: Filter): Either[Cli.Error, List[Facet]] =
    call(Api.facets)((field, prefix, f))
  def getSource(ck: com.histmersion.cli.Client.GetSourceCk): Either[Cli.Error, Source.WithFindings] = call(Api.getSource)((ck, true))
  def findings(q: Api.FindingsQuery): Either[Cli.Error, List[GeoHit]] = call(Api.findings)(q)
  def findingsPage(q: Api.FindingsQuery): Either[Cli.Error, GeoHitPage] = call(Api.findingsPage)(q)
  def aggregate(q: Api.AggregateQuery): Either[Cli.Error, TimeHistogram] = call(Api.aggregate)(q)
  def neighbors(ck: com.histmersion.cli.Client.NeighborsCk, kinds: List[com.histmersion.cli.Client.NeighborsKinds] = Nil): Either[Cli.Error, List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  def graph(ck: com.histmersion.cli.Client.GraphCk, depth: Option[com.histmersion.cli.Client.GraphDepth]): Either[Cli.Error, Source.Graph] =
    call(Api.graph)((ck, depth))
  def contradictions(ck: Option[com.histmersion.cli.Client.ContradictionsCk], since: Option[com.histmersion.cli.Client.ContradictionsSince]): Either[Cli.Error, ContradictionsResult] =
    call(Api.contradictions)((ck, since))
  def seed(req: SeedRequest): Either[Cli.Error, SeedResult] = call(Api.seed)(req)
  def reindex(after: Option[com.histmersion.cli.Client.ReindexAfter], limit: Option[com.histmersion.cli.Client.ReindexLimit]): Either[Cli.Error, ReindexResult] =
    call(Api.reindex)((after, limit))

  // ── backup / restore (plan §1.10): the DUMP is taken server-side; the host only copies it off ──
  def configReload: Either[Cli.Error, ReloadReport] = call(Api.configReload)(())
  def settings(prefix: Option[com.histmersion.cli.Client.SettingsPrefix]): Either[Cli.Error, ConfigEdit.Page] = call(ConfigEditApi.get)(prefix)
  def writeSettings(w: ConfigEdit.Write): Either[Cli.Error, ConfigEdit.Written] = call(ConfigEditApi.write)(w)
  def restartServer: Either[Cli.Error, ConfigEdit.Restart] = call(ConfigEditApi.restart)(())
  def backup: Either[Cli.Error, Backup.Status] = call(Api.backup)(())
  def backupStatus: Either[Cli.Error, Backup.Status] = call(Api.backupStatus)(())
  def vacuum(in: Vacuum.Request): Either[Cli.Error, JobInfo] = call(Api.maintenanceVacuum)(in)
  def backups: Either[Cli.Error, List[Backup.Info]] = call(Api.backups)(())
  def restore(req: RestoreRequest): Either[Cli.Error, RestoreResult] = call(Api.restore)(req)

  /** Stream one archive into `dest`. Tapir's `fileBody` is interpreted by sttp as `asFileAlways` against the file
    * `SttpClientOptions.createFile` hands it, so the response is written straight to disk — a multi-GB dump never
    * becomes a byte array.
    */
  def downloadBackup(file: com.histmersion.cli.Client.DownloadBackupFile, dest: java.io.File): Either[Cli.Error, java.io.File] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    send(authed(toFile.toRequest(Api.downloadBackup, Some(base)).apply(file)))

  /** Stream a source's stored original into `dest`; returns it with its `Content-Disposition`, which names the format
    * ([[Ingest.dispositionExtension]]).
    */
  def original(ck: com.histmersion.cli.Client.OriginalCk, dest: java.io.File): Either[Cli.Error, (java.io.File, com.histmersion.cli.Client.OriginalResultPart2Part2)] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    send(authed(toFile.toRequest(Api.original, Some(base)).apply(ck)))

  def sourceView(q: Api.SourceViewQuery): Either[Cli.Error, com.histmersion.cli.Client.SourceViewResultPart2] = call(Api.sourceView)(q)
  def passages(q: Api.PassagesQuery): Either[Cli.Error, PassageResult] = call(Api.passages)(q)

  // ── portable bundles (docs/research-merge-import.md) ────────────────────────────────────────
  def instance: Either[Cli.Error, InstanceInfo] = call(Api.instance)(())
  def setInstance(label: Option[com.histmersion.cli.Client.SetInstanceLabel]): Either[Cli.Error, InstanceInfo] = call(Api.setInstance)(InstanceLabel(label))

  /** Stream a bundle into `dest` (as [[downloadBackup]]: written to disk, never a byte array). */
  def exportBundle(since: Option[com.histmersion.cli.Client.ExportBundleSince], dest: java.io.File): Either[Cli.Error, (java.io.File, com.histmersion.cli.Client.ExportBundleResultPart2Part2)] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    send(authed(toFile.toRequest(Api.exportBundle, Some(base)).apply(since)))

  /** The bundle file is the request body, streamed from disk. */
  def verifyBundle(file: java.io.File): Either[Cli.Error, Bundle.VerifyResult] = call(Api.verifyBundle)(file)
  def importBundle(dryRun: com.histmersion.cli.Client.ImportBundleDryRun, file: java.io.File): Either[Cli.Error, Bundle.ImportResult] =
    call(Api.importBundle)((dryRun, file))
  // the merge plan (M3): the bundle is uploaded once, then planned and imported by its sha256
  def uploadBundle(file: java.io.File): Either[Cli.Error, Bundle.Upload] = call(Api.uploadBundle)(file)
  def mergePlan(req: MergePlan.Request): Either[Cli.Error, MergePlan] = call(Api.mergePlan)(req)
  def mergeImport(req: MergeImportRequest): Either[Cli.Error, Bundle.ImportResult] = call(Api.mergeImport)(req)
  def mergeApply(req: MergeApplyRequest): Either[Cli.Error, MergeApplyJob] = call(Api.mergeApply)(req)
  def mergeApplyStatus(job: com.histmersion.cli.Client.MergeApplyStatusJob): Either[Cli.Error, MergeApplyJob] = call(Api.mergeApplyStatus)(job)
  def merges(limit: Option[com.histmersion.cli.Client.MergesLimit]): Either[Cli.Error, List[MergeInfo]] = call(Api.merges)(limit)
  def mergeDetail(id: com.histmersion.cli.Client.MergeDetailId): Either[Cli.Error, MergeDetail] = call(Api.mergeDetail)(id)
  def mergeLedger(uid: Option[com.histmersion.cli.Client.MergeLedgerUid], merge: Option[com.histmersion.cli.Client.MergeLedgerMerge], limit: Option[com.histmersion.cli.Client.MergeLedgerLimit]): Either[Cli.Error, MergeLedger] =
    call(Api.mergeLedger)((uid, merge, limit))
  // label renames (V042, V053)
  def renameArticle(slug: com.histmersion.cli.Client.RenameArticleSlug, to: com.histmersion.cli.Client.RenameArticleTo): Either[Cli.Error, LabelRenamed] =
    call(Api.renameArticle)((slug, LabelRename(to)))
  def renameEvent(id: com.histmersion.cli.Client.RenameEventId, to: com.histmersion.cli.Client.RenameEventTo): Either[Cli.Error, LabelRenamed] =
    call(Api.renameEvent)((id, LabelRename(to)))
  def renameGoal(slug: com.histmersion.cli.Client.RenameGoalSlug, to: com.histmersion.cli.Client.RenameGoalTo): Either[Cli.Error, LabelRenamed] =
    call(Api.renameGoal)((slug, LabelRename(to)))
  def renamePlace(id: com.histmersion.cli.Client.RenamePlaceId, to: com.histmersion.cli.Client.RenamePlaceTo): Either[Cli.Error, LabelRenamed] =
    call(Api.renamePlace)((id, LabelRename(to)))

  // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────
  def putSource(ck: com.histmersion.cli.Client.PutSourceCk, in: Source.Put, strict: com.histmersion.cli.Client.PutSourceStrict = false): Either[Cli.Error, Source.WithFindings] =
    call(Api.putSource)((ck, in, strict))
  def patchSource(ck: com.histmersion.cli.Client.PatchSourceCk, p: Source.Patch, strict: com.histmersion.cli.Client.PatchSourceStrict = false): Either[Cli.Error, Source.WithFindings] =
    call(Api.patchSource)((ck, p, strict))
  // the formatting lint of agent-written Markdown
  def lintSource(ck: com.histmersion.cli.Client.LintSourceCk): Either[Cli.Error, FormatReport] = call(Api.lintSource)(ck)
  def fixSourceFormat(ck: com.histmersion.cli.Client.FixSourceFormatCk): Either[Cli.Error, FormatReport] = call(Api.fixSourceFormat)(ck)
  def lintArticle(slug: com.histmersion.cli.Client.LintArticleSlug): Either[Cli.Error, FormatReport] = call(Api.lintArticle)(slug)
  def fixArticleFormat(slug: com.histmersion.cli.Client.FixArticleFormatSlug): Either[Cli.Error, FormatReport] = call(Api.fixArticleFormat)(slug)
  def checkArticle(slug: com.histmersion.cli.Client.CheckArticleSlug, in: Article.Edits.ArticleCheckRequest): Either[Cli.Error, Article.Edits.ArticleCheck] =
    call(Api.checkArticle)((slug, in))
  def lintMarkdown(in: FormatCheck): Either[Cli.Error, FormatReport] = call(OpsApi.lintMarkdown)(in)
  def importNote(req: NoteImport): Either[Cli.Error, SeedResult] = call(Api.importNote)(req)
  def renameSource(ck: com.histmersion.cli.Client.RenameSourceCk, newCk: com.histmersion.cli.Client.RenameSourceNewCk): Either[Cli.Error, Source.Renamed] =
    call(Api.renameSource)((ck, Rename.Request(newCk)))
  def sourceAliases: Either[Cli.Error, List[Source.Alias]] = call(Api.sourceAliases)(())
  def deleteSource(ck: com.histmersion.cli.Client.DeleteSourceCk, apply: com.histmersion.cli.Client.DeleteSourceApply): Either[Cli.Error, Source.Deletion] =
    call(Api.deleteSource)((ck, Source.DeleteRequest(apply)))

  // ── the gazetteer (plan §1.6a) ──────────────────────────────────────────────────────────────
  def places(q: Option[com.histmersion.cli.Client.PlacesQ], asOf: Option[com.histmersion.cli.Client.PlacesAsOf], limit: Option[com.histmersion.cli.Client.PlacesLimit]): Either[Cli.Error, List[Place.Hit]] =
    call(Api.places)((q, asOf, limit))
  def place(id: com.histmersion.cli.Client.PlaceId, asOf: Option[com.histmersion.cli.Client.PlaceAsOf]): Either[Cli.Error, PlaceDetail] = call(Api.place)((id, asOf, None))
  def placeChildren(id: com.histmersion.cli.Client.PlaceChildrenId, asOf: Option[com.histmersion.cli.Client.PlaceChildrenAsOf]): Either[Cli.Error, List[Place.Summary]] =
    call(Api.placeChildren)((id, asOf))
  def placeTree(id: com.histmersion.cli.Client.PlaceTreeId, asOf: Option[com.histmersion.cli.Client.PlaceTreeAsOf], depth: Option[com.histmersion.cli.Client.PlaceTreeDepth]): Either[Cli.Error, List[Place.TreeRow]] =
    call(Api.placeTree)((id, asOf, depth))
  def importPlaces(
      kind: Option[com.histmersion.cli.Client.ImportPlacesKind],
      body: com.histmersion.cli.Client.ImportPlacesBody,
      dataset: Dataset = Dataset.Local
  ): Either[Cli.Error, Place.ImportResult] =
    call(Api.importPlaces)((kind, Some(dataset.wire), body))
  def placeNames(place: Option[com.histmersion.cli.Client.PlaceNamesPlace]): Either[Cli.Error, List[ManualPlaceName]] = call(Api.placeNames)(place)
  def addPlaceName(id: com.histmersion.cli.Client.AddPlaceNameId, in: Place.NameAdd): Either[Cli.Error, Place.NameChanged] =
    call(Api.addPlaceName)((id, in))
  def removePlaceName(id: com.histmersion.cli.Client.RemovePlaceNameId, in: Place.NameRemove): Either[Cli.Error, Place.NameChanged] =
    call(Api.removePlaceName)((id, in))
  def addPlace(in: Place.Add): Either[Cli.Error, Place.Summary] = call(Api.addPlace)(in)
  def placeOverrides(place: Option[com.histmersion.cli.Client.PlaceOverridesPlace]): Either[Cli.Error, List[Place.Override]] = call(Api.placeOverrides)(place)
  def setPlaceOverride(id: com.histmersion.cli.Client.SetPlaceOverrideId, in: Place.OverrideSet): Either[Cli.Error, Place.OverrideChanged] =
    call(Api.setPlaceOverride)((id, in))
  def mergePlace(id: com.histmersion.cli.Client.MergePlaceId, in: Place.MergeIn): Either[Cli.Error, Place.Gone] = call(Api.mergePlace)((id, in))
  def removePlace(id: com.histmersion.cli.Client.RemovePlaceId, in: Place.RemoveIn): Either[Cli.Error, Place.Gone] = call(Api.removePlace)((id, in))
  def rebuildPlaces: Either[Cli.Error, Place.RebuildResult] = call(Api.rebuildPlaces)(())
  def fetchExternalPlaces(source: com.histmersion.cli.Client.FetchExternalPlacesSource, dryRun: com.histmersion.cli.Client.FetchExternalPlacesDryRun): Either[Cli.Error, Place.ImportResult] =
    call(Api.fetchExternalPlaces)((source, dryRun))
  def placeSources: Either[Cli.Error, List[Gazetteer.SourceInfo]] = call(Api.placeSources)(())
  def loadPlaces(source: com.histmersion.cli.Client.LoadPlacesSource, apply: com.histmersion.cli.Client.LoadPlacesApply): Either[Cli.Error, JobInfo] = call(Api.loadPlaces)((source, apply))
  def buildHierarchy: Either[Cli.Error, SpatialHierarchyResult] = call(Api.buildHierarchy)(())
  // ── named periods (#719) and first-run data (#720) ──────────────────────────────────────────
  def periods(
      q: Option[com.histmersion.cli.Client.PeriodsQ],
      region: Option[com.histmersion.cli.Client.PeriodsRegion],
      from: Option[com.histmersion.cli.Client.PeriodsFrom],
      to: Option[com.histmersion.cli.Client.PeriodsTo]
  ): Either[Cli.Error, List[HistoricalPeriod]] = call(Api.periods)((q, region, from, to))
  def period(id: com.histmersion.cli.Client.PeriodId): Either[Cli.Error, Period.Detail] = call(Api.period)(id)
  def seedPeriods(replace: com.histmersion.cli.Client.SeedPeriodsReplace = false): Either[Cli.Error, Period.ImportResult] = call(Api.seedPeriods)(replace)
  def importPeriods(
      collections: List[com.histmersion.cli.Client.ImportPeriodsCollections],
      body: com.histmersion.cli.Client.ImportPeriodsBody,
      dataset: Dataset = Dataset.Local
  ): Either[Cli.Error, Period.ImportResult] =
    call(Api.importPeriods)((collections, Some(dataset.wire), body))
  def fetchPeriods(source: com.histmersion.cli.Client.FetchPeriodsSource, collections: List[com.histmersion.cli.Client.FetchPeriodsCollections]): Either[Cli.Error, Period.ImportResult] =
    call(Api.fetchPeriods)((source, collections))
  def init(fetch: com.histmersion.cli.Client.InitFetch): Either[Cli.Error, JobInfo] = call(Api.init)(fetch)
  // ── events (plan §1.2) ──────────────────────────────────────────────────────────────────────
  def listEvents(q: Api.EventQuery): Either[Cli.Error, EventList] = call(Api.events)(q)
  def getEvent(id: com.histmersion.cli.Client.GetEventId): Either[Cli.Error, EventDetail] = call(Api.getEvent)(id)
  def putEvent(id: com.histmersion.cli.Client.PutEventId, in: EventPut): Either[Cli.Error, EventDetail] = call(Api.putEvent)((id, in))
  def patchEvent(id: com.histmersion.cli.Client.PatchEventId, p: EventPatch): Either[Cli.Error, EventDetail] = call(Api.patchEvent)((id, p))
  def deleteEvent(id: com.histmersion.cli.Client.DeleteEventId): Either[Cli.Error, EventDeleted] = call(Api.deleteEvent)(id)
  // ── goals (plan §1.5) ──────────────────────────────────────────────────────────────────────
  def listGoals(q: Api.GoalQuery): Either[Cli.Error, Goal.List] = call(Api.goals)(q)
  def getGoal(slug: com.histmersion.cli.Client.GetGoalSlug): Either[Cli.Error, Goal] = call(Api.getGoal)(slug)
  def putGoal(slug: com.histmersion.cli.Client.PutGoalSlug, in: Goal.Put): Either[Cli.Error, Goal] = call(Api.putGoal)((slug, in))
  def patchGoal(slug: com.histmersion.cli.Client.PatchGoalSlug, p: Goal.Patch): Either[Cli.Error, Goal] = call(Api.patchGoal)((slug, p))
  def deleteGoal(slug: com.histmersion.cli.Client.DeleteGoalSlug): Either[Cli.Error, Goal.Deleted] = call(Api.deleteGoal)(slug)
  // ── agent state (plan §1.5) ───────────────────────────────────────────────────────────────
  def agentStateList(): Either[Cli.Error, List[AgentStateEntry]] = call(Api.agentStateList)(())
  def agentStateGet(key: com.histmersion.cli.Client.AgentStateGetKey): Either[Cli.Error, AgentStateEntry] = call(Api.agentStateGet)(key)
  def agentStateSet(key: com.histmersion.cli.Client.AgentStateSetKey, in: AgentStatePut): Either[Cli.Error, AgentStateEntry] =
    call(Api.agentStateSet)((key, in))
  def agentStateDelete(key: com.histmersion.cli.Client.AgentStateDeleteKey): Either[Cli.Error, AgentStateDeleted] = call(Api.agentStateDelete)(key)
  // ── attestations (plan §1.3) ───────────────────────────────────────────────────────────────
  def listAttestations(q: Api.AttestationQuery): Either[Cli.Error, Attestation.List] = call(Api.attestations)(q)
  def getAttestation(id: com.histmersion.cli.Client.GetAttestationId): Either[Cli.Error, Attestation] = call(Api.getAttestation)(id)
  def createAttestation(in: Attestation.Put): Either[Cli.Error, Attestation] = call(Api.createAttestation)(in)
  def patchAttestation(id: com.histmersion.cli.Client.PatchAttestationId, p: Attestation.Patch): Either[Cli.Error, Attestation] =
    call(Api.patchAttestation)((id, p))
  def deleteAttestation(id: com.histmersion.cli.Client.DeleteAttestationId): Either[Cli.Error, Attestation.Deleted] = call(Api.deleteAttestation)(id)

  def exportSource(ck: com.histmersion.cli.Client.ExportSourceCk): Either[Cli.Error, com.histmersion.cli.Client.ExportSourceResultPart2] = call(Api.exportSource)(ck)
  def citekeys: Either[Cli.Error, List[com.histmersion.cli.Client.CitekeysResultPart2]] = call(Api.citekeys)(())
  def resolveEdges: Either[Cli.Error, EdgesResolved] = call(Api.resolveEdges)(())

  // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────
  def listArticles(q: Api.ArticleQuery): Either[Cli.Error, Article.List] = call(Api.articles)(q)
  def getArticle(slug: com.histmersion.cli.Client.GetArticleSlug): Either[Cli.Error, Article.View] = call(Api.getArticle)((slug, false))
  def putArticle(slug: com.histmersion.cli.Client.PutArticleSlug, in: Article.Put, strict: com.histmersion.cli.Client.PutArticleStrict = false): Either[Cli.Error, Article.View] =
    call(Api.putArticle)((slug, in, strict))
  def patchArticle(slug: com.histmersion.cli.Client.PatchArticleSlug, p: Article.Patch, strict: com.histmersion.cli.Client.PatchArticleStrict = false): Either[Cli.Error, Article.View] =
    call(Api.patchArticle)((slug, p, strict))
  def deleteArticle(slug: com.histmersion.cli.Client.DeleteArticleSlug): Either[Cli.Error, Article.Deleted] = call(Api.deleteArticle)(slug)
  def articleCoverage(slug: com.histmersion.cli.Client.ArticleCoverageSlug): Either[Cli.Error, Coverage] = call(Api.articleCoverage)(slug)
  def articleReview(slug: com.histmersion.cli.Client.ArticleReviewSlug): Either[Cli.Error, Article.ReviewView] = call(Api.articleReview)(slug)
  def exportArticle(slug: com.histmersion.cli.Client.ExportArticleSlug): Either[Cli.Error, com.histmersion.cli.Client.ExportArticleResultPart2] = call(Api.exportArticle)(slug)
  def importArticle(req: Article.Import): Either[Cli.Error, SeedResult] = call(Api.importArticle)(req)
  def articleSlugs: Either[Cli.Error, List[com.histmersion.cli.Client.ArticleSlugsResultPart2]] = call(Api.articleSlugs)(())
  def articleVersions(slug: com.histmersion.cli.Client.ArticleVersionsSlug): Either[Cli.Error, List[Article.Version]] =
    call(Api.articleVersions)(slug)
  def articleVersion(slug: com.histmersion.cli.Client.ArticleVersionSlug, versionId: com.histmersion.cli.Client.ArticleVersionVersionId): Either[Cli.Error, Article.VersionFull] =
    call(Api.articleVersion)((slug, versionId))
  // ── lint · queue · read-only query (plan §5.2 stream 2.8) ───────────────────────────────────
  def lint(rules: List[com.histmersion.cli.Client.LintRules]): Either[Cli.Error, Lint.LintReport] = call(OpsApi.lint)(rules)
  def lintFix(rules: List[com.histmersion.cli.Client.LintFixRules]): Either[Cli.Error, Lint.LintFixResult] = call(OpsApi.lintFix)(rules)
  def queueList(list: com.histmersion.cli.Client.QueueListList, all: com.histmersion.cli.Client.QueueListAll): Either[Cli.Error, QueueListing] =
    call(OpsApi.queueList)((list, all, None, None))
  def queueAdd(list: com.histmersion.cli.Client.QueueAddList, in: Queue.QueueAdd): Either[Cli.Error, Queue.QueueItem] =
    call(OpsApi.queueAdd)((list, in))
  def queuePatch(id: com.histmersion.cli.Client.QueuePatchId, p: Queue.QueuePatch): Either[Cli.Error, Queue.QueueItem] =
    call(OpsApi.queuePatch)((id, p))
  def queueDelete(id: com.histmersion.cli.Client.QueueDeleteId): Either[Cli.Error, Queue.QueueItem] = call(OpsApi.queueDelete)(id)
  def queueExport(list: com.histmersion.cli.Client.QueueExportList): Either[Cli.Error, com.histmersion.cli.Client.QueueExportResultPart2] = call(OpsApi.queueExport)(list)
  def goalsExport: Either[Cli.Error, com.histmersion.cli.Client.GoalsExportResultPart2] = call(OpsApi.goalsExport)(())
  def queueImport(list: com.histmersion.cli.Client.QueueImportList, req: Queue.QueueImport): Either[Cli.Error, Queue.QueueImported] =
    call(OpsApi.queueImport)((list, req))
  def query(sql: com.histmersion.cli.Client.QuerySql): Either[Cli.Error, Query.QueryResult] = call(OpsApi.query)(Query.QueryRequest(sql))
  def schemaTables: Either[Cli.Error, Query.SchemaTables] = call(OpsApi.schemaTables)(())
  // ── ingest, jobs and assets (plan §1.8) ─────────────────────────────────────────────────────
  /** Upload one document. The body is the FILE itself (tapir `fileBody`), so a 200 MB scan is streamed rather than held
    * in memory, and every other parameter travels as a query parameter.
    */
  def ingest(p: Ingest.Params, file: java.io.File): Either[Cli.Error, Ingest.Accepted] =
    call(Api.ingest)((p, file))
  def reocr(ck: com.histmersion.cli.Client.ReocrCk, quality: com.histmersion.cli.Client.ReocrQuality): Either[Cli.Error, Ingest.Accepted] = call(Api.reocr)((ck, quality))
  def job(id: com.histmersion.api.jobs.JobInfo.Id): Either[Cli.Error, JobInfo] = call(Api.job)(id.value)
  def basemapStatus(): Either[Cli.Error, Basemap.Status] = call(BasemapApi.status)(())
  def basemapFetch(q: Basemap.FetchQuery): Either[Cli.Error, JobInfo] = call(BasemapApi.fetch)(q)
  def basemapRemove(q: Basemap.RemoveQuery): Either[Cli.Error, Basemap.Removed] = call(BasemapApi.remove)(q)
  def jobs(
      f: ListFilters.JobFilter,
      limit: Option[com.histmersion.cli.Client.JobsLimit],
      before: Option[com.histmersion.cli.Client.JobsBefore] = None
  ): Either[Cli.Error, (List[JobInfo], Paging.Page)] =
    call(Api.jobs)((f, limit, before, None))
  def assets(ck: com.histmersion.cli.Client.AssetsCk): Either[Cli.Error, List[AssetInfo]] = call(Api.assets)(ck)
  def putAssets(ck: com.histmersion.cli.Client.PutAssetsCk, in: List[AssetInput]): Either[Cli.Error, AssetsStored] =
    call(Api.putAssets)((ck, in))
  def assetDetail(ck: com.histmersion.cli.Client.AssetDetailCk, name: com.histmersion.cli.Client.AssetDetailName): Either[Cli.Error, AssetDetail] = call(Api.assetDetail)((ck, name))
  def patchAsset(ck: com.histmersion.cli.Client.PatchAssetCk, name: com.histmersion.cli.Client.PatchAssetName, p: AssetPatch): Either[Cli.Error, AssetDetail] =
    call(Api.patchAsset)((ck, name, p))
  def assetLinks(
      ck: Option[com.histmersion.cli.Client.AssetLinksCk],
      asset: Option[com.histmersion.cli.Client.AssetLinksAsset],
      tt: Option[com.histmersion.cli.Client.AssetLinksTt],
      ti: Option[com.histmersion.cli.Client.AssetLinksTi]
  ): Either[Cli.Error, AssetLink.List] =
    call(Api.assetLinks)((ck, asset, tt, ti))
  def createAssetLink(in: AssetLink.Put): Either[Cli.Error, AssetLink] = call(Api.createAssetLink)(in)
  def deleteAssetLink(id: com.histmersion.cli.Client.DeleteAssetLinkId): Either[Cli.Error, AssetLink.Deleted] = call(Api.deleteAssetLink)(id)
  // ── media from outside providers (#1446) ──
  def mediaImport(req: MediaImportRequest): Either[Cli.Error, MediaImportReport] = call(Api.mediaImport)(req)
  def mediaImportJob(req: MediaImportRequest): Either[Cli.Error, JobInfo] = call(Api.mediaImportJob)(req)
  def mediaCovers(req: MediaCoversRequest): Either[Cli.Error, MediaCoversReport] = call(Api.mediaCovers)(req)
  def mediaCoversJob(req: MediaCoversRequest): Either[Cli.Error, JobInfo] = call(Api.mediaCoversJob)(req)
  def putSourceCover(p: SourceCoverParams, bytes: Array[Byte]): Either[Cli.Error, MediaItem] =
    call(Api.putSourceCover)((p, bytes))
  def mediaList(q: Api.MediaQuery): Either[Cli.Error, MediaList] = call(Api.mediaList)(q)
  def mediaShow(uid: com.histmersion.cli.Client.MediaShowUid): Either[Cli.Error, MediaItem] = call(Api.mediaShow)(uid)
  def mediaFacets: Either[Cli.Error, MediaFacets] = call(Api.mediaFacets)(())
  def mediaProviders: Either[Cli.Error, List[MediaProvider.Info]] = call(Api.mediaProviders)(())
  def fillFigureLicences(apply: com.histmersion.cli.Client.FillFigureLicencesApply): Either[Cli.Error, FigureLicenceFill] = call(Api.fillFigureLicences)(apply)

object Client:
  /** POST/PUT/PATCH/DELETE change the corpus; anything else (a GET download) only reads it. */
  type ServerUrl = ServerUrl.T
  object ServerUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Token = Token.T
  object Token extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SearchQ = SearchQ.T
  object SearchQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsField = FacetsField.T
  object FacetsField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsPrefix = FacetsPrefix.T
  object FacetsPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetSourceCk = GetSourceCk.T
  object GetSourceCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NeighborsCk = NeighborsCk.T
  object NeighborsCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NeighborsKinds = NeighborsKinds.T
  object NeighborsKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GraphCk = GraphCk.T
  object GraphCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GraphDepth = GraphDepth.T
  object GraphDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContradictionsCk = ContradictionsCk.T
  object ContradictionsCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContradictionsSince = ContradictionsSince.T
  object ContradictionsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexAfter = ReindexAfter.T
  object ReindexAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexLimit = ReindexLimit.T
  object ReindexLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettingsPrefix = SettingsPrefix.T
  object SettingsPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadBackupFile = DownloadBackupFile.T
  object DownloadBackupFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalResultPart2Part2 = OriginalResultPart2Part2.T
  object OriginalResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalCk = OriginalCk.T
  object OriginalCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceViewResultPart2 = SourceViewResultPart2.T
  object SourceViewResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetInstanceLabel = SetInstanceLabel.T
  object SetInstanceLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportBundleResultPart2Part2 = ExportBundleResultPart2Part2.T
  object ExportBundleResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportBundleSince = ExportBundleSince.T
  object ExportBundleSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportBundleDryRun = ImportBundleDryRun.T
  object ImportBundleDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeApplyStatusJob = MergeApplyStatusJob.T
  object MergeApplyStatusJob extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergesLimit = MergesLimit.T
  object MergesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeDetailId = MergeDetailId.T
  object MergeDetailId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeLedgerUid = MergeLedgerUid.T
  object MergeLedgerUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeLedgerMerge = MergeLedgerMerge.T
  object MergeLedgerMerge extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeLedgerLimit = MergeLedgerLimit.T
  object MergeLedgerLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameArticleSlug = RenameArticleSlug.T
  object RenameArticleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameArticleTo = RenameArticleTo.T
  object RenameArticleTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameEventId = RenameEventId.T
  object RenameEventId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameEventTo = RenameEventTo.T
  object RenameEventTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameGoalSlug = RenameGoalSlug.T
  object RenameGoalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameGoalTo = RenameGoalTo.T
  object RenameGoalTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamePlaceId = RenamePlaceId.T
  object RenamePlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamePlaceTo = RenamePlaceTo.T
  object RenamePlaceTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutSourceCk = PutSourceCk.T
  object PutSourceCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutSourceStrict = PutSourceStrict.T
  object PutSourceStrict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchSourceCk = PatchSourceCk.T
  object PatchSourceCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchSourceStrict = PatchSourceStrict.T
  object PatchSourceStrict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LintSourceCk = LintSourceCk.T
  object LintSourceCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixSourceFormatCk = FixSourceFormatCk.T
  object FixSourceFormatCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LintArticleSlug = LintArticleSlug.T
  object LintArticleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixArticleFormatSlug = FixArticleFormatSlug.T
  object FixArticleFormatSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckArticleSlug = CheckArticleSlug.T
  object CheckArticleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameSourceCk = RenameSourceCk.T
  object RenameSourceCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameSourceNewCk = RenameSourceNewCk.T
  object RenameSourceNewCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteSourceCk = DeleteSourceCk.T
  object DeleteSourceCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteSourceApply = DeleteSourceApply.T
  object DeleteSourceApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesQ = PlacesQ.T
  object PlacesQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesAsOf = PlacesAsOf.T
  object PlacesAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesLimit = PlacesLimit.T
  object PlacesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceId = PlaceId.T
  object PlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceAsOf = PlaceAsOf.T
  object PlaceAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceChildrenId = PlaceChildrenId.T
  object PlaceChildrenId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceChildrenAsOf = PlaceChildrenAsOf.T
  object PlaceChildrenAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceTreeId = PlaceTreeId.T
  object PlaceTreeId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceTreeAsOf = PlaceTreeAsOf.T
  object PlaceTreeAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceTreeDepth = PlaceTreeDepth.T
  object PlaceTreeDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPlacesKind = ImportPlacesKind.T
  object ImportPlacesKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPlacesBody = ImportPlacesBody.T
  object ImportPlacesBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceNamesPlace = PlaceNamesPlace.T
  object PlaceNamesPlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddPlaceNameId = AddPlaceNameId.T
  object AddPlaceNameId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemovePlaceNameId = RemovePlaceNameId.T
  object RemovePlaceNameId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceOverridesPlace = PlaceOverridesPlace.T
  object PlaceOverridesPlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetPlaceOverrideId = SetPlaceOverrideId.T
  object SetPlaceOverrideId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergePlaceId = MergePlaceId.T
  object MergePlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemovePlaceId = RemovePlaceId.T
  object RemovePlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchExternalPlacesSource = FetchExternalPlacesSource.T
  object FetchExternalPlacesSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchExternalPlacesDryRun = FetchExternalPlacesDryRun.T
  object FetchExternalPlacesDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadPlacesSource = LoadPlacesSource.T
  object LoadPlacesSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadPlacesApply = LoadPlacesApply.T
  object LoadPlacesApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsQ = PeriodsQ.T
  object PeriodsQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsRegion = PeriodsRegion.T
  object PeriodsRegion extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsFrom = PeriodsFrom.T
  object PeriodsFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsTo = PeriodsTo.T
  object PeriodsTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodId = PeriodId.T
  object PeriodId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedPeriodsReplace = SeedPeriodsReplace.T
  object SeedPeriodsReplace extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPeriodsCollections = ImportPeriodsCollections.T
  object ImportPeriodsCollections extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPeriodsBody = ImportPeriodsBody.T
  object ImportPeriodsBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchPeriodsSource = FetchPeriodsSource.T
  object FetchPeriodsSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchPeriodsCollections = FetchPeriodsCollections.T
  object FetchPeriodsCollections extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InitFetch = InitFetch.T
  object InitFetch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetEventId = GetEventId.T
  object GetEventId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutEventId = PutEventId.T
  object PutEventId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchEventId = PatchEventId.T
  object PatchEventId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteEventId = DeleteEventId.T
  object DeleteEventId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetGoalSlug = GetGoalSlug.T
  object GetGoalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutGoalSlug = PutGoalSlug.T
  object PutGoalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchGoalSlug = PatchGoalSlug.T
  object PatchGoalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteGoalSlug = DeleteGoalSlug.T
  object DeleteGoalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentStateGetKey = AgentStateGetKey.T
  object AgentStateGetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentStateSetKey = AgentStateSetKey.T
  object AgentStateSetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentStateDeleteKey = AgentStateDeleteKey.T
  object AgentStateDeleteKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetAttestationId = GetAttestationId.T
  object GetAttestationId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchAttestationId = PatchAttestationId.T
  object PatchAttestationId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteAttestationId = DeleteAttestationId.T
  object DeleteAttestationId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportSourceResultPart2 = ExportSourceResultPart2.T
  object ExportSourceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportSourceCk = ExportSourceCk.T
  object ExportSourceCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitekeysResultPart2 = CitekeysResultPart2.T
  object CitekeysResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetArticleSlug = GetArticleSlug.T
  object GetArticleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutArticleSlug = PutArticleSlug.T
  object PutArticleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutArticleStrict = PutArticleStrict.T
  object PutArticleStrict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchArticleSlug = PatchArticleSlug.T
  object PatchArticleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchArticleStrict = PatchArticleStrict.T
  object PatchArticleStrict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteArticleSlug = DeleteArticleSlug.T
  object DeleteArticleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCoverageSlug = ArticleCoverageSlug.T
  object ArticleCoverageSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleReviewSlug = ArticleReviewSlug.T
  object ArticleReviewSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportArticleResultPart2 = ExportArticleResultPart2.T
  object ExportArticleResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportArticleSlug = ExportArticleSlug.T
  object ExportArticleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleSlugsResultPart2 = ArticleSlugsResultPart2.T
  object ArticleSlugsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleVersionsSlug = ArticleVersionsSlug.T
  object ArticleVersionsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleVersionSlug = ArticleVersionSlug.T
  object ArticleVersionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleVersionVersionId = ArticleVersionVersionId.T
  object ArticleVersionVersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LintRules = LintRules.T
  object LintRules extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LintFixRules = LintFixRules.T
  object LintFixRules extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueListList = QueueListList.T
  object QueueListList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueListAll = QueueListAll.T
  object QueueListAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueAddList = QueueAddList.T
  object QueueAddList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueuePatchId = QueuePatchId.T
  object QueuePatchId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueDeleteId = QueueDeleteId.T
  object QueueDeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueExportResultPart2 = QueueExportResultPart2.T
  object QueueExportResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueExportList = QueueExportList.T
  object QueueExportList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsExportResultPart2 = GoalsExportResultPart2.T
  object GoalsExportResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueImportList = QueueImportList.T
  object QueueImportList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuerySql = QuerySql.T
  object QuerySql extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReocrCk = ReocrCk.T
  object ReocrCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReocrQuality = ReocrQuality.T
  object ReocrQuality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsLimit = JobsLimit.T
  object JobsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsBefore = JobsBefore.T
  object JobsBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetsCk = AssetsCk.T
  object AssetsCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutAssetsCk = PutAssetsCk.T
  object PutAssetsCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetDetailCk = AssetDetailCk.T
  object AssetDetailCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetDetailName = AssetDetailName.T
  object AssetDetailName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchAssetCk = PatchAssetCk.T
  object PatchAssetCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchAssetName = PatchAssetName.T
  object PatchAssetName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetLinksCk = AssetLinksCk.T
  object AssetLinksCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetLinksAsset = AssetLinksAsset.T
  object AssetLinksAsset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetLinksTt = AssetLinksTt.T
  object AssetLinksTt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetLinksTi = AssetLinksTi.T
  object AssetLinksTi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteAssetLinkId = DeleteAssetLinkId.T
  object DeleteAssetLinkId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaShowUid = MediaShowUid.T
  object MediaShowUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FillFigureLicencesApply = FillFigureLicencesApply.T
  object FillFigureLicencesApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsWriteResult = IsWriteResult.T
  object IsWriteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnexpectedResult = UnexpectedResult.T
  object UnexpectedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnexpectedStatus = UnexpectedStatus.T
  object UnexpectedStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnexpectedServerUrl = UnexpectedServerUrl.T
  object UnexpectedServerUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnexpectedWrite = UnexpectedWrite.T
  object UnexpectedWrite extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def isWrite(m: sttp.model.Method): com.histmersion.cli.Client.IsWriteResult =
    m == sttp.model.Method.POST || m == sttp.model.Method.PUT || m == sttp.model.Method.PATCH ||
      m == sttp.model.Method.DELETE

  /** What to say when the server answered something the endpoint does not describe. Pure. A refusal names what was
    * refused (#819: a refused backup download used to read "refused this write").
    */
  def unexpected(status: com.histmersion.cli.Client.UnexpectedStatus, serverUrl: com.histmersion.cli.Client.UnexpectedServerUrl, write: com.histmersion.cli.Client.UnexpectedWrite): com.histmersion.cli.Client.UnexpectedResult = status match
    case 401 | 403 =>
      val what = if write then "this write" else "this request"
      s"$serverUrl refused $what: it needs its API key — pass --token <key> or set HISTMERSION_TOKEN"
    case 404 => s"$serverUrl has no such endpoint (HTTP 404) — is the CLI newer than the server?"
    case c   => s"unexpected answer from $serverUrl: HTTP $c"
