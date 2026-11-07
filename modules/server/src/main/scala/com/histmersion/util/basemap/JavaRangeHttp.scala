package com.histmersion.util.basemap

import com.histmersion.util.storage.Reservation

import java.io.InputStream
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.{Duration, Instant}
import scala.util.Using

/** The real client: java.net.http over `client` (the composition root's: HTTP/1.1, one stream per request — the
  * providers are CDNs — following redirects), as the product's `userAgent`, a slow-down answer asked again when
  * `policy` says so, at most `retries` times.
  */
final class JavaRangeHttp(
    client: HttpClient,
    timeoutSeconds: JavaRangeHttp.TimeoutSeconds,
    retries: JavaRangeHttp.Retries,
    userAgent: JavaRangeHttp.UserAgent,
    policy: RetryPolicy,
    sleep: JavaRangeHttp.SleepArg1 => Unit = ms => Thread.sleep(ms)
)(using clock: java.time.Clock) extends RangeHttp:

  private def request(url: com.histmersion.util.basemap.BasemapFetch.Source.Url, range: Option[(com.histmersion.ByteOffset, com.histmersion.ByteOffset)]): HttpRequest =
    val b = HttpRequest
      .newBuilder(URI.create(url))
      .GET()
      .timeout(Duration.ofSeconds(timeoutSeconds.toLong))
      .header("User-Agent", userAgent)
    range.foreach((o, l) => b.header("Range", s"bytes=$o-${o + l - 1}"))
    b.build()

  /** Send, asking again on a slow-down answer or a dropped connection. The body is a STREAM, read only once the answer
    * is known to be the one asked for — an answer that is not (a 200 with the whole 100 GB file) is closed unread,
    * which cancels it.
    */
  private def send(url: com.histmersion.util.basemap.BasemapFetch.Source.Url, req: HttpRequest): Either[com.histmersion.util.basemap.JavaRangeHttp.Problem, HttpResponse[InputStream]] =
    def attempt(n: Int): Either[com.histmersion.util.basemap.JavaRangeHttp.Problem, HttpResponse[InputStream]] =
      scala.util.Try(client.send(req, HttpResponse.BodyHandlers.ofInputStream())) match
        case scala.util.Failure(e: InterruptedException) => throw e
        case scala.util.Failure(e)                       =>
          if n < retries then { sleep(policy.backoffMs(n)); attempt(n + 1) }
          else Left(s"$url unreachable: ${e.getClass.getSimpleName}: ${e.getMessage}")
        case scala.util.Success(resp) =>
          policy.slowDown(
            resp.statusCode,
            Option(resp.headers.firstValue("Retry-After").orElse(null)),
            n,
            retries,
            300,
            Instant.now(clock)
          ) match
            case Some(RetryWait(ms, why)) =>
              resp.body.close()
              scribe.warn(s"basemap: $why — asking $url again in ${ms}ms")
              sleep(ms)
              attempt(n + 1)
            case None => Right(resp)
    attempt(0)

  private def meta[T](r: HttpResponse[T], total: com.histmersion.ByteCount): RemoteFile =
    RemoteFile(total, Option(r.headers.firstValue("ETag").orElse(null)))

  /** The answer's body when `ok` accepts it, closed unread otherwise. */
  private def reading[A](r: HttpResponse[InputStream])(
      ok: HttpResponse[InputStream] => Either[com.histmersion.util.basemap.JavaRangeHttp.Problem, A]
  ): Either[com.histmersion.util.basemap.JavaRangeHttp.Problem, A] =
    try ok(r)
    finally r.body.close()

  private def ranged(url: com.histmersion.util.basemap.BasemapFetch.Source.Url, offset: com.histmersion.ByteOffset, length: com.histmersion.ByteCount) =
    send(url, request(url, Some((offset, length)))).flatMap { r =>
      RangeHttp.check(
        url,
        r.statusCode,
        Option(r.headers.firstValue("Content-Range").orElse(null)),
        offset,
        length
      ) match
        case Left(e)      => r.body.close(); Left(e)
        case Right(total) => Right((r, total))
    }

  override def file(url: String): Either[String, Option[Array[Byte]]] =
    send(url, request(url, None)).flatMap(r =>
      reading(r) { r =>
        r.statusCode match
          case 200       => Right(Some(r.body.readNBytes(16 * 1024 * 1024)))
          case 403 | 404 => Right(None)
          case c         => Left(s"$url: HTTP $c")
      }
    )

  def text(url: com.histmersion.util.basemap.BasemapFetch.Source.Url): Either[com.histmersion.util.basemap.JavaRangeHttp.Problem, com.histmersion.util.basemap.JavaRangeHttp.Text] =
    send(url, request(url, None)).flatMap(r =>
      reading(r) { r =>
        if r.statusCode != 200 then Left(s"$url: HTTP ${r.statusCode}")
        else Right(String(r.body.readNBytes(16 * 1024 * 1024), "UTF-8"))
      }
    )

  def bytes(url: com.histmersion.util.basemap.BasemapFetch.Source.Url, offset: com.histmersion.ByteOffset, length: com.histmersion.ByteCount): Either[com.histmersion.util.basemap.JavaRangeHttp.Problem, (com.histmersion.util.basemap.JavaRangeHttp.Bytes, RemoteFile)] =
    ranged(url, offset, length).flatMap((r, total) =>
      reading(r) { r =>
        val want = math.min(offset + length, total) - offset
        val b = r.body.readNBytes(want.toInt)
        if b.length == want then Right((b, meta(r, total)))
        else Left(s"$url sent ${b.length} bytes for a range of $want")
      }
    )

  def download(url: com.histmersion.util.basemap.BasemapFetch.Source.Url, offset: com.histmersion.ByteOffset, length: com.histmersion.ByteCount, to: Reservation): Either[com.histmersion.util.basemap.JavaRangeHttp.Problem, RemoteFile] =
    ranged(url, offset, length).flatMap((r, total) =>
      reading(r) { r =>
        val got = Using.resource(to.output())(out => r.body.transferTo(out))
        if got != length then
          to.discard()
          Left(s"$url sent $got bytes for a range of $length")
        else Right(meta(r, total))
      }
    )

object JavaRangeHttp:
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Retries = Retries.T
  object Retries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UserAgent = UserAgent.T
  object UserAgent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SleepArg1 = com.histmersion.util.basemap.BasemapFetch.Pace.GapMs
  val SleepArg1: _root_.com.histmersion.util.basemap.BasemapFetch.Pace.GapMs.type = _root_.com.histmersion.util.basemap.BasemapFetch.Pace.GapMs

