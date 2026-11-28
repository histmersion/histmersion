package com.histmersion
package cli
import com.histmersion.util.problem.{External, Legacy, Problem}
import com.histmersion.api.log.LogEvent
import com.histmersion.api.automation.Creation
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.api.json.NewtypeCodecs.given

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
import com.histmersion.api.research.ReviewGoal
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.CollectionsApi
import com.histmersion.api.library.Edge
import com.histmersion.api.catalogue.Event
import com.histmersion.api.instance.CorpusCounts
import com.histmersion.api.media.AssetLinks
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api.HttpMethod.equality
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** Typed HTTP client over the `Api` contract: each call is derived from the same Tapir endpoint the server interprets,
  * so request/response shapes cannot drift. Sync (java.net.http), native-safe. `token` is the
  * `Authorization: Bearer <key>` value for authenticated servers; `None` = local.
  */
@atBoundary(BoundaryKind.Http, "sends the CLI's HTTP requests to the server and decodes the answers")
final class Client(
    val serverUrl: Client.ServerUrl,
    token: Option[Client.Token] = None,
    caller: AgentIdentity = AgentIdentity.Nobody
):
  import Client.{transport, unexpected, ConnectTimeout, Histmersion, HttpStatus, PollTimeout, Write}

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private val base = Uri.unsafeParse(serverUrl.value)

  def createdBy: Option[Creation.By] = caller.createdBy

  // HTTP/1.1, explicitly. sttp's sync backend is java.net.http, whose default HTTP/2 client reuses a
  // connection in a way the server's netty backend mishandles for body-carrying requests: the SECOND
  // upload on a reused connection arrives with an EMPTY body and is rejected. `histmersion ingest` sends
  // exactly such requests. (Same reason the server pins 1.1 when calling the OCR sidecar.)
  private val backend = HttpClientSyncBackend.usingClient(
    java.net.http.HttpClient
      .newBuilder()
      .version(java.net.http.HttpClient.Version.HTTP_1_1)
      .connectTimeout(ConnectTimeout)
      .build()
  )
  private val interp = SttpClientInterpreter()

  // `private[cli]` so the acquisition client (stream 2.6) reuses this one transport rather than
  // opening a second backend with its own error handling.
  private[cli] def call[I, O](ep: PublicEndpoint[I, Api.Error, O, Any])(in: I): Either[Cli.Error, O] =
    // No client-side read timeout: the server bounds every request itself
    // (`server.request-timeout-seconds`), and sttp's 1-minute default is shorter than a backup.
    send(
      authed(
        Client
          .stamped(in, caller)(interp.toRequest(ep, Some(base)).apply(in))
          .readTimeout(scala.concurrent.duration.Duration.Inf)
      )
    )

  private def poll[I, O](ep: PublicEndpoint[I, Api.Error, O, Any])(in: I): Either[Cli.Error, O] =
    send(authed(Client.stamped(in, caller)(interp.toRequest(ep, Some(base)).apply(in)).readTimeout(PollTimeout)))

  /** The bearer token on a request. EVERY request goes through here — the typed calls and the two file downloads alike
    * (#767: `downloadBackup` was built by hand and sent none).
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def authed[T](req: Request[T]): Request[T] = token.fold(req)(t => req.auth.bearer(t.value))

  /** One place turns a response into a result. Decoding does NOT throw: an answer the endpoint does not describe (the
    * auth gate's 401 is not in any endpoint's error output) used to surface as an exception and was then reported as
    * "cannot reach … is the server running?". Only a failure to TALK to the server is that; everything else says what
    * the server answered.
    */
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def send[O](req: Request[sttp.tapir.DecodeResult[Either[Api.Error, O]]]): Either[Cli.Error, O] =
    val write = Write.of(req.method)
    scala.util.Try(req.send(backend)) match
      case scala.util.Failure(e) if WorkingDir.denied(e) =>
        Left(Cli.Error(Problem.Unclassified(Legacy.Text(s"${WorkingDir.message}")), workingDirDenied = true))
      case scala.util.Failure(e) =>
        Left(Cli.Error(transport(e, serverUrl, Problem.Upstream.Url(s"${req.uri}"), write)))
      case scala.util.Success(resp) =>
        import Client.{TraceHeader, traceOf}
        val trace = traceOf(resp.header(TraceHeader))
        resp.body match
          case sttp.tapir.DecodeResult.Value(Right(o)) => Right(o)
          case sttp.tapir.DecodeResult.Value(Left(e))  =>
            Left(Cli.Error(e.problems, ExitCode.Origin.Server, notFound = e.isNotFound, trace = trace))
          case _ =>
            Left(
              Cli.Error(unexpected(HttpStatus(resp.code.code), serverUrl, write), ExitCode.Origin.Server, trace = trace)
            )

  def unanswered(what: String): Problem =
    Problem.Upstream.Unanswered(Histmersion, Problem.Upstream.Url.of(serverUrl), External.Reported.of(what))

  def version: Either[Cli.Error, Version.Info] = call(Api.version)(())
  def health: Either[Cli.Error, Health] = call(Api.health)(())
  def about: Either[Cli.Error, About] = call(Api.about)(())
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def search(q: Option[Api.Search.Text], limit: Option[Paging.Limit]): Either[Cli.Error, List[SearchHit]] =
    call(Api.search)((q, limit.map(l => Api.Search.Limit(l.value))))
  def sources(f: Filter): Either[Cli.Error, SearchResult] = call(Api.sources)(f)
  def facets(field: Api.Facets.Field, prefix: Option[Api.Facets.Prefix], f: Filter): Either[Cli.Error, List[Facet]] =
    call(Api.facets)((field, prefix, f))
  def getSource(ck: Citekey): Either[Cli.Error, Source.WithFindings] =
    call(Api.getSource)((ck, Api.SourceViewQuery.FullText.FullText))
  def findings(q: Api.FindingsQuery): Either[Cli.Error, List[GeoHit]] = call(Api.findings)(q)
  def findingsPage(q: Api.FindingsQuery): Either[Cli.Error, GeoHitPage] = call(Api.findingsPage)(q)
  def aggregate(q: Api.AggregateQuery): Either[Cli.Error, TimeHistogram] = call(Api.aggregate)(q)
  def neighbors(ck: Citekey, kinds: List[Edge.Kind.Wire] = Nil): Either[Cli.Error, List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  def graph(ck: Citekey, depth: Option[Source.Graph.DepthCount]): Either[Cli.Error, Source.Graph] =
    call(Api.graph)((ck, depth))
  def contradictions(
      ck: Option[Citekey],
      since: Option[Api.AggregateQuery.AsOf]
  ): Either[Cli.Error, ContradictionsResult] =
    call(Api.contradictions)((ck, since))
  def seed(req: SeedRequest): Either[Cli.Error, SeedResult] = call(Api.seed)(req)
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def reindex(after: Option[Api.Reindex.Scope], limit: Option[Paging.Limit]): Either[Cli.Error, ReindexResult] =
    call(Api.reindex)((after, limit.map(l => Api.Reindex.Limit(l.value))))

  // ── backup / restore (plan §1.10): the DUMP is taken server-side; the host only copies it off ──
  def configReload: Either[Cli.Error, ReloadReport] = call(Api.configReload)(())
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def settings(prefix: Option[Settings.Prefix]): Either[Cli.Error, ConfigEdit.Page] =
    call(ConfigEditApi.get)(prefix.map(p => ConfigEdit.Page.Selector(p.value)))
  def writeSettings(w: ConfigEdit.Write): Either[Cli.Error, ConfigEdit.Written] = call(ConfigEditApi.write)(w)
  def restartServer: Either[Cli.Error, ConfigEdit.Restart] = call(ConfigEditApi.restart)(())
  def backup: Either[Cli.Error, Backup.Status] = call(Api.backup)(())
  def backupStatus: Either[Cli.Error, Backup.Status] = poll(Api.backupStatus)(())
  def vacuum(in: Vacuum.Request): Either[Cli.Error, JobInfo] = call(Api.maintenanceVacuum)(in)
  def backups: Either[Cli.Error, List[Backup.Info]] = call(Api.backups)(())
  def restore(req: RestoreRequest): Either[Cli.Error, RestoreResult] = call(Api.restore)(req)

  /** Stream one archive into `dest`. Tapir's `fileBody` is interpreted by sttp as `asFileAlways` against the file
    * `SttpClientOptions.createFile` hands it, so the response is written straight to disk — a multi-GB dump never
    * becomes a byte array.
    */
  def downloadBackup(file: Backup.Name, dest: java.io.File): Either[Cli.Error, java.io.File] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    send(authed(toFile.toRequest(Api.downloadBackup, Some(base)).apply(file)))

  /** Stream a source's stored original into `dest`; returns it with its `Content-Disposition`, which names the format
    * ([[Ingest.dispositionExtension]]).
    */
  def original(ck: Citekey, dest: java.io.File): Either[Cli.Error, (java.io.File, Api.FileName)] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    send(authed(toFile.toRequest(Api.original, Some(base)).apply(ck)))

  def sourceView(q: Api.SourceViewQuery): Either[Cli.Error, Source.View] = call(Api.sourceView)(q)
  def passages(q: Api.PassagesQuery): Either[Cli.Error, PassageResult] = call(Api.passages)(q)

  // ── portable bundles (docs/research-merge-import.md) ────────────────────────────────────────
  def instance: Either[Cli.Error, InstanceInfo] = call(Api.instance)(())
  def setInstance(label: Option[InstanceLabel.Label]): Either[Cli.Error, InstanceInfo] =
    call(Api.setInstance)(InstanceLabel(label))

  /** Stream a bundle into `dest` (as [[downloadBackup]]: written to disk, never a byte array). */
  def exportBundle(since: Option[Bundle.Name], dest: java.io.File): Either[Cli.Error, (java.io.File, Api.FileName)] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    send(authed(toFile.toRequest(Api.exportBundle, Some(base)).apply(since)))

  /** The bundle file is the request body, streamed from disk. */
  def verifyBundle(file: java.io.File): Either[Cli.Error, Bundle.VerifyResult] = call(Api.verifyBundle)(file)
  def importBundle(writes: Cli.Writes, file: java.io.File): Either[Cli.Error, Bundle.ImportResult] =
    call(Api.importBundle)(((if writes.dry then Api.Apply.DryRun else Api.Apply.Apply), file))
  // the merge plan (M3): the bundle is uploaded once, then planned and imported by its sha256
  def uploadBundle(file: java.io.File): Either[Cli.Error, Bundle.Upload] = call(Api.uploadBundle)(file)
  def mergePlan(req: MergePlan.Request): Either[Cli.Error, MergePlan] = call(Api.mergePlan)(req)
  def mergeImport(req: MergeImportRequest): Either[Cli.Error, Bundle.ImportResult] = call(Api.mergeImport)(req)
  def mergeApply(req: MergeApplyRequest): Either[Cli.Error, MergeApplyJob] = call(Api.mergeApply)(req)
  def mergeApplyStatus(job: Api.Merge.Id): Either[Cli.Error, MergeApplyJob] = call(Api.mergeApplyStatus)(job)
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def merges(limit: Option[Paging.Limit]): Either[Cli.Error, List[MergeInfo]] =
    call(Api.merges)(limit.map(l => Api.Merge.Limit(l.value)))
  def mergeDetail(id: Api.Merge.Id): Either[Cli.Error, MergeDetail] = call(Api.mergeDetail)(id)
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def mergeLedger(
      uid: Option[Api.Merge.Id],
      merge: Option[Api.Merge.Entity],
      limit: Option[Paging.Limit]
  ): Either[Cli.Error, MergeLedger] =
    call(Api.mergeLedger)((uid, merge, limit.map(l => Api.Merge.Limit(l.value))))
  // label renames (V042, V053)
  def renameArticle(slug: ReviewGoal.Slug, to: LabelRename.To): Either[Cli.Error, LabelRenamed] =
    call(Api.renameArticle)((slug, LabelRename(to)))
  def renameEvent(id: Event.Uid, to: LabelRename.To): Either[Cli.Error, LabelRenamed] =
    call(Api.renameEvent)((id, LabelRename(to)))
  def renameGoal(slug: Goal.Uid, to: LabelRename.To): Either[Cli.Error, LabelRenamed] =
    call(Api.renameGoal)((slug, LabelRename(to)))
  def renamePlace(id: Place.Id, to: LabelRename.To): Either[Cli.Error, LabelRenamed] =
    call(Api.renamePlace)((id, LabelRename(to)))

  // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────
  def putSource(
      ck: Citekey,
      in: Source.Put,
      strict: Cli.Strict = Cli.Strict.Leniently
  ): Either[Cli.Error, Source.WithFindings] =
    call(Api.putSource)((ck, in, (if strict.strictly then Api.Strictness.Strict else Api.Strictness.Lenient)))
  def patchSource(
      ck: Citekey,
      p: Source.Patch,
      strict: Cli.Strict = Cli.Strict.Leniently
  ): Either[Cli.Error, Source.WithFindings] =
    call(Api.patchSource)(
      (ck, Source.Patch.Request.of(p), (if strict.strictly then Api.Strictness.Strict else Api.Strictness.Lenient))
    )
  // the formatting lint of agent-written Markdown
  def lintSource(ck: Citekey): Either[Cli.Error, FormatReport] = call(Api.lintSource)(ck)
  def fixSourceFormat(ck: Citekey): Either[Cli.Error, FormatReport] = call(Api.fixSourceFormat)(ck)
  def lintArticle(slug: ReviewGoal.Slug): Either[Cli.Error, FormatReport] = call(Api.lintArticle)(slug)
  def fixArticleFormat(slug: ReviewGoal.Slug): Either[Cli.Error, FormatReport] = call(Api.fixArticleFormat)(slug)
  def checkArticle(
      slug: ReviewGoal.Slug,
      in: Article.Edits.ArticleCheckRequest
  ): Either[Cli.Error, Article.Edits.ArticleCheck] =
    call(Api.checkArticle)((slug, in))
  def lintMarkdown(in: FormatCheck): Either[Cli.Error, FormatReport] = call(OpsApi.lintMarkdown)(in)
  def importNote(req: NoteImport): Either[Cli.Error, SeedResult] = call(Api.importNote)(req)
  def renameSource(ck: Citekey, newCk: Citekey): Either[Cli.Error, Source.Renamed] =
    call(Api.renameSource)((ck, Rename.Request(newCk)))
  def sourceAliases: Either[Cli.Error, List[Source.Alias]] = call(Api.sourceAliases)(())
  def deleteSource(ck: Citekey, apply: Api.Apply): Either[Cli.Error, Source.Deletion] =
    call(Api.deleteSource)((ck, Source.DeleteRequest(apply)))

  // ── the gazetteer (plan §1.6a) ──────────────────────────────────────────────────────────────
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def places(
      q: Option[Api.Search.Text],
      asOf: Option[Api.AggregateQuery.AsOf],
      limit: Option[Paging.Limit]
  ): Either[Cli.Error, List[Place.Hit]] =
    call(Api.places)((q.map(t => Place.Kind.Wire(t.value)), asOf, limit.map(l => Api.PlacesGeoQuery.Limit(l.value))))
  def place(id: Place.Id, asOf: Option[Api.AggregateQuery.AsOf]): Either[Cli.Error, PlaceDetail] =
    call(Api.place)((id, asOf, None))
  def placeChildren(id: Place.Id, asOf: Option[Api.AggregateQuery.AsOf]): Either[Cli.Error, List[Place.Summary]] =
    call(Api.placeChildren)((id, asOf))
  def placeTree(
      id: Place.Id,
      asOf: Option[Api.AggregateQuery.AsOf],
      depth: Option[Place.Depth]
  ): Either[Cli.Error, List[Place.TreeRow]] =
    call(Api.placeTree)((id, asOf, depth))
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def importPlaces(
      kind: Option[Api.ExternalPlaces.Source],
      body: Api.ExternalPlaces.Format,
      dataset: Dataset = Dataset.Local
  ): Either[Cli.Error, Place.ImportResult] =
    call(Api.importPlaces)((kind, Some(Api.ExternalPlaces.Locator(dataset.wire.value)), body))
  def placeNames(place: Option[Place.Id]): Either[Cli.Error, List[ManualPlaceName]] = call(Api.placeNames)(place)
  def addPlaceName(id: Place.Id, in: Place.NameAdd): Either[Cli.Error, Place.NameChanged] =
    call(Api.addPlaceName)((id, in))
  def removePlaceName(id: Place.Id, in: Place.NameRemove): Either[Cli.Error, Place.NameChanged] =
    call(Api.removePlaceName)((id, in))
  def addPlace(in: Place.Add): Either[Cli.Error, Place.Summary] = call(Api.addPlace)(in)
  def placeOverrides(place: Option[Place.Id]): Either[Cli.Error, List[Place.Override]] = call(Api.placeOverrides)(place)
  def setPlaceOverride(id: Place.Id, in: Place.OverrideSet): Either[Cli.Error, Place.OverrideChanged] =
    call(Api.setPlaceOverride)((id, in))
  def mergePlace(id: Place.Id, in: Place.MergeIn): Either[Cli.Error, Place.Gone] = call(Api.mergePlace)((id, in))
  def removePlace(id: Place.Id, in: Place.RemoveIn): Either[Cli.Error, Place.Gone] = call(Api.removePlace)((id, in))
  def rebuildPlaces: Either[Cli.Error, Place.RebuildResult] = call(Api.rebuildPlaces)(())
  def fetchExternalPlaces(
      source: Api.ExternalPlaces.Source,
      writes: Cli.Writes
  ): Either[Cli.Error, Place.ImportResult] =
    call(Api.fetchExternalPlaces)((source, (if writes.dry then Api.Apply.DryRun else Api.Apply.Apply)))
  def placeSources: Either[Cli.Error, List[Gazetteer.SourceInfo]] = call(Api.placeSources)(())
  def loadPlaces(source: Api.ExternalPlaces.Source, apply: Api.Apply): Either[Cli.Error, JobInfo] =
    call(Api.loadPlaces)((source, apply))
  def buildHierarchy: Either[Cli.Error, SpatialHierarchyResult] = call(Api.buildHierarchy)(())
  // ── named periods (#719) and first-run data (#720) ──────────────────────────────────────────
  def periods(
      q: Option[CorpusCounts.Periods.Source],
      region: Option[CorpusCounts.Periods.Collection],
      from: Option[Api.PlacesGeoQuery.From],
      to: Option[Api.PlacesGeoQuery.To]
  ): Either[Cli.Error, List[HistoricalPeriod]] = call(Api.periods)((q, region, from, to))
  def period(id: Period.Id): Either[Cli.Error, Period.Detail] = call(Api.period)(id)
  def seedPeriods(replace: Api.Overwrite = Api.Overwrite.Keep): Either[Cli.Error, Period.ImportResult] =
    call(Api.seedPeriods)(replace)
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def importPeriods(
      collections: List[CorpusCounts.Periods.Source],
      body: CorpusCounts.Periods.Name,
      dataset: Dataset = Dataset.Local
  ): Either[Cli.Error, Period.ImportResult] =
    call(Api.importPeriods)((collections, Some(CorpusCounts.Periods.Collection(dataset.wire.value)), body))
  def fetchPeriods(
      source: CorpusCounts.Periods.Source,
      collections: List[CorpusCounts.Periods.Collection]
  ): Either[Cli.Error, Period.ImportResult] =
    call(Api.fetchPeriods)((source, collections))
  def init(fetch: Api.Fetching): Either[Cli.Error, JobInfo] = call(Api.init)(fetch)
  // ── events (plan §1.2) ──────────────────────────────────────────────────────────────────────
  def listEvents(q: Api.EventQuery): Either[Cli.Error, EventList] = call(Api.events)(q)
  def getEvent(id: Event.Uid): Either[Cli.Error, EventDetail] = call(Api.getEvent)(id)
  def putEvent(id: Event.Uid, in: EventPut): Either[Cli.Error, EventDetail] = call(Api.putEvent)((id, in))
  def patchEvent(id: Event.Uid, p: EventPatch): Either[Cli.Error, EventDetail] = call(Api.patchEvent)((id, p))
  def deleteEvent(id: Event.Uid): Either[Cli.Error, EventDeleted] = call(Api.deleteEvent)(id)
  // ── goals (plan §1.5) ──────────────────────────────────────────────────────────────────────
  def listGoals(q: Api.GoalQuery): Either[Cli.Error, Goal.List] = call(Api.goals)(q)
  def getGoal(slug: Goal.Uid): Either[Cli.Error, Goal] = call(Api.getGoal)(slug)
  def putGoal(slug: Goal.Uid, in: Goal.Put): Either[Cli.Error, Goal] = call(Api.putGoal)((slug, in))
  def patchGoal(slug: Goal.Uid, p: Goal.Patch): Either[Cli.Error, Goal] = call(Api.patchGoal)((slug, p))
  def deleteGoal(slug: Goal.Uid): Either[Cli.Error, Goal.Deleted] = call(Api.deleteGoal)(slug)
  // ── agent state (plan §1.5) ───────────────────────────────────────────────────────────────
  def agentStateList(): Either[Cli.Error, List[AgentStateEntry]] = call(Api.agentStateList)(())
  def agentStateGet(key: Api.AgentState.Key): Either[Cli.Error, AgentStateEntry] = call(Api.agentStateGet)(key)
  def agentStateSet(key: Api.AgentState.Key, in: AgentStatePut): Either[Cli.Error, AgentStateEntry] =
    call(Api.agentStateSet)((key, in))
  def agentStateDelete(key: Api.AgentState.Key): Either[Cli.Error, AgentStateDeleted] = call(Api.agentStateDelete)(key)
  // ── attestations (plan §1.3) ───────────────────────────────────────────────────────────────
  def listAttestations(q: Api.AttestationQuery): Either[Cli.Error, Attestation.List] = call(Api.attestations)(q)
  def getAttestation(id: Attestation.Id): Either[Cli.Error, Attestation] = call(Api.getAttestation)(id)
  def createAttestation(in: Attestation.Put): Either[Cli.Error, Attestation] = call(Api.createAttestation)(in)
  def patchAttestation(id: Attestation.Id, p: Attestation.Patch): Either[Cli.Error, Attestation] =
    call(Api.patchAttestation)((id, p))
  def deleteAttestation(id: Attestation.Id): Either[Cli.Error, Attestation.Deleted] = call(Api.deleteAttestation)(id)

  def exportSource(ck: Citekey): Either[Cli.Error, Source.Export] = call(Api.exportSource)(ck)
  def citekeys: Either[Cli.Error, List[Citekey]] = call(Api.citekeys)(())
  def resolveEdges: Either[Cli.Error, EdgesResolved] = call(Api.resolveEdges)(())

  // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────
  def listArticles(q: Api.ArticleQuery): Either[Cli.Error, Article.List] = call(Api.articles)(q)
  def getArticle(slug: ReviewGoal.Slug): Either[Cli.Error, Article.View] =
    call(Api.getArticle)((slug, Article.FullText.SummaryOnly))
  def putArticle(
      slug: ReviewGoal.Slug,
      in: Article.Put,
      strict: Cli.Strict = Cli.Strict.Leniently
  ): Either[Cli.Error, Article.View] =
    call(Api.putArticle)((slug, in, (if strict.strictly then Api.Strictness.Strict else Api.Strictness.Lenient)))
  def patchArticle(
      slug: ReviewGoal.Slug,
      p: Article.Patch,
      strict: Cli.Strict = Cli.Strict.Leniently
  ): Either[Cli.Error, Article.View] =
    call(Api.patchArticle)((slug, p, (if strict.strictly then Api.Strictness.Strict else Api.Strictness.Lenient)))
  def deleteArticle(slug: ReviewGoal.Slug): Either[Cli.Error, Article.Deleted] = call(Api.deleteArticle)(slug)
  def articleCoverage(slug: ReviewGoal.Slug): Either[Cli.Error, Coverage] = call(Api.articleCoverage)(slug)
  def articleReview(slug: ReviewGoal.Slug): Either[Cli.Error, Article.ReviewView] = call(Api.articleReview)(slug)
  def exportArticle(slug: ReviewGoal.Slug): Either[Cli.Error, Article.Export] = call(Api.exportArticle)(slug)
  def importArticle(req: Article.Import): Either[Cli.Error, SeedResult] = call(Api.importArticle)(req)
  def articleSlugs: Either[Cli.Error, List[ReviewGoal.Slug]] = call(Api.articleSlugs)(())
  def articleVersions(slug: ReviewGoal.Slug): Either[Cli.Error, List[Article.Version]] =
    call(Api.articleVersions)(slug)
  def articleVersion(slug: ReviewGoal.Slug, versionId: Article.VersionId): Either[Cli.Error, Article.VersionFull] =
    call(Api.articleVersion)((slug, versionId))
  // ── lint · queue · read-only query (plan §5.2 stream 2.8) ───────────────────────────────────
  def lint(rules: List[Lint.Wire]): Either[Cli.Error, Lint.LintReport] = call(OpsApi.lint)(rules)
  def lintFix(rules: List[Lint.Wire]): Either[Cli.Error, Lint.LintFixResult] = call(OpsApi.lintFix)(rules)
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def queueList(list: Queue.Wire, all: Boolean): Either[Cli.Error, QueueListing] =
    call(OpsApi.queueList)((list, all, None, None))
  def queueAdd(list: Queue.Wire, in: Queue.QueueAdd): Either[Cli.Error, Queue.QueueItem] =
    call(OpsApi.queueAdd)((list, in))
  def queuePatch(id: Queue.Id, p: Queue.QueuePatch): Either[Cli.Error, Queue.QueueItem] =
    call(OpsApi.queuePatch)((id, p))
  def queueDelete(id: Queue.Id): Either[Cli.Error, Queue.QueueItem] = call(OpsApi.queueDelete)(id)
  def queueExport(list: Queue.Wire): Either[Cli.Error, Queue.Export] = call(OpsApi.queueExport)(list)
  def goalsExport: Either[Cli.Error, Goal.Export] = call(OpsApi.goalsExport)(())
  def queueImport(list: Queue.Wire, req: Queue.QueueImport): Either[Cli.Error, Queue.QueueImported] =
    call(OpsApi.queueImport)((list, req))
  def query(sql: Query.Statement): Either[Cli.Error, Query.QueryResult] = call(OpsApi.query)(Query.QueryRequest(sql))
  def schemaTables: Either[Cli.Error, Query.SchemaTables] = call(OpsApi.schemaTables)(())
  // ── ingest, jobs and assets (plan §1.8) ─────────────────────────────────────────────────────
  /** Upload one document. The body is the FILE itself (tapir `fileBody`), so a 200 MB scan is streamed rather than held
    * in memory, and every other parameter travels as a query parameter.
    */
  def ingest(p: Ingest.Params, file: java.io.File): Either[Cli.Error, Ingest.Accepted] =
    call(Api.ingest)((p, file))
  def reocr(ck: Citekey, quality: Ingest.Params.Quality): Either[Cli.Error, Ingest.Accepted] =
    call(Api.reocr)((ck, quality))
  def job(id: SpatialHierarchyResult.Job.Id): Either[Cli.Error, JobInfo] = poll(Api.job)(id)
  def basemapStatus(): Either[Cli.Error, Basemap.Status] = call(BasemapApi.status)(())
  def basemapFetch(q: Basemap.FetchQuery): Either[Cli.Error, JobInfo] = call(BasemapApi.fetch)(q)
  def basemapRemove(q: Basemap.RemoveQuery): Either[Cli.Error, Basemap.Removed] = call(BasemapApi.remove)(q)
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def jobs(
      f: ListFilters.JobFilter,
      limit: Option[Paging.Limit],
      before: Option[Api.Jobs.Status] = None
  ): Either[Cli.Error, (List[JobInfo], Paging.Page)] =
    call(Api.jobs)((f, limit.map(l => Api.Jobs.Limit(l.value)), before, None))
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def assets(ck: Citekey): Either[Cli.Error, List[AssetInfo]] = call(Api.assets)(Api.Assets.Kind(ck.value))
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def putAssets(ck: Citekey, in: List[AssetInput]): Either[Cli.Error, AssetsStored] =
    call(Api.putAssets)((Api.Assets.Owner(ck.value), in))
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def assetDetail(ck: Citekey, name: Bundle.Asset.Variant): Either[Cli.Error, AssetDetail] =
    call(Api.assetDetail)((Bundle.Asset.Uid(ck.value), name))
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def patchAsset(ck: Citekey, name: Bundle.Asset.Variant, p: AssetPatch): Either[Cli.Error, AssetDetail] =
    call(Api.patchAsset)((Bundle.Asset.Uid(ck.value), name, p))
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def assetLinks(
      ck: Option[Citekey],
      asset: Option[Bundle.Asset.Variant],
      tt: Option[Attestation.TargetType],
      ti: Option[Attestation.TargetId]
  ): Either[Cli.Error, AssetLink.List] =
    call(Api.assetLinks)(
      (
        ck.map(c => Bundle.Asset.Uid(c.value)),
        asset.map(a => AssetLinks.Kind(a.value)),
        tt.map(t => AssetLinks.TargetId(t.value)),
        ti.map(t => AssetLinks.TargetKind(t.value))
      )
    )
  def createAssetLink(in: AssetLink.Put): Either[Cli.Error, AssetLink] = call(Api.createAssetLink)(in)
  def deleteAssetLink(id: AssetLink.Id): Either[Cli.Error, AssetLink.Deleted] = call(Api.deleteAssetLink)(id)
  // ── media from outside providers (#1446) ──
  def mediaImport(req: MediaImportRequest): Either[Cli.Error, MediaImportReport] = call(Api.mediaImport)(req)
  def mediaImportJob(req: MediaImportRequest): Either[Cli.Error, JobInfo] = call(Api.mediaImportJob)(req)
  def mediaCovers(req: MediaCoversRequest): Either[Cli.Error, MediaCoversReport] = call(Api.mediaCovers)(req)
  def mediaCoversJob(req: MediaCoversRequest): Either[Cli.Error, JobInfo] = call(Api.mediaCoversJob)(req)
  def putSourceCover(p: SourceCoverParams, bytes: Api.FileBytes): Either[Cli.Error, MediaItem] =
    call(Api.putSourceCover)((p, bytes))
  def mediaList(q: Api.MediaQuery): Either[Cli.Error, MediaList] = call(Api.mediaList)(q)
  def mediaShow(uid: Bundle.Asset.Uid): Either[Cli.Error, MediaItem] = call(Api.mediaShow)(uid)
  def mediaFacets: Either[Cli.Error, MediaFacets] = call(Api.mediaFacets)(())
  def mediaProviders: Either[Cli.Error, List[MediaProvider.Info]] = call(Api.mediaProviders)(())
  def fillFigureLicences(apply: Api.Apply): Either[Cli.Error, FigureLicenceFill] = call(Api.fillFigureLicences)(apply)

object Client:
  val TraceHeader = "Histmersion-Trace"

  def requested(in: Any): Option[Creation.Requested] =
    val asRequested = scala.reflect.classTag[Creation.Requested]
    asRequested
      .unapply(in)
      .orElse(
        scala.reflect
          .classTag[Product]
          .unapply(in)
          .filter(_.productPrefix.startsWith("Tuple"))
          .flatMap(_.productIterator.flatMap(asRequested.unapply).nextOption())
      )

  private def rebodied[T](req: Request[T], text: String): Request[T] =
    val replaced = req.withHeaders(req.headers.filterNot(_.is(sttp.model.HeaderNames.ContentLength))).body(text)
    req.header(sttp.model.HeaderNames.ContentType).fold(replaced)(replaced.contentType)

  def stamped[I, T](in: I, caller: AgentIdentity)(req: Request[T]): Request[T] =
    requested(in).fold(req) { r =>
      val via = r.via.getOrElse(Creation.Cli)
      val by = r.createdBy.orElse(caller.createdBy)
      req.body match
        case StringBody(text, _, _) =>
          RawJson.parse(text).toOption.filter(_.asObject.isDefined).fold(req) { json =>
            val fields = ("via" -> via.asJson) :: by.map(b => "createdBy" -> b.asJson).toList
            rebodied(req, json.mapObject(o => fields.foldLeft(o) { case (acc, (k, v)) => acc.updated(k, v) }).noSpaces)
          }
        case _ => req
    }

  def traceOf(header: Option[String]): Option[LogEvent.Trace] =
    header.flatMap(LogEvent.Trace.fromHeader)

  type HttpStatus = HttpStatus.T
  object HttpStatus extends Newtype[Int, Pure]
  enum Write derives CanEqual:
    case Changes, Reads
  object Write:
    extension (write: Write) def changes: Boolean = write == Changes
    def of(method: sttp.model.Method): Write = if isWrite(method) then Changes else Reads

    extension (write: Write) def fold[A](changes: => A, reads: => A): A = if write.changes then changes else reads

  /** POST/PUT/PATCH/DELETE change the corpus; anything else (a GET download) only reads it. */

  type ServerUrl = ServerUrl.T
  object ServerUrl extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  /** The API key the CLI sends as `Authorization: Bearer <token>`. Never a command-line argument, where `ps` shows it:
    * `HISTMERSION_TOKEN`, else the first line of the file `cli.token-file` names.
    */
  type Token = Token.T
  object Token extends Newtype[String, Pure]:
    /** Pure: the variable's value when it is set and not blank, else what the file holds ([[firstLine]]), read only
      * then. A file that cannot be read is the problem `read` reports.
      */
    def chosen[P](variable: Option[String], file: => Either[P, Option[Token]]): Either[P, Option[Token]] =
      variable.map(_.trim).filter(_.nonEmpty) match
        case Some(v) => Right(Some(Token(v)))
        case None    => file

    /** Pure: the token a file holds, its first non-blank line trimmed; none for a file of blank lines. */
    def firstLine(text: String): Option[Token] = text.linesIterator.map(_.trim).find(_.nonEmpty).map(Token(_))

  val Histmersion: Problem.Upstream.Name = Problem.Upstream.Name("Histmersion")

  val ConnectTimeout: java.time.Duration = java.time.Duration.ofSeconds(10)

  val PollTimeout: scala.concurrent.duration.FiniteDuration = scala.concurrent.duration.Duration(30, "s")

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def transport(thrown: Throwable, serverUrl: ServerUrl, sent: Problem.Upstream.Url, write: Write): Problem =
    val reported = External.Reported.of(s"${thrown.getClass.getSimpleName}: ${thrown.getMessage}")
    def unreachable =
      Problem.Upstream.Unreachable.here(
        Histmersion,
        Problem.Upstream.Url.of(serverUrl),
        External.Reported.of(s"$reported — is the server running?")
      )
    thrown match
      case _: sttp.client4.SttpClientException.ConnectException => unreachable
      case _ => write.fold(Problem.Upstream.Unanswered(Histmersion, sent, reported), unreachable)

  def isWrite(m: sttp.model.Method): Boolean =
    m == sttp.model.Method.POST || m == sttp.model.Method.PUT || m == sttp.model.Method.PATCH ||
      m == sttp.model.Method.DELETE

  /** What to say when the server answered something the endpoint does not describe. Pure. A refusal names what was
    * refused (#819: a refused backup download used to read "refused this write").
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def unexpected(status: HttpStatus, serverUrl: ServerUrl, write: Write): Problem = status.value match
    case 401 =>
      Problem.Unclassified(
        Legacy.Text(
          shown"$serverUrl refused ${what(write)}: it needs its API key — set HISTMERSION_TOKEN, or name a file that " +
            "holds it in cli.token-file"
        )
      )
    case 403 => Problem.Unclassified(Legacy.Text(shown"$serverUrl refused ${what(write)} (HTTP 403)"))
    case 404 =>
      Problem.Unclassified(
        Legacy.Text(shown"$serverUrl has no such endpoint (HTTP 404) — is the CLI newer than the server?")
      )
    case c => Problem.Unclassified(Legacy.Text(s"unexpected answer from $serverUrl: HTTP $c"))

  private def what(write: Write): String = write.fold("this write", "this request")
