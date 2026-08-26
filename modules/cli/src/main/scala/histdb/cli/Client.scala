package histdb.cli

import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.tapir.client.sttp4.{SttpClientInterpreter, SttpClientOptions}
import sttp.tapir.PublicEndpoint
import sttp.model.Uri
import histdb.core.*

/** Typed HTTP client over the `Api` contract: each call is derived from the same Tapir endpoint the
  * server interprets, so request/response shapes cannot drift. Sync (java.net.http), native-safe. */
final class Client(serverUrl: String):
  private val base = Uri.unsafeParse(serverUrl)

  // HTTP/1.1, explicitly. sttp's sync backend is java.net.http, whose default HTTP/2 client reuses a
  // connection in a way the server's netty backend mishandles for body-carrying requests: the SECOND
  // upload on a reused connection arrives with an EMPTY body and is rejected. `histdb ingest` sends
  // exactly such requests. (Same reason the server pins 1.1 when calling the OCR sidecar.)
  private val backend = HttpClientSyncBackend.usingClient(
    java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build())
  private val interp = SttpClientInterpreter()

  // `private[cli]` so the acquisition client (stream 2.6) reuses this one transport rather than
  // opening a second backend with its own error handling.
  private[cli] def call[I, O](ep: PublicEndpoint[I, ApiError, O, Any])(in: I): Either[CliError, O] =
    // No client-side read timeout: the server bounds every request itself
    // (`server.request-timeout-seconds`), and sttp's 1-minute default is shorter than a backup.
    val req = interp.toRequestThrowDecodeFailures(ep, Some(base)).apply(in).readTimeout(scala.concurrent.duration.Duration.Inf)
    scala.util.Try(req.send(backend)) match
      case scala.util.Failure(e) =>
        Left(CliError(s"cannot reach hist-db at $serverUrl (${e.getClass.getSimpleName}: ${e.getMessage}) — is the server running?"))
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
  def getSource(ck: String): Either[CliError, SourceWithFindings]           = call(Api.getSource)(ck)
  def findings(q: Api.FindingsQuery): Either[CliError, List[GeoHit]]        = call(Api.findings)(q)
  def aggregate(q: Api.AggregateQuery): Either[CliError, TimeHistogram]    = call(Api.aggregate)(q)
  def neighbors(ck: String, kinds: List[String] = Nil): Either[CliError, List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  def contradictions(ck: Option[String], since: Option[Long]): Either[CliError, ContradictionsResult] =
    call(Api.contradictions)((ck, since))
  def seed(req: SeedRequest): Either[CliError, SeedResult]                  = call(Api.seed)(req)
  def reindex(after: Option[String], limit: Option[Int]): Either[CliError, ReindexResult] =
    call(Api.reindex)((after, limit))

  // ── backup / restore (plan §1.10): the DUMP is taken server-side; the host only copies it off ──
  def backup: Either[CliError, BackupResult]      = call(Api.backup)(())
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
        Left(CliError(s"cannot reach hist-db at $serverUrl (${e.getClass.getSimpleName}: ${e.getMessage}) — is the server running?"))
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

/** A user-facing failure: message + process exit code (1 = usage, 2 = server said no, 3 = unreachable). */
final case class CliError(message: String, exitCode: Int = 3)
