package com.histmersion.cli

import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.tapir.client.sttp4.{SttpClientInterpreter, SttpClientOptions}
import sttp.tapir.PublicEndpoint
import sttp.model.Uri
import com.histmersion.api.{Api, ApiError, Dataset, OpsApi, Paging, SpatialHierarchyResult}
import com.histmersion.config.*
import com.histmersion.api.aliases.{InstanceInfo, LabelRename, LabelRenamed}
import com.histmersion.api.automation.{
  AgentStateDeleted,
  AgentStateEntry,
  AgentStatePut,
  Goal,
  GoalDeleted,
  GoalList,
  GoalPatch,
  GoalPut,
  ListFilters,
  Queue
}
import com.histmersion.api.backup.{BackupInfo, BackupStatus, RestoreRequest, RestoreResult}
import com.histmersion.api.basemap.{BasemapApi, BasemapFetchQuery, BasemapRemoved, BasemapRemoveQuery, BasemapStatus}
import com.histmersion.api.catalogue.{
  Attestation,
  AttestationDeleted,
  AttestationList,
  AttestationPatch,
  AttestationPut,
  EventDeleted,
  EventList,
  EventPatch,
  EventPut,
  GazetteerSourceInfo,
  HistoricalPeriod,
  ManualPlaceName,
  PeriodDetail,
  PeriodImportResult,
  PlaceAdd,
  PlaceGone,
  PlaceHit,
  PlaceImportResult,
  PlaceMergeIn,
  PlaceNameAdd,
  PlaceNameChanged,
  PlaceNameRemove,
  PlaceOverride,
  PlaceOverrideChanged,
  PlaceOverrideSet,
  PlaceRebuildResult,
  PlaceRemoveIn,
  PlaceSummary,
  PlaceTreeRow
}
import com.histmersion.api.checks.{FormatCheck, FormatReport, Lint}
import com.histmersion.api.exchange.{
  BundleImportResult,
  BundleUpload,
  BundleVerifyResult,
  MergeApplyJob,
  MergeApplyRequest,
  MergeDetail,
  MergeImportRequest,
  MergeInfo,
  MergeLedger,
  MergePlan,
  MergePlanRequest
}
import com.histmersion.api.instance.{About, Health, VersionInfo}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.library.{
  ContradictionsResult,
  EdgesResolved,
  Facet,
  Filter,
  GeoHit,
  GeoHitPage,
  IngestAccepted,
  IngestParams,
  InstanceLabel,
  NeighborRow,
  NoteImport,
  PassageResult,
  Query,
  RenameRequest,
  SearchHit,
  SearchResult,
  SeedRequest,
  SeedResult,
  SourceAlias,
  SourceDeleteRequest,
  SourceDeletion,
  SourceGraph,
  SourcePatch,
  SourcePut,
  SourceRenamed,
  SourceWithFindings,
  TimeHistogram
}
import com.histmersion.api.maintenance.{ReindexResult, VacuumRequest}
import com.histmersion.api.media.{
  AssetDetail,
  AssetInfo,
  AssetInput,
  AssetLink,
  AssetLinkDeleted,
  AssetLinkList,
  AssetLinkPut,
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
  MediaProviderInfo,
  SourceCoverParams
}
import com.histmersion.api.research.{
  ArticleDeleted,
  ArticleEdits,
  ArticleImport,
  ArticleList,
  ArticlePatch,
  ArticlePut,
  ArticleReviewView,
  ArticleVersion,
  ArticleVersionFull,
  ArticleView,
  Coverage
}
import com.histmersion.api.settings.{ConfigEdit, ConfigEditApi, ReloadReport}
import com.histmersion.api.views.{EventDetail, PlaceDetail, QueueListing}

/** Typed HTTP client over the `Api` contract: each call is derived from the same Tapir endpoint the server interprets,
  * so request/response shapes cannot drift. Sync (java.net.http), native-safe. `token` is the
  * `Authorization: Bearer <key>` value for authenticated servers; `None` = local.
  */
