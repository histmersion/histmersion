package histdb.server

import histdb.core.*
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Files

/** Uploads over a REUSED connection (plan §1.8), pinned after a real bug found building the stream.
  *
  * java.net.http's default client is HTTP/2-capable, and against the server's netty backend it reuses
  * a connection in a way that makes the SECOND body-carrying request arrive with an EMPTY body — so
  * a perfectly good PDF is rejected as "the upload is empty", seemingly at random. Pinning HTTP/1.1
  * fixes it, which is why [[histdb.cli.Client]] and [[HttpConverterClient]] both do so.
  *
  * This suite is the regression pin for the contract the CLI depends on: one HTTP/1.1 client, several
  * uploads, including a degenerate zero-byte one, and every response is about the request it belongs
  * to. */
class ZeroLengthUploadSuite extends munit.FunSuite:
  private val testDb                = sys.env.get("HISTDB_TEST_DB_URL")
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout         = scala.concurrent.duration.Duration(60, "s")

  /** Exactly what the CLI builds. */
  private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

  private def upload(url: String, file: java.nio.file.Path): (Int, String) =
    val r = http.send(
      HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofFile(file)).build(),
      HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  test("several uploads over one reused connection each get their own body"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get),
      converter = base.converter.copy(spoolDir = Files.createTempDirectory("histdb-spool0").toString))
    supervised {
      val b     = Main.server(cfg, "127.0.0.1", 0, NoConverter).start()
      val url   = s"http://127.0.0.1:${b.port}/api/ingest"
      val empty = Files.createTempFile("histdb-zero-", ".pdf")
      val one   = Files.createTempFile("histdb-one-", ".pdf")
      val two   = Files.createTempFile("histdb-two-", ".pdf")
      Files.write(one, "%PDF-1.4 the first real document".getBytes("UTF-8"))
      Files.write(two, "%PDF-1.4 a different document entirely".getBytes("UTF-8"))

      val (s1, b1) = upload(s"$url?citekey=zz_reuse_1&force=true", one)
      assertEquals(s1, 202, b1)
      val sha1 = """"sha256":"([^"]+)"""".r.findFirstMatchIn(b1).map(_.group(1)).get

      // a degenerate upload in the middle: it must be refused, and must not poison what follows
      val (se, be) = upload(s"$url?citekey=zz_reuse_empty&force=true", empty)
      assertEquals(se, 400, be)
      assert(be.contains("empty"), be)

      val (s2, b2) = upload(s"$url?citekey=zz_reuse_2&force=true", two)
      assertEquals(s2, 202, b2)
      val sha2 = """"sha256":"([^"]+)"""".r.findFirstMatchIn(b2).map(_.group(1)).get
      assertNotEquals(sha1, sha2, "the second upload must be digested from ITS OWN body")

      Files.deleteIfExists(empty); Files.deleteIfExists(one); Files.deleteIfExists(two)
      b.stop()
    }

  /** No sidecar is needed: the jobs never get to run in this suite. */
  private object NoConverter extends ConverterClient:
    def health = Right("ok")
    def convert(file: java.nio.file.Path, filename: String, quality: Boolean) = Left("not used")
