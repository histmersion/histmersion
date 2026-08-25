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
  def getSource(ck: String): Either[CliError, SourceWithFindings]           = call(Api.getSource)(ck)
  def findings(q: Api.FindingsQuery): Either[CliError, List[GeoHit]]        = call(Api.findings)(q)
  def aggregate(q: Api.AggregateQuery): Either[CliError, TimeHistogram]    = call(Api.aggregate)(q)
  def neighbors(ck: String): Either[CliError, List[NeighborRow]]            = call(Api.neighbors)(ck)
  def seed(req: SeedRequest): Either[CliError, SeedResult]                  = call(Api.seed)(req)

/** A user-facing failure: message + process exit code (1 = usage, 2 = server said no, 3 = unreachable). */
final case class CliError(message: String, exitCode: Int = 3)
