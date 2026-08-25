package histdb.cli

import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.tapir.client.sttp4.SttpClientInterpreter
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

  private def call[I, O](ep: PublicEndpoint[I, ApiError, O, Any])(in: I): Either[CliError, O] =
    val req = interp.toRequestThrowDecodeFailures(ep, Some(base)).apply(in)
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
  def neighbors(ck: String): Either[CliError, List[NeighborRow]]            = call(Api.neighbors)(ck)
  def seed(req: SeedRequest): Either[CliError, SeedResult]                  = call(Api.seed)(req)
  def reindex(after: Option[String], limit: Option[Int]): Either[CliError, ReindexResult] =
    call(Api.reindex)((after, limit))

  // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────
  def putSource(ck: String, in: SourcePut): Either[CliError, SourceWithFindings] =
    call(Api.putSource)((ck, in))
  def patchSource(ck: String, p: SourcePatch): Either[CliError, SourceWithFindings] =
    call(Api.patchSource)((ck, p))
  def importNote(req: NoteImport): Either[CliError, SeedResult] = call(Api.importNote)(req)
  def exportSource(ck: String): Either[CliError, String]        = call(Api.exportSource)(ck)
  def citekeys: Either[CliError, List[String]]                  = call(Api.citekeys)(())
  def resolveEdges: Either[CliError, EdgesResolved]             = call(Api.resolveEdges)(())

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
