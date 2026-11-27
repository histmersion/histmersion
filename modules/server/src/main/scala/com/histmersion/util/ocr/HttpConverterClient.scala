package com.histmersion
package util.ocr
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.util.storage.Blob

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import com.histmersion.api.media.AssetLinks
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** Multipart `POST /convert` against the sidecar. Stateless and thread-safe: one `HttpClient` is shared by the worker
  * pool, and every request carries its own body.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
@atBoundary(BoundaryKind.Http, "sends a PDF to the OCR converter and reads its answer")
final class HttpConverterClient(cfg: => ConverterSettings, http: HttpClient)
    extends ConverterClient: // by name: a reload may move it
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def base = cfg.url.value.trim.stripSuffix("/")

  private val ConversionTimeoutHint =
    "raise converter.seconds-per-page or converter.timeout-seconds for documents like this one"

  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def health: Either[Problem, ConverterClient.HealthSummary] =
    send(
      HttpRequest.newBuilder(URI.create(s"$base/health")).timeout(Duration.ofSeconds(5)).GET().build(),
      timeoutHint = None
    ).map(_ => ConverterClient.HealthSummary("ok")).left.map((text => Problem.Unclassified(Legacy.Text(text))))

  /** The document's page count from the sidecar's `/inspect` — cheap, no conversion. `None` when it cannot tell (an
    * older sidecar, an unreadable file): the conversion then gets the plain timeout and reports the real error itself.
    */
  def pages(file: Blob, filename: ConverterClient.Filename): Option[ConverterClient.PageCount] =
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
      json <- RawJson.parse(text)
      n <- json.cursor.get[Int]("pages").left.map(_ => "no page count")
    yield ConverterClient.PageCount(
      ConverterClient.PageCount.Pages(n),
      ConverterClient.PageCount.Scanned(json.cursor.get[Int]("scanned").getOrElse(0))
    )).toOption

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def convert(
      file: Blob,
      filename: ConverterClient.Filename,
      quality: ConverterClient.HighQuality
  ): Either[Problem, ConvertedDoc] =
    val boundary = "histmersion-" + java.util.UUID.randomUUID().toString.replace("-", "")
    val allowed = ConverterClient.timeoutFor(cfg, pages(file, filename), quality)
    (for
      body <- multipart(boundary, file, filename, quality.value)
      req = HttpRequest
        .newBuilder(URI.create(s"$base/convert"))
        .timeout(allowed)
        .header("Content-Type", s"multipart/form-data; boundary=$boundary")
        .POST(body)
        .build()
      text <- send(req)
      doc <- RawJson
        .decode[ConvertedDoc](text)
        .left
        .map(e => s"converter at $base returned unreadable JSON: $e")
    yield doc).left.map((text => Problem.Unclassified(Legacy.Text(text))))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  override def thumbnail(
      bytes: ConverterClient.DocumentBytes,
      filename: ConverterClient.Filename,
      maxPx: Pixels
  ): Either[Problem, ConverterClient.Thumb] =
    val boundary = "histmersion-" + java.util.UUID.randomUUID().toString.replace("-", "")
    def ascii(s: String) = s.getBytes("UTF-8")
    val body = java.io.ByteArrayOutputStream()
    body.write(
      ascii(
        s"--$boundary\r\nContent-Disposition: form-data; name=\"file\"; " +
          s"filename=\"${AssetLinks.safeName(filename.value)}\"\r\nContent-Type: application/octet-stream\r\n\r\n"
      )
    )
    body.write(bytes.value)
    body.write(ascii(s"\r\n--$boundary\r\nContent-Disposition: form-data; name=\"max_px\"\r\n\r\n$maxPx\r\n"))
    body.write(ascii(s"--$boundary--\r\n"))
    val req = HttpRequest
      .newBuilder(URI.create(s"$base/thumbnail"))
      .timeout(Duration.ofSeconds(60))
      .header("Content-Type", s"multipart/form-data; boundary=$boundary")
      .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray))
      .build()
    (for
      text <- send(req, timeoutHint = None)
      json <- RawJson.parse(text).left.map(e => s"converter at $base returned unreadable JSON: $e")
      c = json.cursor
      b64 <- c.get[String]("base64").left.map(_ => s"converter at $base returned no thumbnail")
      raw <- scala.util.Try(java.util.Base64.getDecoder.decode(b64)).toEither.left.map(_.getMessage)
    yield ConverterClient.Thumb(
      ConverterClient.Thumb.Bytes(raw),
      ConverterClient.Thumb.Mime(c.get[String]("mime").getOrElse("image/jpeg")),
      ConverterClient.Thumb.Width(c.get[Int]("width").getOrElse(0)),
      ConverterClient.Thumb.Height(c.get[Int]("height").getOrElse(0))
    )).left.map((text => Problem.Unclassified(Legacy.Text(text))))

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
    * STREAMED: the stored file goes from disk to socket between the prologue and the epilogue, with its length given
    * (so the request is not chunked). Reading it whole first held a 250 MB scan in the heap twice to send it once (and
    * the accepted upload limit is exactly 256 MB).
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def multipart(
      boundary: String,
      file: Blob,
      filename: ConverterClient.Filename,
      quality: Boolean
  ): Either[String, HttpRequest.BodyPublisher] =
    def ascii(s: String) = s.getBytes("UTF-8")
    def field(name: String, value: String) =
      ascii(s"--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
    val head = ascii(
      s"--$boundary\r\nContent-Disposition: form-data; name=\"file\"; " +
        s"filename=\"${AssetLinks.safeName(filename.value)}\"\r\nContent-Type: application/octet-stream\r\n\r\n"
    )
    val tail = java.io.ByteArrayOutputStream()
    tail.write(ascii("\r\n"))
    tail.write(field("force_quality", quality.toString))
    tail.write(field("dpi", cfg.dpi.value.toString))
    tail.write(field("max_image_px", cfg.maxImage.value.toString))
    tail.write(ascii(s"--$boundary--\r\n"))
    scala.util
      .Try {
        val length = file.size.value
        if length == 0 then HttpRequest.BodyPublishers.noBody()
        else
          HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(() => file.open()), length)
      }
      .toEither
      .left
      .map(e => s"cannot read the upload at ${file.where}: ${e.getMessage}")
      .map(fileBody =>
        HttpRequest.BodyPublishers.concat(
          HttpRequest.BodyPublishers.ofByteArray(head),
          fileBody,
          HttpRequest.BodyPublishers.ofByteArray(tail.toByteArray)
        )
      )
