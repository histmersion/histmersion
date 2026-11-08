package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.AppConfig
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.db.TestDb
import com.histmersion.config.AuthConfig
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig

/** The write gate as the web UI meets it, in both modes: `/api/config` tells the page whether writes need a key (and
  * never carries the key), reads stay open, and every write — a JSON PATCH, a queue edit, a raw-body upload — is
  * refused with 401 until it carries `Authorization: Bearer <key>`. Skipped without HISTMERSION_TEST_DB_URL.
  */
class AuthHttpSuite extends munit.FunSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  private def req(url: String, key: Option[String]) =
    val b = HttpRequest.newBuilder(URI.create(url))
    key.fold(b)(k => b.header("Authorization", s"Bearer $k"))

  private def get(url: String) = send(req(url, None).build())
  private def put(url: String, body: String, key: Option[String]) =
    send(
      req(url, key)
        .header("content-type", "application/json")
        .PUT(HttpRequest.BodyPublishers.ofString(body))
        .build()
    )
  private def patch(url: String, body: String, key: Option[String]) =
    send(
      req(url, key)
        .header("content-type", "application/json")
        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
        .build()
    )
  private def upload(url: String, bytes: Array[Byte], key: Option[String]) =
    send(
      req(url, key)
        .header("content-type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
        .build()
    )

  private val ck = "zz_http_auth"

  /** The accepted upload's job converts through this stub, never the live sidecar on :8100. */
  private val converter = OfflineConverter()

  private def withServer(apiKey: AuthConfig.ApiKey)(body: String => Unit): Unit =
    val base = AppConfig.load()
    val cfg = base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.auth)
      .setTo(base.auth.modify(_.apiKey).setTo(apiKey))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), converter).start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  test("no key configured: the page is told writes are open, and a write needs no header"):
    withServer(AuthConfig.ApiKey("")) { url =>
      val (cs, cb) = get(s"$url/api/config")
      assertEquals(cs, 200)
      assert(cb.contains("\"writeKeyRequired\":false"), cb)
      val (ps, pb) = put(s"$url/api/sources/$ck", """{"title":"auth open"}""", None)
      assertEquals(ps, 200, pb)
      val (as, ab) = patch(s"$url/api/sources/$ck", """{"metadata":{"title":"auth open 2"}}""", None)
      assertEquals(as, 200, ab)
      assert(ab.contains("auth open 2"), ab)
    }

  test("a key configured: config says so without leaking it; reads stay open; writes need the key"):
    withServer(AuthConfig.ApiKey("s3cret")) { url =>
      val (cs, cb) = get(s"$url/api/config")
      assertEquals(cs, 200)
      assert(cb.contains("\"writeKeyRequired\":true"), cb)
      assert(!cb.contains("s3cret"), "the key must never be served: " + cb)

      // reads: open, with or without a header
      assertEquals(get(s"$url/api/jobs")._1, 200)
      assertEquals(get(s"$url/api/queue/acquire")._1, 200)

      // a JSON write: 401 without a key and with a wrong one, accepted with the right one
      val body = """{"title":"auth gated"}"""
      assertEquals(put(s"$url/api/sources/$ck", body, None)._1, 401)
      assertEquals(put(s"$url/api/sources/$ck", body, Some("wrong"))._1, 401)
      val (ps, pb) = put(s"$url/api/sources/$ck", body, Some("s3cret"))
      assertEquals(ps, 200, pb)
      assertEquals(patch(s"$url/api/sources/$ck", """{"summary":"x"}""", None)._1, 401)
      val (as, ab) = patch(s"$url/api/sources/$ck", """{"summary":"with key"}""", Some("s3cret"))
      assertEquals(as, 200, ab)
      assert(ab.contains("with key"), ab)

      // the raw-body upload the web UI's file picker sends is gated too
      val pdf = "%PDF-1.4\n%auth test\n".getBytes("UTF-8")
      val (us, ub) = upload(s"$url/api/ingest?citekey=zz_http_auth_upload&filename=a.pdf", pdf, None)
      assertEquals(us, 401, ub)
      assert(ub.contains("unauthorized"), ub)
      // a refused body is drained, not left in the connection: the next requests are answered
      assertEquals(get(s"$url/api/health")._1, 200)
      val (ks, kb) = upload(s"$url/api/ingest?citekey=zz_http_auth_upload&filename=a.pdf", pdf, Some("s3cret"))
      assertEquals(ks, 202, kb)
      // the accepted job converts through the test's stub — not the converter configured at :8100
      val deadline = System.currentTimeMillis() + 10000
      while converter.converted.isEmpty && System.currentTimeMillis() < deadline do Thread.sleep(50)
      assert(
        converter.converted.exists(_.value.endsWith(".pdf")),
        s"the upload never reached the stub: ${converter.converted}"
      )
      // …and a body-less write is gated the same way
      val del = HttpRequest.newBuilder(URI.create(s"$url/api/articles/zz-auth-none")).DELETE()
      assertEquals(send(del.build())._1, 401)
    }
