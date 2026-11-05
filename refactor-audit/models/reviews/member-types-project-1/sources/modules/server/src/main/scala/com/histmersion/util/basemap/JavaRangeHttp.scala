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
    timeoutSeconds: Int,
    retries: Int,
    userAgent: String,
    policy: RetryPolicy,
    sleep: Long => Unit = ms => Thread.sleep(ms)
)(using clock: java.time.Clock) extends RangeHttp:

  private def request(url: com.histmersion.util.basemap.JavaRangeHttp.RequestUrl, range: Option[(com.histmersion.util.basemap.JavaRangeHttp.RequestRangePart1, com.histmersion.util.basemap.JavaRangeHttp.RequestRangePart2)]): HttpRequest =
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
  private def send(url: com.histmersion.util.basemap.JavaRangeHttp.SendUrl, req: HttpRequest): Either[com.histmersion.util.basemap.JavaRangeHttp.SendResultPart1, HttpResponse[InputStream]] =
    def attempt(n: Int): Either[String, HttpResponse[InputStream]] =
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

  private def meta[T](r: HttpResponse[T], total: com.histmersion.util.basemap.JavaRangeHttp.MetaTotal): RemoteFile =
    RemoteFile(total, Option(r.headers.firstValue("ETag").orElse(null)))

  /** The answer's body when `ok` accepts it, closed unread otherwise. */
  private def reading[A](r: HttpResponse[InputStream])(
      ok: HttpResponse[InputStream] => Either[com.histmersion.util.basemap.JavaRangeHttp.ReadingOkResultPart1, A]
  ): Either[com.histmersion.util.basemap.JavaRangeHttp.ReadingResultPart1, A] =
    try ok(r)
    finally r.body.close()

  private def ranged(url: com.histmersion.util.basemap.JavaRangeHttp.RangedUrl, offset: com.histmersion.util.basemap.JavaRangeHttp.RangedOffset, length: com.histmersion.util.basemap.JavaRangeHttp.RangedLength) =
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

  def text(url: com.histmersion.util.basemap.JavaRangeHttp.TextUrl): Either[com.histmersion.util.basemap.JavaRangeHttp.TextResultPart1, com.histmersion.util.basemap.JavaRangeHttp.TextResultPart2] =
    send(url, request(url, None)).flatMap(r =>
      reading(r) { r =>
        if r.statusCode != 200 then Left(s"$url: HTTP ${r.statusCode}")
        else Right(String(r.body.readNBytes(16 * 1024 * 1024), "UTF-8"))
      }
    )

  def bytes(url: com.histmersion.util.basemap.JavaRangeHttp.BytesUrl, offset: com.histmersion.util.basemap.JavaRangeHttp.BytesOffset, length: com.histmersion.util.basemap.JavaRangeHttp.BytesLength): Either[com.histmersion.util.basemap.JavaRangeHttp.BytesResultPart1, (Array[Byte], RemoteFile)] =
    ranged(url, offset, length).flatMap((r, total) =>
      reading(r) { r =>
        val want = math.min(offset + length, total) - offset
        val b = r.body.readNBytes(want.toInt)
        if b.length == want then Right((b, meta(r, total)))
        else Left(s"$url sent ${b.length} bytes for a range of $want")
      }
    )

  def download(url: com.histmersion.util.basemap.JavaRangeHttp.DownloadUrl, offset: com.histmersion.util.basemap.JavaRangeHttp.DownloadOffset, length: com.histmersion.util.basemap.JavaRangeHttp.DownloadLength, to: Reservation): Either[com.histmersion.util.basemap.JavaRangeHttp.DownloadResultPart1, RemoteFile] =
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
  type RequestUrl = RequestUrl.T
  object RequestUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestRangePart1 = RequestRangePart1.T
  object RequestRangePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestRangePart2 = RequestRangePart2.T
  object RequestRangePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendResultPart1 = SendResultPart1.T
  object SendResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendUrl = SendUrl.T
  object SendUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaTotal = MetaTotal.T
  object MetaTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadingResultPart1 = ReadingResultPart1.T
  object ReadingResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadingOkResultPart1 = ReadingOkResultPart1.T
  object ReadingOkResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangedUrl = RangedUrl.T
  object RangedUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangedOffset = RangedOffset.T
  object RangedOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangedLength = RangedLength.T
  object RangedLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResultPart1 = TextResultPart1.T
  object TextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResultPart2 = TextResultPart2.T
  object TextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextUrl = TextUrl.T
  object TextUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesResultPart1 = BytesResultPart1.T
  object BytesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesUrl = BytesUrl.T
  object BytesUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesOffset = BytesOffset.T
  object BytesOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesLength = BytesLength.T
  object BytesLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadResultPart1 = DownloadResultPart1.T
  object DownloadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadUrl = DownloadUrl.T
  object DownloadUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadOffset = DownloadOffset.T
  object DownloadOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadLength = DownloadLength.T
  object DownloadLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
