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
  private val base    = Uri.unsafeParse(serverUrl)
  private val backend = HttpClientSyncBackend()
  private val interp  = SttpClientInterpreter()

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

/** A user-facing failure: message + process exit code (1 = usage, 2 = server said no, 3 = unreachable). */
final case class CliError(message: String, exitCode: Int = 3)
