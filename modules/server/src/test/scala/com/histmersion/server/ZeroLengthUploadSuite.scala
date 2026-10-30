package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}

/** Uploads over a REUSED connection (plan §1.8) — the SAFETY property, pinned after a real bug.
  *
  * `java.net.http` intermittently loses the body of a request sent on a connection it has just reused, and the server
  * then answers a perfectly good PDF with 400 "the upload is empty". It is a race below `fileBody` (curl, on the same
  * keep-alive connection, is always correct), so it is not ours to fix — but it IS ours to bound, because
  * `POST /api/ingest` is exactly the kind of endpoint that meets it.
  *
  * What must never happen is the dangerous outcome: a 202 that stored SOMEONE ELSE'S bytes. That is what this suite
  * pins — every accepted upload is digested from its own file, a lost one is a clean, readable refusal, and the
  * connection is usable again immediately after. The CLI additionally never sends a zero-length body
  * (`IngestCmd.upload` refuses an empty file), which is the trigger that makes the race easy to hit.
  */
class ZeroLengthUploadSuite extends munit.FunSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(60, "s")

  /** Exactly what the CLI builds: HTTP/1.1, because java.net.http's HTTP/2 default mangles body-carrying requests
    * against this server outright.
    */
  private def client() = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

  private def upload(c: HttpClient, url: String, file: Path): (Int, String) =
    val r = c.send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofFile(file))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )
    (r.statusCode, r.body)

  private def pdf(text: String): Path =
    val p = Files.createTempFile("histmersion-reuse-", ".pdf")
    Files.write(p, s"%PDF-1.4 $text".getBytes("UTF-8"))
    p

  private def sha256(p: Path): String =
    java.security.MessageDigest
      .getInstance("SHA-256")
      .digest(Files.readAllBytes(p))
      .map(b => f"${b & 0xff}%02x")
      .mkString

  private val Sha = """"sha256":"([^"]+)"""".r

  /** The only two acceptable outcomes of an upload: stored, digested from THIS file — or cleanly refused as empty
    * because the connection lost the body. Never anything else.
    */
  private def assertHonest(status: Int, body: String, file: Path, what: String): Unit =
    if status == 202 then
      assertEquals(
        Sha.findFirstMatchIn(body).map(_.group(1)),
        Some(sha256(file)),
        s"$what was accepted, so it must have been digested from ITS OWN body: $body"
      )
    else
      assertEquals(status, 400, s"$what: $body")
      assert(body.contains("empty"), s"$what: a lost body must read as a clean refusal: $body")

  private def withServer(f: String => Unit): Unit =
    val base = AppConfig.load()
    val cfg = base.copy(
      db = base.db.copy(url = testDb.get),
      converter = base.converter.copy(spoolDir = Files.createTempDirectory("histmersion-spool0").toString)
    )
    supervised {
      val b = Main.server(cfg, "127.0.0.1", 0, NoConverter).start()
      try f(s"http://127.0.0.1:${b.port}/api/ingest")
      finally b.stop()
    }

  test("no upload on a reused connection is ever stored with another request's bytes"):
    withServer { url =>
      val c = client()
      val files = (1 to 6).map(i => pdf(s"document number $i, which is unlike every other one")).toList
      files.zipWithIndex.foreach { (f, i) =>
        val (s, b) = upload(c, s"$url?citekey=zz_reuse_$i&force=true", f)
        assertHonest(s, b, f, s"upload #$i")
      }
      files.foreach(Files.deleteIfExists)
    }

  test("a zero-byte upload is refused, and a FRESH connection afterwards is unaffected"):
    withServer { url =>
      val empty = Files.createTempFile("histmersion-zero-", ".pdf")
      val real = pdf("perfectly good content")
      val (se, be) = upload(client(), s"$url?citekey=zz_zero&force=true", empty)
      assertEquals(se, 400, be)
      assert(be.contains("empty"), be)
      // a NEW client, hence a new connection: always correct
      val (s2, b2) = upload(client(), s"$url?citekey=zz_zero_next&force=true", real)
      assertEquals(s2, 202, b2)
      assertHonest(s2, b2, real, "the upload after a zero-byte one, on a new connection")
      Files.deleteIfExists(empty); Files.deleteIfExists(real)
    }

  test("a connection recovers after losing one request's body"):
    withServer { url =>
      val c = client()
      val empty = Files.createTempFile("histmersion-zero-", ".pdf")
      val real = pdf("perfectly good content")
      assertEquals(upload(c, s"$url?citekey=zz_quirk&force=true", empty)._1, 400)
      // the next request is the one at risk; whatever happens to it, the one after must be fine
      val (sn, bn) = upload(c, s"$url?citekey=zz_quirk_next&force=true", real)
      assertHonest(sn, bn, real, "the request right after a zero-byte upload")
      val (sa, ba) = upload(c, s"$url?citekey=zz_quirk_after&force=true", real)
      assertEquals(sa, 202, s"the connection must be usable again after at most one lost request: $ba")
      Files.deleteIfExists(empty); Files.deleteIfExists(real)
    }

  /** No sidecar is needed: the jobs never get to run in this suite. */
  private object NoConverter extends ConverterClient:
    def health = Right("ok")
    def convert(file: Path, filename: String, quality: Boolean) = Left("not used")
