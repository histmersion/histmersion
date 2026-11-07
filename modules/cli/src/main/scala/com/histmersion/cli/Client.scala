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
  def search(q: Option[com.histmersion.api.Api.Search.Text], limit: Option[com.histmersion.api.Paging.Limit]): Either[Cli.Error, List[SearchHit]] =
    call(Api.search)((q, limit))
  def sources(f: Filter): Either[Cli.Error, SearchResult] = call(Api.sources)(f)
  def facets(field: com.histmersion.api.Api.Facets.Field, prefix: Option[com.histmersion.api.Api.Facets.Prefix], f: Filter): Either[Cli.Error, List[Facet]] =
    call(Api.facets)((field, prefix, f))
  def getSource(ck: com.histmersion.plugin.FetchedFile.Source.Citekey): Either[Cli.Error, Source.WithFindings] = call(Api.getSource)((ck, true))
  def findings(q: Api.FindingsQuery): Either[Cli.Error, List[GeoHit]] = call(Api.findings)(q)
  def findingsPage(q: Api.FindingsQuery): Either[Cli.Error, GeoHitPage] = call(Api.findingsPage)(q)
  def aggregate(q: Api.AggregateQuery): Either[Cli.Error, TimeHistogram] = call(Api.aggregate)(q)
  def neighbors(ck: com.histmersion.plugin.FetchedFile.Source.Citekey, kinds: List[com.histmersion.domain.library.Edge.KindText] = Nil): Either[Cli.Error, List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  def graph(ck: com.histmersion.plugin.FetchedFile.Source.Citekey, depth: Option[com.histmersion.web.WindowKey.Graph.Depth]): Either[Cli.Error, Source.Graph] =
    call(Api.graph)((ck, depth))
  def contradictions(ck: Option[com.histmersion.plugin.FetchedFile.Source.Citekey], since: Option[com.histmersion.cli.Contradictions.Since]): Either[Cli.Error, ContradictionsResult] =
    call(Api.contradictions)((ck, since))
  def seed(req: SeedRequest): Either[Cli.Error, SeedResult] = call(Api.seed)(req)
  def reindex(after: Option[com.histmersion.cli.Reindex.After], limit: Option[com.histmersion.api.Paging.Limit]): Either[Cli.Error, ReindexResult] =
    call(Api.reindex)((after, limit))

  // ── backup / restore (plan §1.10): the DUMP is taken server-side; the host only copies it off ──
  def configReload: Either[Cli.Error, ReloadReport] = call(Api.configReload)(())
  def settings(prefix: Option[com.histmersion.cli.Settings.Prefix]): Either[Cli.Error, ConfigEdit.Page] = call(ConfigEditApi.get)(prefix)
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
  def downloadBackup(file: com.histmersion.api.backup.Backup.Name, dest: java.io.File): Either[Cli.Error, java.io.File] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    send(authed(toFile.toRequest(Api.downloadBackup, Some(base)).apply(file)))

  /** Stream a source's stored original into `dest`; returns it with its `Content-Disposition`, which names the format
    * ([[Ingest.dispositionExtension]]).
    */
  def original(ck: com.histmersion.plugin.FetchedFile.Source.Citekey, dest: java.io.File): Either[Cli.Error, (java.io.File, com.histmersion.plugin.FetchedFile.Source.FileName)] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    send(authed(toFile.toRequest(Api.original, Some(base)).apply(ck)))

  def sourceView(q: Api.SourceViewQuery): Either[Cli.Error, com.histmersion.plugin.FetchedFile.Source.View] = call(Api.sourceView)(q)
  def passages(q: Api.PassagesQuery): Either[Cli.Error, PassageResult] = call(Api.passages)(q)

  // ── portable bundles (docs/research-merge-import.md) ────────────────────────────────────────
  def instance: Either[Cli.Error, InstanceInfo] = call(Api.instance)(())
  def setInstance(label: Option[com.histmersion.cli.AuditIssue.Label.Text]): Either[Cli.Error, InstanceInfo] = call(Api.setInstance)(InstanceLabel(label))

  /** Stream a bundle into `dest` (as [[downloadBackup]]: written to disk, never a byte array). */
  def exportBundle(since: Option[com.histmersion.domain.HttpPolicy.Trust.Bundle.Since], dest: java.io.File): Either[Cli.Error, (java.io.File, com.histmersion.domain.HttpPolicy.Trust.Bundle.Name)] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    send(authed(toFile.toRequest(Api.exportBundle, Some(base)).apply(since)))

  /** The bundle file is the request body, streamed from disk. */
  def verifyBundle(file: java.io.File): Either[Cli.Error, Bundle.VerifyResult] = call(Api.verifyBundle)(file)
  def importBundle(dryRun: com.histmersion.cli.Cli.DryRun, file: java.io.File): Either[Cli.Error, Bundle.ImportResult] =
    call(Api.importBundle)((dryRun, file))
  // the merge plan (M3): the bundle is uploaded once, then planned and imported by its sha256
  def uploadBundle(file: java.io.File): Either[Cli.Error, Bundle.Upload] = call(Api.uploadBundle)(file)
  def mergePlan(req: MergePlan.Request): Either[Cli.Error, MergePlan] = call(Api.mergePlan)(req)
  def mergeImport(req: MergeImportRequest): Either[Cli.Error, Bundle.ImportResult] = call(Api.mergeImport)(req)
  def mergeApply(req: MergeApplyRequest): Either[Cli.Error, MergeApplyJob] = call(Api.mergeApply)(req)
  def mergeApplyStatus(job: com.histmersion.cli.Merge.JobId): Either[Cli.Error, MergeApplyJob] = call(Api.mergeApplyStatus)(job)
  def merges(limit: Option[com.histmersion.api.Paging.Limit]): Either[Cli.Error, List[MergeInfo]] = call(Api.merges)(limit)
  def mergeDetail(id: com.histmersion.api.Api.Merge.Id): Either[Cli.Error, MergeDetail] = call(Api.mergeDetail)(id)
  def mergeLedger(uid: Option[com.histmersion.cli.Merge.Uid], merge: Option[com.histmersion.api.Api.Merge.Id], limit: Option[com.histmersion.api.Paging.Limit]): Either[Cli.Error, MergeLedger] =
    call(Api.mergeLedger)((uid, merge, limit))
  // label renames (V042, V053)
  def renameArticle(slug: com.histmersion.domain.research.Article.Slug, to: com.histmersion.cli.AuditIssue.Label.Text): Either[Cli.Error, LabelRenamed] =
    call(Api.renameArticle)((slug, LabelRename(to)))
  def renameEvent(id: com.histmersion.api.catalogue.Event.Id, to: com.histmersion.cli.AuditIssue.Label.Text): Either[Cli.Error, LabelRenamed] =
    call(Api.renameEvent)((id, LabelRename(to)))
  def renameGoal(slug: com.histmersion.domain.automation.Goal.SlugText, to: com.histmersion.cli.AuditIssue.Label.Text): Either[Cli.Error, LabelRenamed] =
    call(Api.renameGoal)((slug, LabelRename(to)))
  def renamePlace(id: com.histmersion.api.catalogue.Place.Id, to: com.histmersion.cli.AuditIssue.Label.Text): Either[Cli.Error, LabelRenamed] =
    call(Api.renamePlace)((id, LabelRename(to)))

  // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────
  def putSource(ck: com.histmersion.plugin.FetchedFile.Source.Citekey, in: Source.Put, strict: com.histmersion.cli.Cli.Strict = false): Either[Cli.Error, Source.WithFindings] =
    call(Api.putSource)((ck, in, strict))
  def patchSource(ck: com.histmersion.plugin.FetchedFile.Source.Citekey, p: Source.Patch, strict: com.histmersion.cli.Cli.Strict = false): Either[Cli.Error, Source.WithFindings] =
    call(Api.patchSource)((ck, p, strict))
  // the formatting lint of agent-written Markdown
  def lintSource(ck: com.histmersion.plugin.FetchedFile.Source.Citekey): Either[Cli.Error, FormatReport] = call(Api.lintSource)(ck)
  def fixSourceFormat(ck: com.histmersion.plugin.FetchedFile.Source.Citekey): Either[Cli.Error, FormatReport] = call(Api.fixSourceFormat)(ck)
  def lintArticle(slug: com.histmersion.domain.research.Article.Slug): Either[Cli.Error, FormatReport] = call(Api.lintArticle)(slug)
  def fixArticleFormat(slug: com.histmersion.domain.research.Article.Slug): Either[Cli.Error, FormatReport] = call(Api.fixArticleFormat)(slug)
  def checkArticle(slug: com.histmersion.domain.research.Article.Slug, in: Article.Edits.ArticleCheckRequest): Either[Cli.Error, Article.Edits.ArticleCheck] =
    call(Api.checkArticle)((slug, in))
  def lintMarkdown(in: FormatCheck): Either[Cli.Error, FormatReport] = call(OpsApi.lintMarkdown)(in)
  def importNote(req: NoteImport): Either[Cli.Error, SeedResult] = call(Api.importNote)(req)
  def renameSource(ck: com.histmersion.plugin.FetchedFile.Source.Citekey, newCk: com.histmersion.plugin.FetchedFile.Source.Citekey): Either[Cli.Error, Source.Renamed] =
    call(Api.renameSource)((ck, Rename.Request(newCk)))
  def sourceAliases: Either[Cli.Error, List[Source.Alias]] = call(Api.sourceAliases)(())
  def deleteSource(ck: com.histmersion.plugin.FetchedFile.Source.Citekey, apply: com.histmersion.api.Api.Apply): Either[Cli.Error, Source.Deletion] =
    call(Api.deleteSource)((ck, Source.DeleteRequest(apply)))

  // ── the gazetteer (plan §1.6a) ──────────────────────────────────────────────────────────────
  def places(q: Option[com.histmersion.api.Api.Search.Text], asOf: Option[com.histmersion.api.Api.AggregateQuery.AsOf], limit: Option[com.histmersion.api.Paging.Limit]): Either[Cli.Error, List[Place.Hit]] =
    call(Api.places)((q, asOf, limit))
  def place(id: com.histmersion.api.catalogue.Place.Id, asOf: Option[com.histmersion.api.Api.AggregateQuery.AsOf]): Either[Cli.Error, PlaceDetail] = call(Api.place)((id, asOf, None))
  def placeChildren(id: com.histmersion.api.catalogue.Place.Id, asOf: Option[com.histmersion.api.Api.AggregateQuery.AsOf]): Either[Cli.Error, List[Place.Summary]] =
    call(Api.placeChildren)((id, asOf))
  def placeTree(id: com.histmersion.api.catalogue.Place.Id, asOf: Option[com.histmersion.api.Api.AggregateQuery.AsOf], depth: Option[com.histmersion.web.WindowKey.Graph.Depth]): Either[Cli.Error, List[Place.TreeRow]] =
    call(Api.placeTree)((id, asOf, depth))
  def importPlaces(
      kind: Option[com.histmersion.web.PlaceHover.Places.ImportKind],
      body: com.histmersion.web.PlaceHover.Places.Body,
      dataset: Dataset = Dataset.Local
  ): Either[Cli.Error, Place.ImportResult] =
    call(Api.importPlaces)((kind, Some(dataset.wire), body))
  def placeNames(place: Option[com.histmersion.api.catalogue.Place.Id]): Either[Cli.Error, List[ManualPlaceName]] = call(Api.placeNames)(place)
  def addPlaceName(id: com.histmersion.api.catalogue.Place.Id, in: Place.NameAdd): Either[Cli.Error, Place.NameChanged] =
    call(Api.addPlaceName)((id, in))
  def removePlaceName(id: com.histmersion.api.catalogue.Place.Id, in: Place.NameRemove): Either[Cli.Error, Place.NameChanged] =
    call(Api.removePlaceName)((id, in))
  def addPlace(in: Place.Add): Either[Cli.Error, Place.Summary] = call(Api.addPlace)(in)
  def placeOverrides(place: Option[com.histmersion.api.catalogue.Place.Id]): Either[Cli.Error, List[Place.Override]] = call(Api.placeOverrides)(place)
  def setPlaceOverride(id: com.histmersion.api.catalogue.Place.Id, in: Place.OverrideSet): Either[Cli.Error, Place.OverrideChanged] =
    call(Api.setPlaceOverride)((id, in))
  def mergePlace(id: com.histmersion.api.catalogue.Place.Id, in: Place.MergeIn): Either[Cli.Error, Place.Gone] = call(Api.mergePlace)((id, in))
  def removePlace(id: com.histmersion.api.catalogue.Place.Id, in: Place.RemoveIn): Either[Cli.Error, Place.Gone] = call(Api.removePlace)((id, in))
  def rebuildPlaces: Either[Cli.Error, Place.RebuildResult] = call(Api.rebuildPlaces)(())
  def fetchExternalPlaces(source: com.histmersion.api.Api.ExternalPlaces.Source, dryRun: com.histmersion.cli.Cli.DryRun): Either[Cli.Error, Place.ImportResult] =
    call(Api.fetchExternalPlaces)((source, dryRun))
  def placeSources: Either[Cli.Error, List[Gazetteer.SourceInfo]] = call(Api.placeSources)(())
  def loadPlaces(source: com.histmersion.api.Api.ExternalPlaces.Source, apply: com.histmersion.api.Api.Apply): Either[Cli.Error, JobInfo] = call(Api.loadPlaces)((source, apply))
  def buildHierarchy: Either[Cli.Error, SpatialHierarchyResult] = call(Api.buildHierarchy)(())
  // ── named periods (#719) and first-run data (#720) ──────────────────────────────────────────
  def periods(
      q: Option[com.histmersion.api.Api.Search.Text],
      region: Option[com.histmersion.cli.Periods.Region],
      from: Option[com.histmersion.cli.Periods.From],
      to: Option[com.histmersion.cli.Periods.To]
  ): Either[Cli.Error, List[HistoricalPeriod]] = call(Api.periods)((q, region, from, to))
  def period(id: com.histmersion.api.catalogue.Period.Id): Either[Cli.Error, Period.Detail] = call(Api.period)(id)
  def seedPeriods(replace: com.histmersion.api.Api.Overwrite = false): Either[Cli.Error, Period.ImportResult] = call(Api.seedPeriods)(replace)
  def importPeriods(
      collections: List[com.histmersion.cli.Periods.Collection],
      body: com.histmersion.cli.Periods.Body,
      dataset: Dataset = Dataset.Local
  ): Either[Cli.Error, Period.ImportResult] =
    call(Api.importPeriods)((collections, Some(dataset.wire), body))
  def fetchPeriods(source: com.histmersion.cli.Periods.Source, collections: List[com.histmersion.cli.Periods.Collection]): Either[Cli.Error, Period.ImportResult] =
    call(Api.fetchPeriods)((source, collections))
  def init(fetch: com.histmersion.cli.Init.Fetch): Either[Cli.Error, JobInfo] = call(Api.init)(fetch)
  // ── events (plan §1.2) ──────────────────────────────────────────────────────────────────────
  def listEvents(q: Api.EventQuery): Either[Cli.Error, EventList] = call(Api.events)(q)
  def getEvent(id: com.histmersion.api.catalogue.Event.Id): Either[Cli.Error, EventDetail] = call(Api.getEvent)(id)
  def putEvent(id: com.histmersion.api.catalogue.Event.Id, in: EventPut): Either[Cli.Error, EventDetail] = call(Api.putEvent)((id, in))
  def patchEvent(id: com.histmersion.api.catalogue.Event.Id, p: EventPatch): Either[Cli.Error, EventDetail] = call(Api.patchEvent)((id, p))
  def deleteEvent(id: com.histmersion.api.catalogue.Event.Id): Either[Cli.Error, EventDeleted] = call(Api.deleteEvent)(id)
  // ── goals (plan §1.5) ──────────────────────────────────────────────────────────────────────
  def listGoals(q: Api.GoalQuery): Either[Cli.Error, Goal.List] = call(Api.goals)(q)
  def getGoal(slug: com.histmersion.domain.automation.Goal.SlugText): Either[Cli.Error, Goal] = call(Api.getGoal)(slug)
  def putGoal(slug: com.histmersion.domain.automation.Goal.SlugText, in: Goal.Put): Either[Cli.Error, Goal] = call(Api.putGoal)((slug, in))
  def patchGoal(slug: com.histmersion.domain.automation.Goal.SlugText, p: Goal.Patch): Either[Cli.Error, Goal] = call(Api.patchGoal)((slug, p))
  def deleteGoal(slug: com.histmersion.domain.automation.Goal.SlugText): Either[Cli.Error, Goal.Deleted] = call(Api.deleteGoal)(slug)
  // ── agent state (plan §1.5) ───────────────────────────────────────────────────────────────
  def agentStateList(): Either[Cli.Error, List[AgentStateEntry]] = call(Api.agentStateList)(())
  def agentStateGet(key: com.histmersion.api.Api.AgentState.Key): Either[Cli.Error, AgentStateEntry] = call(Api.agentStateGet)(key)
  def agentStateSet(key: com.histmersion.api.Api.AgentState.Key, in: AgentStatePut): Either[Cli.Error, AgentStateEntry] =
    call(Api.agentStateSet)((key, in))
  def agentStateDelete(key: com.histmersion.api.Api.AgentState.Key): Either[Cli.Error, AgentStateDeleted] = call(Api.agentStateDelete)(key)
  // ── attestations (plan §1.3) ───────────────────────────────────────────────────────────────
  def listAttestations(q: Api.AttestationQuery): Either[Cli.Error, Attestation.List] = call(Api.attestations)(q)
  def getAttestation(id: com.histmersion.api.catalogue.Attestation.Id): Either[Cli.Error, Attestation] = call(Api.getAttestation)(id)
  def createAttestation(in: Attestation.Put): Either[Cli.Error, Attestation] = call(Api.createAttestation)(in)
  def patchAttestation(id: com.histmersion.api.catalogue.Attestation.Id, p: Attestation.Patch): Either[Cli.Error, Attestation] =
    call(Api.patchAttestation)((id, p))
  def deleteAttestation(id: com.histmersion.api.catalogue.Attestation.Id): Either[Cli.Error, Attestation.Deleted] = call(Api.deleteAttestation)(id)

  def exportSource(ck: com.histmersion.plugin.FetchedFile.Source.Citekey): Either[Cli.Error, com.histmersion.plugin.FetchedFile.Source.Markdown] = call(Api.exportSource)(ck)
  def citekeys: Either[Cli.Error, List[com.histmersion.plugin.FetchedFile.Source.Citekey]] = call(Api.citekeys)(())
  def resolveEdges: Either[Cli.Error, EdgesResolved] = call(Api.resolveEdges)(())

  // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────
  def listArticles(q: Api.ArticleQuery): Either[Cli.Error, Article.List] = call(Api.articles)(q)
  def getArticle(slug: com.histmersion.domain.research.Article.Slug): Either[Cli.Error, Article.View] = call(Api.getArticle)((slug, false))
  def putArticle(slug: com.histmersion.domain.research.Article.Slug, in: Article.Put, strict: com.histmersion.cli.Cli.Strict = false): Either[Cli.Error, Article.View] =
    call(Api.putArticle)((slug, in, strict))
  def patchArticle(slug: com.histmersion.domain.research.Article.Slug, p: Article.Patch, strict: com.histmersion.cli.Cli.Strict = false): Either[Cli.Error, Article.View] =
    call(Api.patchArticle)((slug, p, strict))
  def deleteArticle(slug: com.histmersion.domain.research.Article.Slug): Either[Cli.Error, Article.Deleted] = call(Api.deleteArticle)(slug)
  def articleCoverage(slug: com.histmersion.domain.research.Article.Slug): Either[Cli.Error, Coverage] = call(Api.articleCoverage)(slug)
  def articleReview(slug: com.histmersion.domain.research.Article.Slug): Either[Cli.Error, Article.ReviewView] = call(Api.articleReview)(slug)
  def exportArticle(slug: com.histmersion.domain.research.Article.Slug): Either[Cli.Error, com.histmersion.domain.research.Article.Markdown] = call(Api.exportArticle)(slug)
  def importArticle(req: Article.Import): Either[Cli.Error, SeedResult] = call(Api.importArticle)(req)
  def articleSlugs: Either[Cli.Error, List[com.histmersion.domain.research.Article.Slug]] = call(Api.articleSlugs)(())
  def articleVersions(slug: com.histmersion.domain.research.Article.Slug): Either[Cli.Error, List[Article.Version]] =
    call(Api.articleVersions)(slug)
  def articleVersion(slug: com.histmersion.domain.research.Article.Slug, versionId: com.histmersion.domain.research.Article.VersionId): Either[Cli.Error, Article.VersionFull] =
    call(Api.articleVersion)((slug, versionId))
  // ── lint · queue · read-only query (plan §5.2 stream 2.8) ───────────────────────────────────
  def lint(rules: List[com.histmersion.domain.EnrichSource.Closed.Lint.Rule]): Either[Cli.Error, Lint.LintReport] = call(OpsApi.lint)(rules)
  def lintFix(rules: List[com.histmersion.domain.EnrichSource.Closed.Lint.Rule]): Either[Cli.Error, Lint.LintFixResult] = call(OpsApi.lintFix)(rules)
  def queueList(list: com.histmersion.cli.Queue.Name, all: com.histmersion.cli.Queue.All): Either[Cli.Error, QueueListing] =
    call(OpsApi.queueList)((list, all, None, None))
  def queueAdd(list: com.histmersion.cli.Queue.Name, in: Queue.QueueAdd): Either[Cli.Error, Queue.QueueItem] =
    call(OpsApi.queueAdd)((list, in))
  def queuePatch(id: com.histmersion.cli.Queue.ItemId, p: Queue.QueuePatch): Either[Cli.Error, Queue.QueueItem] =
    call(OpsApi.queuePatch)((id, p))
  def queueDelete(id: com.histmersion.cli.Queue.ItemId): Either[Cli.Error, Queue.QueueItem] = call(OpsApi.queueDelete)(id)
  def queueExport(list: com.histmersion.cli.Queue.Name): Either[Cli.Error, com.histmersion.cli.Queue.Markdown] = call(OpsApi.queueExport)(list)
  def goalsExport: Either[Cli.Error, com.histmersion.domain.automation.Goal.Export] = call(OpsApi.goalsExport)(())
  def queueImport(list: com.histmersion.cli.Queue.Name, req: Queue.QueueImport): Either[Cli.Error, Queue.QueueImported] =
    call(OpsApi.queueImport)((list, req))
  def query(sql: com.histmersion.domain.PassageFinder.Query.Statement): Either[Cli.Error, Query.QueryResult] = call(OpsApi.query)(Query.QueryRequest(sql))
  def schemaTables: Either[Cli.Error, Query.SchemaTables] = call(OpsApi.schemaTables)(())
  // ── ingest, jobs and assets (plan §1.8) ─────────────────────────────────────────────────────
  /** Upload one document. The body is the FILE itself (tapir `fileBody`), so a 200 MB scan is streamed rather than held
    * in memory, and every other parameter travels as a query parameter.
    */
  def ingest(p: Ingest.Params, file: java.io.File): Either[Cli.Error, Ingest.Accepted] =
    call(Api.ingest)((p, file))
  def reocr(ck: com.histmersion.plugin.FetchedFile.Source.Citekey, quality: com.histmersion.cli.Ingest.Quality): Either[Cli.Error, Ingest.Accepted] = call(Api.reocr)((ck, quality))
  def job(id: com.histmersion.api.SpatialHierarchyResult.Job.Id): Either[Cli.Error, JobInfo] = call(Api.job)(id.value)
  def basemapStatus(): Either[Cli.Error, Basemap.Status] = call(BasemapApi.status)(())
  def basemapFetch(q: Basemap.FetchQuery): Either[Cli.Error, JobInfo] = call(BasemapApi.fetch)(q)
  def basemapRemove(q: Basemap.RemoveQuery): Either[Cli.Error, Basemap.Removed] = call(BasemapApi.remove)(q)
  def jobs(
      f: ListFilters.JobFilter,
      limit: Option[com.histmersion.api.Paging.Limit],
      before: Option[com.histmersion.cli.Jobs.Before] = None
  ): Either[Cli.Error, (List[JobInfo], Paging.Page)] =
    call(Api.jobs)((f, limit, before, None))
  def assets(ck: com.histmersion.plugin.FetchedFile.Source.Citekey): Either[Cli.Error, List[AssetInfo]] = call(Api.assets)(ck)
  def putAssets(ck: com.histmersion.plugin.FetchedFile.Source.Citekey, in: List[AssetInput]): Either[Cli.Error, AssetsStored] =
    call(Api.putAssets)((ck, in))
  def assetDetail(ck: com.histmersion.plugin.FetchedFile.Source.Citekey, name: com.histmersion.server.Static.Asset.Name): Either[Cli.Error, AssetDetail] = call(Api.assetDetail)((ck, name))
  def patchAsset(ck: com.histmersion.plugin.FetchedFile.Source.Citekey, name: com.histmersion.server.Static.Asset.Name, p: AssetPatch): Either[Cli.Error, AssetDetail] =
    call(Api.patchAsset)((ck, name, p))
  def assetLinks(
      ck: Option[com.histmersion.plugin.FetchedFile.Source.Citekey],
      asset: Option[com.histmersion.server.Static.Asset.Name],
      tt: Option[com.histmersion.api.catalogue.Attestation.TargetType],
      ti: Option[com.histmersion.api.catalogue.Attestation.TargetId]
  ): Either[Cli.Error, AssetLink.List] =
    call(Api.assetLinks)((ck, asset, tt, ti))
  def createAssetLink(in: AssetLink.Put): Either[Cli.Error, AssetLink] = call(Api.createAssetLink)(in)
  def deleteAssetLink(id: com.histmersion.cli.AssetLink.Id): Either[Cli.Error, AssetLink.Deleted] = call(Api.deleteAssetLink)(id)
  // ── media from outside providers (#1446) ──
  def mediaImport(req: MediaImportRequest): Either[Cli.Error, MediaImportReport] = call(Api.mediaImport)(req)
  def mediaImportJob(req: MediaImportRequest): Either[Cli.Error, JobInfo] = call(Api.mediaImportJob)(req)
  def mediaCovers(req: MediaCoversRequest): Either[Cli.Error, MediaCoversReport] = call(Api.mediaCovers)(req)
  def mediaCoversJob(req: MediaCoversRequest): Either[Cli.Error, JobInfo] = call(Api.mediaCoversJob)(req)
  def putSourceCover(p: SourceCoverParams, bytes: com.histmersion.api.Api.FileBytes): Either[Cli.Error, MediaItem] =
    call(Api.putSourceCover)((p, bytes))
  def mediaList(q: Api.MediaQuery): Either[Cli.Error, MediaList] = call(Api.mediaList)(q)
  def mediaShow(uid: com.histmersion.web.PointKinds.Media.Uid): Either[Cli.Error, MediaItem] = call(Api.mediaShow)(uid)
  def mediaFacets: Either[Cli.Error, MediaFacets] = call(Api.mediaFacets)(())
  def mediaProviders: Either[Cli.Error, List[MediaProvider.Info]] = call(Api.mediaProviders)(())
  def fillFigureLicences(apply: com.histmersion.api.Api.Apply): Either[Cli.Error, FigureLicenceFill] = call(Api.fillFigureLicences)(apply)

object Client:
  type HttpStatus = HttpStatus.T
  object HttpStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Write = Write.T
  object Write extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** POST/PUT/PATCH/DELETE change the corpus; anything else (a GET download) only reads it. */

  type ServerUrl = ServerUrl.T
  object ServerUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Token = Token.T
  object Token extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def isWrite(m: sttp.model.Method): Boolean =
    m == sttp.model.Method.POST || m == sttp.model.Method.PUT || m == sttp.model.Method.PATCH ||
      m == sttp.model.Method.DELETE

  /** What to say when the server answered something the endpoint does not describe. Pure. A refusal names what was
    * refused (#819: a refused backup download used to read "refused this write").
    */
  def unexpected(status: com.histmersion.cli.Client.HttpStatus, serverUrl: com.histmersion.cli.Client.ServerUrl, write: com.histmersion.cli.Client.Write): com.histmersion.cli.Client.Problem = status match
    case 401 | 403 =>
      val what = if write then "this write" else "this request"
      s"$serverUrl refused $what: it needs its API key — pass --token <key> or set HISTMERSION_TOKEN"
    case 404 => s"$serverUrl has no such endpoint (HTTP 404) — is the CLI newer than the server?"
    case c   => s"unexpected answer from $serverUrl: HTTP $c"