final class Client(val serverUrl: String, token: Option[String] = None):
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
  private[cli] def call[I, O](ep: PublicEndpoint[I, ApiError, O, Any])(in: I): Either[CliError, O] =
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
  private def send[O](req: Request[sttp.tapir.DecodeResult[Either[ApiError, O]]]): Either[CliError, O] =
    scala.util.Try(req.send(backend)) match
      case scala.util.Failure(e) =>
        Left(
          CliError(
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
            Left(CliError(e.message, exitCode = code))
          case _ =>
            Left(
              CliError(
                Client.unexpected(resp.code.code, serverUrl, Client.isWrite(req.method)),
                exitCode = ExitCode.Refused
              )
            )

  def version: Either[CliError, VersionInfo] = call(Api.version)(())
  def health: Either[CliError, Health] = call(Api.health)(())
  def about: Either[CliError, About] = call(Api.about)(())
  def search(q: Option[String], limit: Option[Int]): Either[CliError, List[SearchHit]] =
    call(Api.search)((q, limit))
  def sources(f: Filter): Either[CliError, SearchResult] = call(Api.sources)(f)
  def facets(field: String, prefix: Option[String], f: Filter): Either[CliError, List[Facet]] =
    call(Api.facets)((field, prefix, f))
  def getSource(ck: String): Either[CliError, SourceWithFindings] = call(Api.getSource)((ck, true))
  def findings(q: Api.FindingsQuery): Either[CliError, List[GeoHit]] = call(Api.findings)(q)
  def findingsPage(q: Api.FindingsQuery): Either[CliError, GeoHitPage] = call(Api.findingsPage)(q)
  def aggregate(q: Api.AggregateQuery): Either[CliError, TimeHistogram] = call(Api.aggregate)(q)
  def neighbors(ck: String, kinds: List[String] = Nil): Either[CliError, List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  def graph(ck: String, depth: Option[Int]): Either[CliError, SourceGraph] =
    call(Api.graph)((ck, depth))
  def contradictions(ck: Option[String], since: Option[Long]): Either[CliError, ContradictionsResult] =
    call(Api.contradictions)((ck, since))
  def seed(req: SeedRequest): Either[CliError, SeedResult] = call(Api.seed)(req)
  def reindex(after: Option[String], limit: Option[Int]): Either[CliError, ReindexResult] =
    call(Api.reindex)((after, limit))

  // ── backup / restore (plan §1.10): the DUMP is taken server-side; the host only copies it off ──
  def configReload: Either[CliError, ReloadReport] = call(Api.configReload)(())
  def settings(prefix: Option[String]): Either[CliError, ConfigEdit.Page] = call(ConfigEditApi.get)(prefix)
  def writeSettings(w: ConfigEdit.Write): Either[CliError, ConfigEdit.Written] = call(ConfigEditApi.write)(w)
  def restartServer: Either[CliError, ConfigEdit.Restart] = call(ConfigEditApi.restart)(())
  def backup: Either[CliError, BackupStatus] = call(Api.backup)(())
  def backupStatus: Either[CliError, BackupStatus] = call(Api.backupStatus)(())
  def vacuum(in: VacuumRequest): Either[CliError, JobInfo] = call(Api.maintenanceVacuum)(in)
  def backups: Either[CliError, List[BackupInfo]] = call(Api.backups)(())
  def restore(req: RestoreRequest): Either[CliError, RestoreResult] = call(Api.restore)(req)

  /** Stream one archive into `dest`. Tapir's `fileBody` is interpreted by sttp as `asFileAlways` against the file
    * `SttpClientOptions.createFile` hands it, so the response is written straight to disk — a multi-GB dump never
    * becomes a byte array.
    */
  def downloadBackup(file: String, dest: java.io.File): Either[CliError, java.io.File] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    send(authed(toFile.toRequest(Api.downloadBackup, Some(base)).apply(file)))

  /** Stream a source's stored original into `dest`; returns it with its `Content-Disposition`, which names the format
    * ([[Ingest.dispositionExtension]]).
    */
  def original(ck: String, dest: java.io.File): Either[CliError, (java.io.File, String)] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    send(authed(toFile.toRequest(Api.original, Some(base)).apply(ck)))

  def sourceView(q: Api.SourceViewQuery): Either[CliError, String] = call(Api.sourceView)(q)
  def passages(q: Api.PassagesQuery): Either[CliError, PassageResult] = call(Api.passages)(q)

  // ── portable bundles (docs/research-merge-import.md) ────────────────────────────────────────
  def instance: Either[CliError, InstanceInfo] = call(Api.instance)(())
  def setInstance(label: Option[String]): Either[CliError, InstanceInfo] = call(Api.setInstance)(InstanceLabel(label))

  /** Stream a bundle into `dest` (as [[downloadBackup]]: written to disk, never a byte array). */
  def exportBundle(since: Option[String], dest: java.io.File): Either[CliError, (java.io.File, String)] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    send(authed(toFile.toRequest(Api.exportBundle, Some(base)).apply(since)))

  /** The bundle file is the request body, streamed from disk. */
  def verifyBundle(file: java.io.File): Either[CliError, BundleVerifyResult] = call(Api.verifyBundle)(file)
  def importBundle(dryRun: Boolean, file: java.io.File): Either[CliError, BundleImportResult] =
    call(Api.importBundle)((dryRun, file))
  // the merge plan (M3): the bundle is uploaded once, then planned and imported by its sha256
  def uploadBundle(file: java.io.File): Either[CliError, BundleUpload] = call(Api.uploadBundle)(file)
  def mergePlan(req: MergePlanRequest): Either[CliError, MergePlan] = call(Api.mergePlan)(req)
  def mergeImport(req: MergeImportRequest): Either[CliError, BundleImportResult] = call(Api.mergeImport)(req)
  def mergeApply(req: MergeApplyRequest): Either[CliError, MergeApplyJob] = call(Api.mergeApply)(req)
  def mergeApplyStatus(job: String): Either[CliError, MergeApplyJob] = call(Api.mergeApplyStatus)(job)
  def merges(limit: Option[Int]): Either[CliError, List[MergeInfo]] = call(Api.merges)(limit)
  def mergeDetail(id: String): Either[CliError, MergeDetail] = call(Api.mergeDetail)(id)
  def mergeLedger(uid: Option[String], merge: Option[String], limit: Option[Int]): Either[CliError, MergeLedger] =
    call(Api.mergeLedger)((uid, merge, limit))
  // label renames (V042, V053)
  def renameArticle(slug: String, to: String): Either[CliError, LabelRenamed] =
    call(Api.renameArticle)((slug, LabelRename(to)))
  def renameEvent(id: String, to: String): Either[CliError, LabelRenamed] = call(Api.renameEvent)((id, LabelRename(to)))
  def renameGoal(slug: String, to: String): Either[CliError, LabelRenamed] =
    call(Api.renameGoal)((slug, LabelRename(to)))
  def renamePlace(id: String, to: String): Either[CliError, LabelRenamed] = call(Api.renamePlace)((id, LabelRename(to)))

  // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────
  def putSource(ck: String, in: SourcePut, strict: Boolean = false): Either[CliError, SourceWithFindings] =
    call(Api.putSource)((ck, in, strict))
  def patchSource(ck: String, p: SourcePatch, strict: Boolean = false): Either[CliError, SourceWithFindings] =
    call(Api.patchSource)((ck, p, strict))
  // the formatting lint of agent-written Markdown
  def lintSource(ck: String): Either[CliError, FormatReport] = call(Api.lintSource)(ck)
  def fixSourceFormat(ck: String): Either[CliError, FormatReport] = call(Api.fixSourceFormat)(ck)
  def lintArticle(slug: String): Either[CliError, FormatReport] = call(Api.lintArticle)(slug)
  def fixArticleFormat(slug: String): Either[CliError, FormatReport] = call(Api.fixArticleFormat)(slug)
  def checkArticle(slug: String, in: ArticleEdits.ArticleCheckRequest): Either[CliError, ArticleEdits.ArticleCheck] =
    call(Api.checkArticle)((slug, in))
  def lintMarkdown(in: FormatCheck): Either[CliError, FormatReport] = call(OpsApi.lintMarkdown)(in)
  def importNote(req: NoteImport): Either[CliError, SeedResult] = call(Api.importNote)(req)
  def renameSource(ck: String, newCk: String): Either[CliError, SourceRenamed] =
    call(Api.renameSource)((ck, RenameRequest(newCk)))
  def sourceAliases: Either[CliError, List[SourceAlias]] = call(Api.sourceAliases)(())
  def deleteSource(ck: String, apply: Boolean): Either[CliError, SourceDeletion] =
    call(Api.deleteSource)((ck, SourceDeleteRequest(apply)))

  // ── the gazetteer (plan §1.6a) ──────────────────────────────────────────────────────────────
  def places(q: Option[String], asOf: Option[Long], limit: Option[Int]): Either[CliError, List[PlaceHit]] =
    call(Api.places)((q, asOf, limit))
  def place(id: String, asOf: Option[Long]): Either[CliError, PlaceDetail] = call(Api.place)((id, asOf, None))
  def placeChildren(id: String, asOf: Option[Long]): Either[CliError, List[PlaceSummary]] =
    call(Api.placeChildren)((id, asOf))
  def placeTree(id: String, asOf: Option[Long], depth: Option[Int]): Either[CliError, List[PlaceTreeRow]] =
    call(Api.placeTree)((id, asOf, depth))
  def importPlaces(
      kind: Option[String],
      body: String,
      dataset: Dataset = Dataset.Local
  ): Either[CliError, PlaceImportResult] =
    call(Api.importPlaces)((kind, Some(dataset.wire), body))
  def placeNames(place: Option[String]): Either[CliError, List[ManualPlaceName]] = call(Api.placeNames)(place)
  def addPlaceName(id: String, in: PlaceNameAdd): Either[CliError, PlaceNameChanged] = call(Api.addPlaceName)((id, in))
  def removePlaceName(id: String, in: PlaceNameRemove): Either[CliError, PlaceNameChanged] =
    call(Api.removePlaceName)((id, in))
  def addPlace(in: PlaceAdd): Either[CliError, PlaceSummary] = call(Api.addPlace)(in)
  def placeOverrides(place: Option[String]): Either[CliError, List[PlaceOverride]] = call(Api.placeOverrides)(place)
  def setPlaceOverride(id: String, in: PlaceOverrideSet): Either[CliError, PlaceOverrideChanged] =
    call(Api.setPlaceOverride)((id, in))
  def mergePlace(id: String, in: PlaceMergeIn): Either[CliError, PlaceGone] = call(Api.mergePlace)((id, in))
  def removePlace(id: String, in: PlaceRemoveIn): Either[CliError, PlaceGone] = call(Api.removePlace)((id, in))
  def rebuildPlaces: Either[CliError, PlaceRebuildResult] = call(Api.rebuildPlaces)(())
  def fetchExternalPlaces(source: String, dryRun: Boolean): Either[CliError, PlaceImportResult] =
    call(Api.fetchExternalPlaces)((source, dryRun))
  def placeSources: Either[CliError, List[GazetteerSourceInfo]] = call(Api.placeSources)(())
  def loadPlaces(source: String, apply: Boolean): Either[CliError, JobInfo] = call(Api.loadPlaces)((source, apply))
  def buildHierarchy: Either[CliError, SpatialHierarchyResult] = call(Api.buildHierarchy)(())
  // ── named periods (#719) and first-run data (#720) ──────────────────────────────────────────
  def periods(
      q: Option[String],
      region: Option[String],
      from: Option[Long],
      to: Option[Long]
  ): Either[CliError, List[HistoricalPeriod]] = call(Api.periods)((q, region, from, to))
  def period(id: String): Either[CliError, PeriodDetail] = call(Api.period)(id)
  def seedPeriods(replace: Boolean = false): Either[CliError, PeriodImportResult] = call(Api.seedPeriods)(replace)
  def importPeriods(
      collections: List[String],
      body: String,
      dataset: Dataset = Dataset.Local
  ): Either[CliError, PeriodImportResult] =
    call(Api.importPeriods)((collections, Some(dataset.wire), body))
  def fetchPeriods(source: String, collections: List[String]): Either[CliError, PeriodImportResult] =
    call(Api.fetchPeriods)((source, collections))
  def init(fetch: Boolean): Either[CliError, JobInfo] = call(Api.init)(fetch)
  // ── events (plan §1.2) ──────────────────────────────────────────────────────────────────────
  def listEvents(q: Api.EventQuery): Either[CliError, EventList] = call(Api.events)(q)
  def getEvent(id: String): Either[CliError, EventDetail] = call(Api.getEvent)(id)
  def putEvent(id: String, in: EventPut): Either[CliError, EventDetail] = call(Api.putEvent)((id, in))
  def patchEvent(id: String, p: EventPatch): Either[CliError, EventDetail] = call(Api.patchEvent)((id, p))
  def deleteEvent(id: String): Either[CliError, EventDeleted] = call(Api.deleteEvent)(id)
  // ── goals (plan §1.5) ──────────────────────────────────────────────────────────────────────
  def listGoals(q: Api.GoalQuery): Either[CliError, GoalList] = call(Api.goals)(q)
  def getGoal(slug: String): Either[CliError, Goal] = call(Api.getGoal)(slug)
  def putGoal(slug: String, in: GoalPut): Either[CliError, Goal] = call(Api.putGoal)((slug, in))
  def patchGoal(slug: String, p: GoalPatch): Either[CliError, Goal] = call(Api.patchGoal)((slug, p))
  def deleteGoal(slug: String): Either[CliError, GoalDeleted] = call(Api.deleteGoal)(slug)
  // ── agent state (plan §1.5) ───────────────────────────────────────────────────────────────
  def agentStateList(): Either[CliError, List[AgentStateEntry]] = call(Api.agentStateList)(())
  def agentStateGet(key: String): Either[CliError, AgentStateEntry] = call(Api.agentStateGet)(key)
  def agentStateSet(key: String, in: AgentStatePut): Either[CliError, AgentStateEntry] =
    call(Api.agentStateSet)((key, in))
  def agentStateDelete(key: String): Either[CliError, AgentStateDeleted] = call(Api.agentStateDelete)(key)
  // ── attestations (plan §1.3) ───────────────────────────────────────────────────────────────
  def listAttestations(q: Api.AttestationQuery): Either[CliError, AttestationList] = call(Api.attestations)(q)
  def getAttestation(id: Long): Either[CliError, Attestation] = call(Api.getAttestation)(id)
  def createAttestation(in: AttestationPut): Either[CliError, Attestation] = call(Api.createAttestation)(in)
  def patchAttestation(id: Long, p: AttestationPatch): Either[CliError, Attestation] =
    call(Api.patchAttestation)((id, p))
  def deleteAttestation(id: Long): Either[CliError, AttestationDeleted] = call(Api.deleteAttestation)(id)

  def exportSource(ck: String): Either[CliError, String] = call(Api.exportSource)(ck)
  def citekeys: Either[CliError, List[String]] = call(Api.citekeys)(())
  def resolveEdges: Either[CliError, EdgesResolved] = call(Api.resolveEdges)(())

  // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────
  def listArticles(q: Api.ArticleQuery): Either[CliError, ArticleList] = call(Api.articles)(q)
  def getArticle(slug: String): Either[CliError, ArticleView] = call(Api.getArticle)((slug, false))
  def putArticle(slug: String, in: ArticlePut, strict: Boolean = false): Either[CliError, ArticleView] =
    call(Api.putArticle)((slug, in, strict))
  def patchArticle(slug: String, p: ArticlePatch, strict: Boolean = false): Either[CliError, ArticleView] =
    call(Api.patchArticle)((slug, p, strict))
  def deleteArticle(slug: String): Either[CliError, ArticleDeleted] = call(Api.deleteArticle)(slug)
  def articleCoverage(slug: String): Either[CliError, Coverage] = call(Api.articleCoverage)(slug)
  def articleReview(slug: String): Either[CliError, ArticleReviewView] = call(Api.articleReview)(slug)
  def exportArticle(slug: String): Either[CliError, String] = call(Api.exportArticle)(slug)
  def importArticle(req: ArticleImport): Either[CliError, SeedResult] = call(Api.importArticle)(req)
  def articleSlugs: Either[CliError, List[String]] = call(Api.articleSlugs)(())
  def articleVersions(slug: String): Either[CliError, List[ArticleVersion]] =
    call(Api.articleVersions)(slug)
  def articleVersion(slug: String, versionId: Long): Either[CliError, ArticleVersionFull] =
    call(Api.articleVersion)((slug, versionId))
  // ── lint · queue · read-only query (plan §5.2 stream 2.8) ───────────────────────────────────
  def lint(rules: List[String]): Either[CliError, Lint.LintReport] = call(OpsApi.lint)(rules)
  def lintFix(rules: List[String]): Either[CliError, Lint.LintFixResult] = call(OpsApi.lintFix)(rules)
  def queueList(list: String, all: Boolean): Either[CliError, QueueListing] =
    call(OpsApi.queueList)((list, all, None, None))
  def queueAdd(list: String, in: Queue.QueueAdd): Either[CliError, Queue.QueueItem] =
    call(OpsApi.queueAdd)((list, in))
  def queuePatch(id: Int, p: Queue.QueuePatch): Either[CliError, Queue.QueueItem] =
    call(OpsApi.queuePatch)((id, p))
  def queueDelete(id: Int): Either[CliError, Queue.QueueItem] = call(OpsApi.queueDelete)(id)
  def queueExport(list: String): Either[CliError, String] = call(OpsApi.queueExport)(list)
  def goalsExport: Either[CliError, String] = call(OpsApi.goalsExport)(())
  def queueImport(list: String, req: Queue.QueueImport): Either[CliError, Queue.QueueImported] =
    call(OpsApi.queueImport)((list, req))
  def query(sql: String): Either[CliError, Query.QueryResult] = call(OpsApi.query)(Query.QueryRequest(sql))
  def schemaTables: Either[CliError, Query.SchemaTables] = call(OpsApi.schemaTables)(())
  // ── ingest, jobs and assets (plan §1.8) ─────────────────────────────────────────────────────
  /** Upload one document. The body is the FILE itself (tapir `fileBody`), so a 200 MB scan is streamed rather than held
    * in memory, and every other parameter travels as a query parameter.
    */
  def ingest(p: IngestParams, file: java.io.File): Either[CliError, IngestAccepted] =
    call(Api.ingest)((p, file))
  def reocr(ck: String, quality: Boolean): Either[CliError, IngestAccepted] = call(Api.reocr)((ck, quality))
  def job(id: String): Either[CliError, JobInfo] = call(Api.job)(id)
  def basemapStatus(): Either[CliError, BasemapStatus] = call(BasemapApi.status)(())
  def basemapFetch(q: BasemapFetchQuery): Either[CliError, JobInfo] = call(BasemapApi.fetch)(q)
  def basemapRemove(q: BasemapRemoveQuery): Either[CliError, BasemapRemoved] = call(BasemapApi.remove)(q)
  def jobs(
      f: ListFilters.JobFilter,
      limit: Option[Int],
      before: Option[String] = None
  ): Either[CliError, (List[JobInfo], Paging.Page)] =
    call(Api.jobs)((f, limit, before, None))
  def assets(ck: String): Either[CliError, List[AssetInfo]] = call(Api.assets)(ck)
  def putAssets(ck: String, in: List[AssetInput]): Either[CliError, AssetsStored] =
    call(Api.putAssets)((ck, in))
  def assetDetail(ck: String, name: String): Either[CliError, AssetDetail] = call(Api.assetDetail)((ck, name))
  def patchAsset(ck: String, name: String, p: AssetPatch): Either[CliError, AssetDetail] =
    call(Api.patchAsset)((ck, name, p))
  def assetLinks(
      ck: Option[String],
      asset: Option[String],
      tt: Option[String],
      ti: Option[String]
  ): Either[CliError, AssetLinkList] =
    call(Api.assetLinks)((ck, asset, tt, ti))
  def createAssetLink(in: AssetLinkPut): Either[CliError, AssetLink] = call(Api.createAssetLink)(in)
  def deleteAssetLink(id: Long): Either[CliError, AssetLinkDeleted] = call(Api.deleteAssetLink)(id)
  // ── media from outside providers (#1446) ──
  def mediaImport(req: MediaImportRequest): Either[CliError, MediaImportReport] = call(Api.mediaImport)(req)
  def mediaImportJob(req: MediaImportRequest): Either[CliError, JobInfo] = call(Api.mediaImportJob)(req)
  def mediaCovers(req: MediaCoversRequest): Either[CliError, MediaCoversReport] = call(Api.mediaCovers)(req)
  def mediaCoversJob(req: MediaCoversRequest): Either[CliError, JobInfo] = call(Api.mediaCoversJob)(req)
  def putSourceCover(p: SourceCoverParams, bytes: Array[Byte]): Either[CliError, MediaItem] =
    call(Api.putSourceCover)((p, bytes))
  def mediaList(q: Api.MediaQuery): Either[CliError, MediaList] = call(Api.mediaList)(q)
  def mediaShow(uid: String): Either[CliError, MediaItem] = call(Api.mediaShow)(uid)
  def mediaFacets: Either[CliError, MediaFacets] = call(Api.mediaFacets)(())
  def mediaProviders: Either[CliError, List[MediaProviderInfo]] = call(Api.mediaProviders)(())
  def fillFigureLicences(apply: Boolean): Either[CliError, FigureLicenceFill] = call(Api.fillFigureLicences)(apply)

object Client:
  /** POST/PUT/PATCH/DELETE change the corpus; anything else (a GET download) only reads it. */
  def isWrite(m: sttp.model.Method): Boolean =
    m == sttp.model.Method.POST || m == sttp.model.Method.PUT || m == sttp.model.Method.PATCH ||
      m == sttp.model.Method.DELETE

  /** What to say when the server answered something the endpoint does not describe. Pure. A refusal names what was
    * refused (#819: a refused backup download used to read "refused this write").
    */
  def unexpected(status: Int, serverUrl: String, write: Boolean): String = status match
    case 401 | 403 =>
      val what = if write then "this write" else "this request"
      s"$serverUrl refused $what: it needs its API key — pass --token <key> or set HISTMERSION_TOKEN"
    case 404 => s"$serverUrl has no such endpoint (HTTP 404) — is the CLI newer than the server?"
    case c   => s"unexpected answer from $serverUrl: HTTP $c"
