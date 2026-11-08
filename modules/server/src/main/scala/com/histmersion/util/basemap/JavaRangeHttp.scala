package com.histmersion
package util.basemap

import com.histmersion.util.storage.Reservation

import java.io.InputStream
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.{Duration, Instant}
import scala.util.Using
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
    sleep: RetryWait.Ms => Unit = ms => Thread.sleep(ms.value)
)(using clock: java.time.Clock) extends RangeHttp:

  private def request(url: RangeHttp.Url, range: Option[(ByteOffset, ByteCount)]): HttpRequest =
    val b = HttpRequest
      .newBuilder(URI.create(url.value))
      .GET()
      .timeout(Duration.ofSeconds(timeoutSeconds.value.toLong))
      .header("User-Agent", userAgent.value)
    range.foreach((o, l) => b.header("Range", s"bytes=${o.value}-${o.value + l.value - 1}"))
    b.build()

  /** Send, asking again on a slow-down answer or a dropped connection. The body is a STREAM, read only once the answer
    * is known to be the one asked for — an answer that is not (a 200 with the whole 100 GB file) is closed unread,
    * which cancels it.
    */
  private def send(url: RangeHttp.Url, req: HttpRequest): Either[RangeHttp.Problem, HttpResponse[InputStream]] =
    def attempt(n: Int): Either[RangeHttp.Problem, HttpResponse[InputStream]] =
      scala.util.Try(client.send(req, HttpResponse.BodyHandlers.ofInputStream())) match
        case scala.util.Failure(e: InterruptedException) => throw e
        case scala.util.Failure(e)                       =>
          if n < retries.value then { sleep(policy.backoffMs(n)); attempt(n + 1) }
          else Left(RangeHttp.Problem(s"$url unreachable: ${e.getClass.getSimpleName}: ${e.getMessage}"))
        case scala.util.Success(resp) =>
          policy.slowDown(
            RangeHttp.StatusCode(resp.statusCode),
            Option(resp.headers.firstValue("Retry-After").orElse(null)).map(RetryPolicy.RetryAfterHeader(_)),
            n,
            RetryPolicy.MaxRetries(retries.value),
            RetryPolicy.RetryAfterCap(300),
            Instant.now(clock)
          ) match
            case Some(RetryWait(ms, why)) =>
              resp.body.close()
              scribe.warn(s"basemap: $why — asking $url again in ${ms}ms")
              sleep(ms)
              attempt(n + 1)
            case None => Right(resp)
    attempt(0)

  private def meta[T](r: HttpResponse[T], total: ByteCount): RemoteFile =
    RemoteFile(
      RemoteFile.Size(total.value),
      Option(r.headers.firstValue("ETag").orElse(null)).map(RemoteFile.Etag(_))
    )

  /** The answer's body when `ok` accepts it, closed unread otherwise. */
  private def reading[A](r: HttpResponse[InputStream])(
      ok: HttpResponse[InputStream] => Either[RangeHttp.Problem, A]
  ): Either[RangeHttp.Problem, A] =
    try ok(r)
    finally r.body.close()

  private def ranged(url: RangeHttp.Url, offset: ByteOffset, length: ByteCount) =
    send(url, request(url, Some((offset, length)))).flatMap { r =>
      RangeHttp.check(
        url,
        RangeHttp.StatusCode(r.statusCode),
        Option(r.headers.firstValue("Content-Range").orElse(null)).map(RangeHttp.ContentRangeHeader(_)),
        offset,
        length
      ) match
        case Left(e)      => r.body.close(); Left(e)
        case Right(total) => Right((r, total))
    }

  override def file(url: RangeHttp.Url): Either[RangeHttp.Problem, Option[RangeHttp.ResponseBytes]] =
    send(url, request(url, None)).flatMap(r =>
      reading(r) { r =>
        r.statusCode match
          case 200       => Right(Some(RangeHttp.ResponseBytes(r.body.readNBytes(16 * 1024 * 1024))))
          case 403 | 404 => Right(None)
          case c         => Left(RangeHttp.Problem(s"$url: HTTP $c"))
      }
    )

  def text(url: RangeHttp.Url): Either[RangeHttp.Problem, RangeHttp.ResponseText] =
    send(url, request(url, None)).flatMap(r =>
      reading(r) { r =>
        if r.statusCode != 200 then Left(RangeHttp.Problem(s"$url: HTTP ${r.statusCode}"))
        else Right(RangeHttp.ResponseText(String(r.body.readNBytes(16 * 1024 * 1024), "UTF-8")))
      }
    )

  def bytes(url: RangeHttp.Url, offset: ByteOffset, length: ByteCount): Either[RangeHttp.Problem, (RangeHttp.ResponseBytes, RemoteFile)] =
    ranged(url, offset, length).flatMap((r, total) =>
      reading(r) { r =>
        val want = math.min(offset.value + length.value, total.value) - offset.value
        val b = r.body.readNBytes(want.toInt)
        if b.length == want then Right((RangeHttp.ResponseBytes(b), meta(r, total)))
        else Left(RangeHttp.Problem(s"$url sent ${b.length} bytes for a range of $want"))
      }
    )

  def download(url: RangeHttp.Url, offset: ByteOffset, length: ByteCount, to: Reservation): Either[RangeHttp.Problem, RemoteFile] =
    ranged(url, offset, length).flatMap((r, total) =>
      reading(r) { r =>
        val got = Using.resource(to.output())(out => r.body.transferTo(out))
        if got != length.value then
          to.discard()
          Left(RangeHttp.Problem(s"$url sent $got bytes for a range of $length"))
        else Right(meta(r, total))
      }
    )

object JavaRangeHttp:
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends RefinedType[Int, Pure]
  type Retries = Retries.T
  object Retries extends RefinedType[Int, Pure]
  type UserAgent = UserAgent.T
  object UserAgent extends RefinedType[String, Pure]
