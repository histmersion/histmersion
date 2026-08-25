package histdb.server

import histdb.core.*

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}
import java.time.Duration

/** The seam between the server and the Python OCR sidecar (plan §1.8).
  *
  * A trait, so an HTTP test can ingest a PDF through a FAKE converter and still exercise the whole
  * job → assets → rendering path without a container. The real implementation speaks the sidecar's
  * multipart `POST /convert` over `java.net.http` — no extra dependency, and nothing for
  * native-image to reflect over. */
trait ConverterClient:
  /** Convert one document. `Left` carries a message that names the converter URL, because "the OCR
    * job failed" without saying where it tried to go is useless in a log. */
  def convert(file: Path, filename: String, quality: Boolean): Either[String, ConvertedDoc]

  /** Sidecar liveness for `/api/health`. */
  def health: Either[String, String]

object ConverterClient:

  def fromConfig(cfg: ConverterConfig): ConverterClient = HttpConverterClient(cfg)

/** Multipart `POST /convert` against the sidecar. Stateless and thread-safe: one `HttpClient` is
  * shared by the worker pool, and every request carries its own body. */
final class HttpConverterClient(cfg: ConverterConfig) extends ConverterClient:
  private val timeout = Duration.ofSeconds(math.max(1, cfg.timeoutSeconds).toLong)
  // HTTP/1.1 explicitly: java.net.http defaults to HTTP/2 and offers an h2c upgrade
  // (`Connection: Upgrade, HTTP2-Settings` + `Upgrade: h2c`), on which uvicorn DISCARDS the request
  // body — the sidecar then answers 422 "field required: file" for a perfectly well-formed upload.
  private val http = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .connectTimeout(Duration.ofSeconds(10))
    .build()
  private val base    = cfg.url.trim.stripSuffix("/")

  def health: Either[String, String] =
    send(HttpRequest.newBuilder(URI.create(s"$base/health")).timeout(Duration.ofSeconds(5)).GET().build())
      .map(_ => "ok")

  def convert(file: Path, filename: String, quality: Boolean): Either[String, ConvertedDoc] =
    val boundary = "histdb-" + java.util.UUID.randomUUID().toString.replace("-", "")
    for
      body <- multipart(boundary, file, filename, quality)
      req   = HttpRequest.newBuilder(URI.create(s"$base/convert"))
                .timeout(timeout)
                .header("Content-Type", s"multipart/form-data; boundary=$boundary")
                .POST(body)
                .build()
      text <- send(req)
      doc  <- io.circe.parser.decode[ConvertedDoc](text).left
                .map(e => s"converter at $base returned unreadable JSON: ${e.getMessage}")
    yield doc

  private def send(req: HttpRequest): Either[String, String] =
    scala.util.Try(http.send(req, HttpResponse.BodyHandlers.ofString())).toEither.left
      .map(e => s"converter at $base is unreachable (${e.getClass.getSimpleName}: ${e.getMessage})")
      .flatMap { r =>
        if r.statusCode() / 100 == 2 then Right(r.body())
        else Left(s"converter at $base answered ${r.statusCode()}: ${r.body().take(400)}")
      }

  /** RFC 7578 body: the file part plus the scalar form fields the sidecar expects.
    *
    * STREAMED. It used to `Files.readAllBytes` the upload and then copy it into a
    * `ByteArrayOutputStream`, i.e. hold a 250 MB scan in the heap TWICE to send it once (and the
    * accepted upload limit is exactly 256 MB). `BodyPublishers.concat` splices the prologue, an
    * `ofFile` publisher and the epilogue, so the file goes from disk to socket. */
  private def multipart(
      boundary: String, file: Path, filename: String, quality: Boolean
  ): Either[String, HttpRequest.BodyPublisher] =
    def ascii(s: String) = s.getBytes("UTF-8")
    def field(name: String, value: String) =
      ascii(s"--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
    val head = ascii(s"--$boundary\r\nContent-Disposition: form-data; name=\"file\"; " +
      s"filename=\"${AssetLinks.safeName(filename)}\"\r\nContent-Type: application/octet-stream\r\n\r\n")
    val tail = java.io.ByteArrayOutputStream()
    tail.write(ascii("\r\n"))
    tail.write(field("force_quality", quality.toString))
    tail.write(field("dpi", cfg.dpi.toString))
    tail.write(field("max_image_px", cfg.maxImagePx.toString))
    tail.write(ascii(s"--$boundary--\r\n"))
    scala.util.Try(HttpRequest.BodyPublishers.ofFile(file)).toEither.left
      .map(e => s"cannot read the upload at $file: ${e.getMessage}")
      .map(fileBody =>
        HttpRequest.BodyPublishers.concat(
          HttpRequest.BodyPublishers.ofByteArray(head),
          fileBody,
          HttpRequest.BodyPublishers.ofByteArray(tail.toByteArray)))
