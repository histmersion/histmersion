package com.histmersion.server.basemap

import com.histmersion.core.{HttpPolicy, Version}

import java.io.InputStream
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path, StandardCopyOption}
import java.time.{Duration, Instant}
import scala.util.Using

/** What a range answer says about the file it came from: its whole size (from `Content-Range`) and
  * its `ETag` — a fetch that resumes compares both, so it never mixes two builds' bytes. */
final case class RemoteFile(size: Long, etag: Option[String])

/** The ONE seam between the base-map fetch and the network: a test answers from a local file and
  * never opens a socket. Failures are `Left(message)`. */
trait RangeHttp:
  /** A small text document (the provider's list of builds). */
  def text(url: String): Either[String, String]
  /** Bytes `[offset, offset + length)` of `url`, in memory (headers and directories). */
  def bytes(url: String, offset: Long, length: Long): Either[String, (Array[Byte], RemoteFile)]
  /** The same, streamed into the file `to` (tile data, megabytes at a time). */
  def download(url: String, offset: Long, length: Long, to: Path): Either[String, RemoteFile]
  /** A whole small file (one map tile): `None` when the server has none (404, or 403 from a bucket). */
  def file(url: String): Either[String, Option[Array[Byte]]] = Left(s"$url: whole files are not read by this client")

object RangeHttp:

  /** Pure: `Content-Range: bytes 0-99/12345` → `(0, 99, Some(12345))`. */
  def contentRange(v: String): Option[(Long, Long, Option[Long])] =
    val R = """(?i)\s*bytes\s+(\d+)-(\d+)/(\d+|\*)\s*""".r
    v match
      case R(a, b, total) => Some((a.toLong, b.toLong, total.toLongOption))
      case _              => None

  /** Pure: is this answer the range asked for? A 200 (the server ignored the range) is refused —
    * reading a 100 GB planet file whole is exactly what this must never do. */
  def check(url: String, code: Int, range: Option[String], offset: Long, length: Long): Either[String, Long] =
    if code == 200 then Left(s"$url ignores range requests (answered 200 to a range) — refused rather than download the whole file")
    else if code != 206 then Left(s"$url: HTTP $code")
    else
      range.flatMap(contentRange) match
        // a range past the end of the file is answered up to its end
        case Some((a, b, Some(total))) if a == offset && b == math.min(offset + length, total) - 1 => Right(total)
        case Some((a, b, total)) => Left(s"$url answered bytes $a-$b/${total.getOrElse("*")} for $offset-${offset + length - 1}")
        case None => Left(s"$url sent no Content-Range")

/** The real client: java.net.http over HTTP/1.1 (one stream per request; the providers are CDNs),
  * following redirects, under [[HttpPolicy]] — the product's User-Agent, a slow-down answer asked again
  * after its `Retry-After` or 1, 2, 4 … s, at most `retries` times. */
final class JavaRangeHttp(timeoutSeconds: Int, retries: Int, userAgent: String = s"Histmersion/${Version.version} (terrain base map)",
    sleep: Long => Unit = ms => Thread.sleep(ms)) extends RangeHttp:

  private val client = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .version(HttpClient.Version.HTTP_1_1)
    .connectTimeout(Duration.ofSeconds(30))
    .build()

  private def request(url: String, range: Option[(Long, Long)]): HttpRequest =
    val b = HttpRequest.newBuilder(URI.create(url)).GET()
      .timeout(Duration.ofSeconds(timeoutSeconds.toLong))
      .header("User-Agent", HttpPolicy.userAgent(userAgent))
    range.foreach((o, l) => b.header("Range", s"bytes=$o-${o + l - 1}"))
    b.build()

  /** Send, asking again on a slow-down answer or a dropped connection. The body is a STREAM, read
    * only once the answer is known to be the one asked for — an answer that is not (a 200 with the
    * whole 100 GB file) is closed unread, which cancels it. */
  private def send(url: String, req: HttpRequest): Either[String, HttpResponse[InputStream]] =
    def attempt(n: Int): Either[String, HttpResponse[InputStream]] =
      scala.util.Try(client.send(req, HttpResponse.BodyHandlers.ofInputStream())) match
        case scala.util.Failure(e: InterruptedException) => throw e
        case scala.util.Failure(e) =>
          if n < retries then { sleep(HttpPolicy.backoffMs(n)); attempt(n + 1) }
          else Left(s"$url unreachable: ${e.getClass.getSimpleName}: ${e.getMessage}")
        case scala.util.Success(resp) =>
          HttpPolicy.decide(resp.statusCode, Option(resp.headers.firstValue("Retry-After").orElse(null)), n, retries, 300, Instant.now()) match
            case HttpPolicy.Step.Wait(ms, why) =>
              resp.body.close()
              scribe.warn(s"basemap: $why — asking $url again in ${ms}ms")
              sleep(ms)
              attempt(n + 1)
            case _ => Right(resp)
    attempt(0)

  private def meta[T](r: HttpResponse[T], total: Long): RemoteFile =
    RemoteFile(total, Option(r.headers.firstValue("ETag").orElse(null)))

  /** The answer's body when `ok` accepts it, closed unread otherwise. */
  private def reading[A](r: HttpResponse[InputStream])(ok: HttpResponse[InputStream] => Either[String, A]): Either[String, A] =
    try ok(r) finally r.body.close()

  private def ranged(url: String, offset: Long, length: Long) =
    send(url, request(url, Some((offset, length)))).flatMap { r =>
      RangeHttp.check(url, r.statusCode, Option(r.headers.firstValue("Content-Range").orElse(null)), offset, length) match
        case Left(e)      => r.body.close(); Left(e)
        case Right(total) => Right((r, total))
    }

  override def file(url: String): Either[String, Option[Array[Byte]]] =
    send(url, request(url, None)).flatMap(r => reading(r) { r =>
      r.statusCode match
        case 200       => Right(Some(r.body.readNBytes(16 * 1024 * 1024)))
        case 403 | 404 => Right(None)
        case c         => Left(s"$url: HTTP $c")
    })

  def text(url: String): Either[String, String] =
    send(url, request(url, None)).flatMap(r => reading(r) { r =>
      if r.statusCode != 200 then Left(s"$url: HTTP ${r.statusCode}")
      else Right(String(r.body.readNBytes(16 * 1024 * 1024), "UTF-8"))
    })

  def bytes(url: String, offset: Long, length: Long): Either[String, (Array[Byte], RemoteFile)] =
    ranged(url, offset, length).flatMap((r, total) => reading(r) { r =>
      val want = math.min(offset + length, total) - offset
      val b    = r.body.readNBytes(want.toInt)
      if b.length == want then Right((b, meta(r, total))) else Left(s"$url sent ${b.length} bytes for a range of $want")
    })

  def download(url: String, offset: Long, length: Long, to: Path): Either[String, RemoteFile] =
    val part = to.resolveSibling(to.getFileName.toString + ".part")
    ranged(url, offset, length).flatMap((r, total) => reading(r) { r =>
      val got = Using.resource(Files.newOutputStream(part))(out => r.body.transferTo(out))
      if got != length then
        val _ = Files.deleteIfExists(part)
        Left(s"$url sent $got bytes for a range of $length")
      else
        val _ = Files.move(part, to, StandardCopyOption.REPLACE_EXISTING)
        Right(meta(r, total))
    })
