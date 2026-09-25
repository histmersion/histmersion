package com.histmersion.cli

import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.tapir.client.sttp4.{SttpClientInterpreter, SttpClientOptions}
import sttp.tapir.PublicEndpoint
import sttp.model.Uri
import com.histmersion.core.*

/** Typed HTTP client over the `Api` contract: each call is derived from the same Tapir endpoint the
  * server interprets, so request/response shapes cannot drift. Sync (java.net.http), native-safe.
  * `token` is the `Authorization: Bearer <key>` value for authenticated servers; `None` = local. */
final class Client(serverUrl: String, token: Option[String] = None):
  private val base = Uri.unsafeParse(serverUrl)

  // HTTP/1.1, explicitly. sttp's sync backend is java.net.http, whose default HTTP/2 client reuses a
  // connection in a way the server's netty backend mishandles for body-carrying requests: the SECOND
  // upload on a reused connection arrives with an EMPTY body and is rejected. `histmersion ingest` sends
  // exactly such requests. (Same reason the server pins 1.1 when calling the OCR sidecar.)
  private val backend = HttpClientSyncBackend.usingClient(
    java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build())
  private val interp = SttpClientInterpreter()

  // `private[cli]` so the acquisition client (stream 2.6) reuses this one transport rather than
  // opening a second backend with its own error handling.
  private[cli] def call[I, O](ep: PublicEndpoint[I, ApiError, O, Any])(in: I): Either[CliError, O] =
    // No client-side read timeout: the server bounds every request itself
    // (`server.request-timeout-seconds`), and sttp's 1-minute default is shorter than a backup.
    val base0 = interp.toRequestThrowDecodeFailures(ep, Some(base)).apply(in).readTimeout(scala.concurrent.duration.Duration.Inf)
    val req   = token.fold(base0)(t => base0.auth.bearer(t))
    scala.util.Try(req.send(backend)) match
      case scala.util.Failure(e) =>
        Left(CliError(s"cannot reach Histmersion at $serverUrl (${e.getClass.getSimpleName}: ${e.getMessage}) — is the server running?"))
      case scala.util.Success(resp) =>
        resp.body match
          case Right(o) => Right(o)
          case Left(e)  => Left(CliError(e.message, exitCode = 2))

  def version: Either[CliError, VersionInfo]                                = call(Api.version)(())
  def health: Either[CliError, Health]                                      = call(Api.health)(())
  def search(q: Option[String], limit: Option[Int]): Either[CliError, List[SearchHit]] =
    call(Api.search)((q, limit))
  def sources(f: Filter): Either[CliError, SearchResult]                    = call(Api.sources)(f)
  def facets(field: String, prefix: Option[String], f: Filter): Either[CliError, List[Facet]] =
    call(Api.facets)((field, prefix, f))
  def getSource(ck: String): Either[CliError, SourceWithFindings]           = call(Api.getSource)((ck, true))
  def findings(q: Api.FindingsQuery): Either[CliError, List[GeoHit]]        = call(Api.findings)(q)
  def aggregate(q: Api.AggregateQuery): Either[CliError, TimeHistogram]    = call(Api.aggregate)(q)
  def neighbors(ck: String, kinds: List[String] = Nil): Either[CliError, List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  def graph(ck: String, depth: Option[Int]): Either[CliError, SourceGraph] =
    call(Api.graph)((ck, depth))
  def contradictions(ck: Option[String], since: Option[Long]): Either[CliError, ContradictionsResult] =
    call(Api.contradictions)((ck, since))
  def seed(req: SeedRequest): Either[CliError, SeedResult]                  = call(Api.seed)(req)
  def reindex(after: Option[String], limit: Option[Int]): Either[CliError, ReindexResult] =
    call(Api.reindex)((after, limit))

  // ── backup / restore (plan §1.10): the DUMP is taken server-side; the host only copies it off ──
  def backup: Either[CliError, BackupStatus]      = call(Api.backup)(())
  def backupStatus: Either[CliError, BackupStatus] = call(Api.backupStatus)(())
  def backups: Either[CliError, List[BackupInfo]] = call(Api.backups)(())
  def restore(req: RestoreRequest): Either[CliError, RestoreResult] = call(Api.restore)(req)

  /** Stream one archive into `dest`. Tapir's `fileBody` is interpreted by sttp as `asFileAlways`
    * against the file `SttpClientOptions.createFile` hands it, so the response is written straight to
    * disk — a multi-GB dump never becomes a byte array. */
  def downloadBackup(file: String, dest: java.io.File): Either[CliError, java.io.File] =
    val toFile = SttpClientInterpreter(SttpClientOptions(() => dest))
    val req    = toFile.toRequestThrowDecodeFailures(Api.downloadBackup, Some(base)).apply(file)
    scala.util.Try(req.send(backend)) match
      case scala.util.Failure(e) =>
        Left(CliError(s"cannot reach Histmersion at $serverUrl (${e.getClass.getSimpleName}: ${e.getMessage}) — is the server running?"))
      case scala.util.Success(resp) =>
        resp.body match
          case Right(f) => Right(f)
          case Left(e)  => Left(CliError(e.message, exitCode = 2))

  def sourceView(q: Api.SourceViewQuery): Either[CliError, String] = call(Api.sourceView)(q)

  // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────
  def putSource(ck: String, in: SourcePut): Either[CliError, SourceWithFindings] =
    call(Api.putSource)((ck, in))
  def patchSource(ck: String, p: SourcePatch): Either[CliError, SourceWithFindings] =
    call(Api.patchSource)((ck, p))
  def importNote(req: NoteImport): Either[CliError, SeedResult] = call(Api.importNote)(req)

  // ── the gazetteer (plan §1.6a) ──────────────────────────────────────────────────────────────
  def places(q: Option[String], asOf: Option[Long], limit: Option[Int]): Either[CliError, List[PlaceHit]] =
    call(Api.places)((q, asOf, limit))
  def place(id: String, asOf: Option[Long]): Either[CliError, PlaceDetail] = call(Api.place)((id, asOf))
  def placeChildren(id: String, asOf: Option[Long]): Either[CliError, List[PlaceSummary]] =
    call(Api.placeChildren)((id, asOf))
  def placeTree(id: String, asOf: Option[Long], depth: Option[Int]): Either[CliError, List[PlaceTreeRow]] =
    call(Api.placeTree)((id, asOf, depth))
  def importPlaces(kind: Option[String], body: String): Either[CliError, PlaceImportResult] =
    call(Api.importPlaces)((kind, body))
  def rebuildPlaces: Either[CliError, PlaceRebuildResult] = call(Api.rebuildPlaces)(())
  def fetchExternalPlaces(source: String, dryRun: Boolean): Either[CliError, PlaceImportResult] =
    call(Api.fetchExternalPlaces)((source, dryRun))
  def buildHierarchy: Either[CliError, SpatialHierarchyResult] = call(Api.buildHierarchy)(())
  // ── events (plan §1.2) ──────────────────────────────────────────────────────────────────────
  def listEvents(q: Api.EventQuery): Either[CliError, EventList] = call(Api.events)(q)
  def getEvent(id: String): Either[CliError, EventDetail]        = call(Api.getEvent)(id)
  def putEvent(id: String, in: EventPut): Either[CliError, EventDetail]   = call(Api.putEvent)((id, in))
  def patchEvent(id: String, p: EventPatch): Either[CliError, EventDetail] = call(Api.patchEvent)((id, p))
  def deleteEvent(id: String): Either[CliError, EventDeleted]    = call(Api.deleteEvent)(id)
  // ── goals (plan §1.5) ──────────────────────────────────────────────────────────────────────
  def listGoals(q: Api.GoalQuery): Either[CliError, GoalList] = call(Api.goals)(q)
  def getGoal(slug: String): Either[CliError, Goal]            = call(Api.getGoal)(slug)
  def putGoal(slug: String, in: GoalPut): Either[CliError, Goal] = call(Api.putGoal)((slug, in))
  def patchGoal(slug: String, p: GoalPatch): Either[CliError, Goal] = call(Api.patchGoal)((slug, p))
  def deleteGoal(slug: String): Either[CliError, GoalDeleted]  = call(Api.deleteGoal)(slug)
  // ── agent state (plan §1.5) ───────────────────────────────────────────────────────────────
  def agentStateList(): Either[CliError, List[AgentStateEntry]] = call(Api.agentStateList)(())
  def agentStateGet(key: String): Either[CliError, AgentStateEntry] = call(Api.agentStateGet)(key)
  def agentStateSet(key: String, in: AgentStatePut): Either[CliError, AgentStateEntry] = call(Api.agentStateSet)((key, in))
  def agentStateDelete(key: String): Either[CliError, AgentStateDeleted] = call(Api.agentStateDelete)(key)
  // ── attestations (plan §1.3) ───────────────────────────────────────────────────────────────
  def listAttestations(q: Api.AttestationQuery): Either[CliError, AttestationList] = call(Api.attestations)(q)
  def getAttestation(id: Long): Either[CliError, Attestation]    = call(Api.getAttestation)(id)
  def createAttestation(in: AttestationPut): Either[CliError, Attestation] = call(Api.createAttestation)(in)
  def patchAttestation(id: Long, p: AttestationPatch): Either[CliError, Attestation] = call(Api.patchAttestation)((id, p))
  def deleteAttestation(id: Long): Either[CliError, AttestationDeleted] = call(Api.deleteAttestation)(id)

  def exportSource(ck: String): Either[CliError, String]        = call(Api.exportSource)(ck)
  def citekeys: Either[CliError, List[String]]                  = call(Api.citekeys)(())
  def resolveEdges: Either[CliError, EdgesResolved]             = call(Api.resolveEdges)(())

  // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────
  def listArticles(q: Api.ArticleQuery): Either[CliError, ArticleList] = call(Api.articles)(q)
  def getArticle(slug: String): Either[CliError, ArticleView]          = call(Api.getArticle)(slug)
  def putArticle(slug: String, in: ArticlePut): Either[CliError, ArticleView] =
    call(Api.putArticle)((slug, in))
  def patchArticle(slug: String, p: ArticlePatch): Either[CliError, ArticleView] =
    call(Api.patchArticle)((slug, p))
  def deleteArticle(slug: String): Either[CliError, ArticleDeleted]    = call(Api.deleteArticle)(slug)
  def exportArticle(slug: String): Either[CliError, String]            = call(Api.exportArticle)(slug)
  def importArticle(req: ArticleImport): Either[CliError, SeedResult]  = call(Api.importArticle)(req)
  def articleSlugs: Either[CliError, List[String]]                     = call(Api.articleSlugs)(())
  def articleVersions(slug: String): Either[CliError, List[ArticleVersion]] =
    call(Api.articleVersions)(slug)
  def articleVersion(slug: String, versionId: Long): Either[CliError, ArticleVersionFull] =
    call(Api.articleVersion)((slug, versionId))
  // ── lint · queue · read-only query (plan §5.2 stream 2.8) ───────────────────────────────────
  def lint(rules: List[String]): Either[CliError, Lint.LintReport] = call(OpsApi.lint)(rules)
  def lintFix: Either[CliError, Lint.LintFixResult]                = call(OpsApi.lintFix)(())
  def queueList(list: String, all: Boolean): Either[CliError, Queue.QueueListing] =
    call(OpsApi.queueList)((list, all))
  def queueAdd(list: String, in: Queue.QueueAdd): Either[CliError, Queue.QueueItem] =
    call(OpsApi.queueAdd)((list, in))
  def queuePatch(id: Int, p: Queue.QueuePatch): Either[CliError, Queue.QueueItem] =
    call(OpsApi.queuePatch)((id, p))
  def queueDelete(id: Int): Either[CliError, Queue.QueueItem] = call(OpsApi.queueDelete)(id)
  def queueExport(list: String): Either[CliError, String]     = call(OpsApi.queueExport)(list)
  def queueImport(list: String, req: Queue.QueueImport): Either[CliError, Queue.QueueImported] =
    call(OpsApi.queueImport)((list, req))
  def query(sql: String): Either[CliError, Query.QueryResult] = call(OpsApi.query)(Query.QueryRequest(sql))
  def schemaTables: Either[CliError, Query.SchemaTables]      = call(OpsApi.schemaTables)(())
  // ── ingest, jobs and assets (plan §1.8) ─────────────────────────────────────────────────────
  /** Upload one document. The body is the FILE itself (tapir `fileBody`), so a 200 MB scan is
    * streamed rather than held in memory, and every other parameter travels as a query parameter. */
  def ingest(p: IngestParams, file: java.io.File): Either[CliError, IngestAccepted] =
    call(Api.ingest)((p, file))
  def reocr(ck: String, quality: Boolean): Either[CliError, IngestAccepted] = call(Api.reocr)((ck, quality))
  def job(id: String): Either[CliError, JobInfo]                            = call(Api.job)(id)
  def jobs(status: Option[String], limit: Option[Int]): Either[CliError, List[JobInfo]] =
    call(Api.jobs)((status, limit))
  def assets(ck: String): Either[CliError, List[AssetInfo]]                 = call(Api.assets)(ck)
  def putAssets(ck: String, in: List[AssetInput]): Either[CliError, AssetsStored] =
    call(Api.putAssets)((ck, in))
  def assetDetail(ck: String, name: String): Either[CliError, AssetDetail] = call(Api.assetDetail)((ck, name))
  def patchAsset(ck: String, name: String, p: AssetPatch): Either[CliError, AssetDetail] =
    call(Api.patchAsset)((ck, name, p))
  def assetLinks(ck: Option[String], asset: Option[String], tt: Option[String], ti: Option[String]): Either[CliError, AssetLinkList] =
    call(Api.assetLinks)((ck, asset, tt, ti))
  def createAssetLink(in: AssetLinkPut): Either[CliError, AssetLink] = call(Api.createAssetLink)(in)
  def deleteAssetLink(id: Long): Either[CliError, AssetLinkDeleted] = call(Api.deleteAssetLink)(id)

/** A user-facing failure: message + process exit code (1 = usage, 2 = server said no, 3 = unreachable). */
final case class CliError(message: String, exitCode: Int = 3)
