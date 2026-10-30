package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}
import java.time.Duration

/** Multipart `POST /convert` against the sidecar. Stateless and thread-safe: one `HttpClient` is shared by the worker
  * pool, and every request carries its own body.
  */
final class HttpConverterClient(cfg: => ConverterConfig) extends ConverterClient: // by name: a reload may move it
  // HTTP/1.1 explicitly: java.net.http defaults to HTTP/2 and offers an h2c upgrade
  // (`Connection: Upgrade, HTTP2-Settings` + `Upgrade: h2c`), on which uvicorn DISCARDS the request
  // body — the sidecar then answers 422 "field required: file" for a perfectly well-formed upload.
  private val http = HttpClient
    .newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .connectTimeout(Duration.ofSeconds(10))
    .build()
  private def base = cfg.url.trim.stripSuffix("/")

  private val ConversionTimeoutHint =
    "raise converter.seconds-per-page or converter.timeout-seconds for documents like this one"

  def health: Either[String, String] =
    send(
      HttpRequest.newBuilder(URI.create(s"$base/health")).timeout(Duration.ofSeconds(5)).GET().build(),
      timeoutHint = None
    ).map(_ => "ok")

  /** The document's page count from the sidecar's `/inspect` — cheap, no conversion. `None` when it cannot tell (an
    * older sidecar, an unreadable file): the conversion then gets the plain timeout and reports the real error itself.
    */
  def pages(file: Path, filename: String): Option[ConverterClient.PageCount] =
    val boundary = "histmersion-" + java.util.UUID.randomUUID().toString.replace("-", "")
    (for
      body <- multipart(boundary, file, filename, quality = false)
      text <- send(
        HttpRequest
          .newBuilder(URI.create(s"$base/inspect"))
          .timeout(Duration.ofSeconds(60))
          .header("Content-Type", s"multipart/form-data; boundary=$boundary")
          .POST(body)
          .build()
      )
      json <- io.circe.parser.parse(text).left.map(_.getMessage)
      n <- json.hcursor.get[Int]("pages").left.map(_ => "no page count")
    yield ConverterClient.PageCount(n, json.hcursor.get[Int]("scanned").getOrElse(0))).toOption

  def convert(file: Path, filename: String, quality: Boolean): Either[String, ConvertedDoc] =
    val boundary = "histmersion-" + java.util.UUID.randomUUID().toString.replace("-", "")
    val allowed = ConverterClient.timeoutFor(cfg, pages(file, filename), quality)
    for
      body <- multipart(boundary, file, filename, quality)
      req = HttpRequest
        .newBuilder(URI.create(s"$base/convert"))
        .timeout(allowed)
        .header("Content-Type", s"multipart/form-data; boundary=$boundary")
        .POST(body)
        .build()
      text <- send(req)
      doc <- io.circe.parser
        .decode[ConvertedDoc](text)
        .left
        .map(e => s"converter at $base returned unreadable JSON: ${e.getMessage}")
    yield doc

  override def thumbnail(bytes: Array[Byte], filename: String, maxPx: Int): Either[String, ConverterClient.Thumb] =
    val boundary = "histmersion-" + java.util.UUID.randomUUID().toString.replace("-", "")
    def ascii(s: String) = s.getBytes("UTF-8")
    val body = java.io.ByteArrayOutputStream()
    body.write(
      ascii(
        s"--$boundary\r\nContent-Disposition: form-data; name=\"file\"; " +
          s"filename=\"${AssetLinks.safeName(filename)}\"\r\nContent-Type: application/octet-stream\r\n\r\n"
      )
    )
    body.write(bytes)
    body.write(ascii(s"\r\n--$boundary\r\nContent-Disposition: form-data; name=\"max_px\"\r\n\r\n$maxPx\r\n"))
    body.write(ascii(s"--$boundary--\r\n"))
    val req = HttpRequest
      .newBuilder(URI.create(s"$base/thumbnail"))
      .timeout(Duration.ofSeconds(60))
      .header("Content-Type", s"multipart/form-data; boundary=$boundary")
      .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray))
      .build()
    for
      text <- send(req, timeoutHint = None)
      json <- io.circe.parser.parse(text).left.map(e => s"converter at $base returned unreadable JSON: ${e.getMessage}")
      c = json.hcursor
      b64 <- c.get[String]("base64").left.map(_ => s"converter at $base returned no thumbnail")
      raw <- scala.util.Try(java.util.Base64.getDecoder.decode(b64)).toEither.left.map(_.getMessage)
    yield ConverterClient.Thumb(
      raw,
      c.get[String]("mime").getOrElse("image/jpeg"),
      c.get[Int]("width").getOrElse(0),
      c.get[Int]("height").getOrElse(0)
    )

  /** `timeoutHint` is what to do about a timeout. Only a CONVERSION timing out is about the per-page budget; the health
    * probe timing out is not (#692), so it carries none.
    */
  private def send(
      req: HttpRequest,
      timeoutHint: Option[String] = Some(ConversionTimeoutHint)
  ): Either[String, String] =
    scala.util
      .Try(http.send(req, HttpResponse.BodyHandlers.ofString()))
      .toEither
      .left
      .map {
        case _: java.net.http.HttpTimeoutException =>
          val waited = req.timeout().map(d => s"${d.toSeconds} s").orElse("its time limit")
          s"converter at $base did not finish within $waited" + timeoutHint.fold("")(h => s" ($h)")
        case e => s"converter at $base is unreachable (${e.getClass.getSimpleName}: ${e.getMessage})"
      }
      .flatMap { r =>
        if r.statusCode() / 100 == 2 then Right(r.body())
        else Left(s"converter at $base answered ${r.statusCode()}: ${r.body().take(400)}")
      }

  /** RFC 7578 body: the file part plus the scalar form fields the sidecar expects.
    *
    * STREAMED. It used to `Files.readAllBytes` the upload and then copy it into a `ByteArrayOutputStream`, i.e. hold a
    * 250 MB scan in the heap TWICE to send it once (and the accepted upload limit is exactly 256 MB).
    * `BodyPublishers.concat` splices the prologue, an `ofFile` publisher and the epilogue, so the file goes from disk
    * to socket.
    */
  private def multipart(
      boundary: String,
      file: Path,
      filename: String,
      quality: Boolean
  ): Either[String, HttpRequest.BodyPublisher] =
    def ascii(s: String) = s.getBytes("UTF-8")
    def field(name: String, value: String) =
      ascii(s"--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
    val head = ascii(
      s"--$boundary\r\nContent-Disposition: form-data; name=\"file\"; " +
        s"filename=\"${AssetLinks.safeName(filename)}\"\r\nContent-Type: application/octet-stream\r\n\r\n"
    )
    val tail = java.io.ByteArrayOutputStream()
    tail.write(ascii("\r\n"))
    tail.write(field("force_quality", quality.toString))
    tail.write(field("dpi", cfg.dpi.toString))
    tail.write(field("max_image_px", cfg.maxImagePx.toString))
    tail.write(ascii(s"--$boundary--\r\n"))
    scala.util
      .Try(HttpRequest.BodyPublishers.ofFile(file))
      .toEither
      .left
      .map(e => s"cannot read the upload at $file: ${e.getMessage}")
      .map(fileBody =>
        HttpRequest.BodyPublishers.concat(
          HttpRequest.BodyPublishers.ofByteArray(head),
          fileBody,
          HttpRequest.BodyPublishers.ofByteArray(tail.toByteArray)
        )
      )
