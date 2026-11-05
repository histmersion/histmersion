package com.histmersion.util.ocr

import com.histmersion.util.storage.Blob

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import com.histmersion.api.media.AssetLinks
import com.histmersion.api.ocr.ConvertedDoc

/** Multipart `POST /convert` against the sidecar. Stateless and thread-safe: one `HttpClient` is shared by the worker
  * pool, and every request carries its own body.
  */
final class HttpConverterClient(cfg: => ConverterSettings, http: HttpClient)
    extends ConverterClient: // by name: a reload may move it
  private def base = cfg.url.value.trim.stripSuffix("/")

  private val ConversionTimeoutHint =
    "raise converter.seconds-per-page or converter.timeout-seconds for documents like this one"

  def health: Either[com.histmersion.util.ocr.HttpConverterClient.HealthResultPart1, com.histmersion.util.ocr.HttpConverterClient.HealthResultPart2] =
    send(
      HttpRequest.newBuilder(URI.create(s"$base/health")).timeout(Duration.ofSeconds(5)).GET().build(),
      timeoutHint = None
    ).map(_ => "ok")

  /** The document's page count from the sidecar's `/inspect` — cheap, no conversion. `None` when it cannot tell (an
    * older sidecar, an unreadable file): the conversion then gets the plain timeout and reports the real error itself.
    */
  def pages(file: Blob, filename: com.histmersion.util.ocr.HttpConverterClient.PagesFilename): Option[ConverterClient.PageCount] =
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

  def convert(file: Blob, filename: com.histmersion.util.ocr.HttpConverterClient.ConvertFilename, quality: com.histmersion.util.ocr.HttpConverterClient.ConvertQuality): Either[com.histmersion.util.ocr.HttpConverterClient.ConvertResultPart1, ConvertedDoc] =
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
    def ascii(s: com.histmersion.util.ocr.HttpConverterClient.ThumbnailAsciiS) = s.getBytes("UTF-8")
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
      timeoutHint: Option[com.histmersion.util.ocr.HttpConverterClient.SendTimeoutHint] = Some(ConversionTimeoutHint)
  ): Either[com.histmersion.util.ocr.HttpConverterClient.SendResultPart1, com.histmersion.util.ocr.HttpConverterClient.SendResultPart2] =
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
  private def multipart(
      boundary: com.histmersion.util.ocr.HttpConverterClient.MultipartBoundary,
      file: Blob,
      filename: com.histmersion.util.ocr.HttpConverterClient.MultipartFilename,
      quality: com.histmersion.util.ocr.HttpConverterClient.MultipartQuality
  ): Either[com.histmersion.util.ocr.HttpConverterClient.MultipartResultPart1, HttpRequest.BodyPublisher] =
    def ascii(s: com.histmersion.util.ocr.HttpConverterClient.MultipartAsciiS) = s.getBytes("UTF-8")
    def field(name: com.histmersion.util.ocr.HttpConverterClient.MultipartFieldName, value: com.histmersion.util.ocr.HttpConverterClient.MultipartFieldValue) =
      ascii(s"--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
    val head = ascii(
      s"--$boundary\r\nContent-Disposition: form-data; name=\"file\"; " +
        s"filename=\"${AssetLinks.safeName(filename)}\"\r\nContent-Type: application/octet-stream\r\n\r\n"
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

object HttpConverterClient:
  type ThumbnailAsciiS = ThumbnailAsciiS.T
  object ThumbnailAsciiS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MultipartAsciiS = MultipartAsciiS.T
  object MultipartAsciiS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MultipartFieldName = MultipartFieldName.T
  object MultipartFieldName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MultipartFieldValue = MultipartFieldValue.T
  object MultipartFieldValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type HealthResultPart1 = HealthResultPart1.T
  object HealthResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HealthResultPart2 = HealthResultPart2.T
  object HealthResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PagesFilename = PagesFilename.T
  object PagesFilename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConvertResultPart1 = ConvertResultPart1.T
  object ConvertResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConvertFilename = ConvertFilename.T
  object ConvertFilename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConvertQuality = ConvertQuality.T
  object ConvertQuality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendResultPart1 = SendResultPart1.T
  object SendResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendResultPart2 = SendResultPart2.T
  object SendResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendTimeoutHint = SendTimeoutHint.T
  object SendTimeoutHint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MultipartResultPart1 = MultipartResultPart1.T
  object MultipartResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MultipartBoundary = MultipartBoundary.T
  object MultipartBoundary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MultipartFilename = MultipartFilename.T
  object MultipartFilename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MultipartQuality = MultipartQuality.T
  object MultipartQuality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
