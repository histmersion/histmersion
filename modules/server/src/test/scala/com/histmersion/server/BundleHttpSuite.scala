package com.histmersion.server

import ox.supervised
import com.histmersion.core.*
import com.histmersion.db.{Db, Migrate}
import io.circe.parser
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Files

/** The portable bundle over real HTTP: `GET/PUT /api/instance`, `GET /api/bundle`,
  * `POST /api/bundle/verify` and `POST /api/bundle/import` — the wiring, the write gate, the
  * download name, and an instance importing its own bundle changing nothing. The behaviour in depth
  * is `db.BundleSuite`'s. Skipped without HISTMERSION_TEST_DB_URL. */
class BundleHttpSuite extends munit.FunSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Key  = "k3y"
  private val http = HttpClient.newHttpClient()
  private val work = Files.createTempDirectory("histmersion-bundles")

  private lazy val cfg =
    val base = AppConfig.load()
    base.copy(db = base.db.copy(url = testDb.get), auth = base.auth.copy(apiKey = Key),
      bundles = base.bundles.copy(dir = work.toString))

  private def withServer(body: String => Unit): Unit =
    Migrate.run(Db.dataSource(cfg.db))
    supervised {
      val b = Main.server(cfg, "127.0.0.1", 0).start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  private def get(url: String): HttpResponse[Array[Byte]] =
    http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray())

  private def post(url: String, body: Array[Byte], key: Option[String] = Some(Key)): HttpResponse[String] =
    val b = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/octet-stream")
      .POST(HttpRequest.BodyPublishers.ofByteArray(body))
    http.send(key.fold(b)(k => b.header("Authorization", s"Bearer $k")).build(), HttpResponse.BodyHandlers.ofString())

  private def put(url: String, json: String, key: Option[String]): HttpResponse[String] =
    val b = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
      .PUT(HttpRequest.BodyPublishers.ofString(json))
    http.send(key.fold(b)(k => b.header("Authorization", s"Bearer $k")).build(), HttpResponse.BodyHandlers.ofString())

  private def decode[A: io.circe.Decoder](s: String): A = parser.decode[A](s).fold(e => fail(s"$e in $s"), identity)

  test("instance, export, verify and an own-bundle import over HTTP"):
    withServer { url =>
      val inst = decode[InstanceInfo](String(get(s"$url/api/instance").body, "UTF-8"))

      // naming the instance is a write: gated
      assertEquals(put(s"$url/api/instance", """{"label":"zz-work"}""", None).statusCode, 401)
      val named = put(s"$url/api/instance", """{"label":"zz-work"}""", Some(Key))
      assertEquals(named.statusCode, 200, named.body)
      assertEquals(decode[InstanceInfo](named.body).label, Some("zz-work"))

      val exp = get(s"$url/api/bundle")
      assertEquals(exp.statusCode, 200)
      val cd = exp.headers.firstValue("content-disposition").orElse("")
      assert(cd.contains("histmersion-zz-work-") && cd.endsWith(".hmx\""), cd)
      val r  = BundleTar.Reader(java.io.ByteArrayInputStream(exp.body))
      val m  = r.next().toOption.flatten.filter(_.name == Bundle.ManifestPath)
        .flatMap(_ => BundleFormat.parseManifest(r.readAll()).toOption).getOrElse(fail("no manifest"))
      assertEquals(m.instance, BundleInstance(inst.id, Some("zz-work")))
      // the same bytes again: the export is deterministic
      assert(java.util.Arrays.equals(get(s"$url/api/bundle").body, exp.body))

      assertEquals(get(s"$url/api/bundle?since=yesterday").statusCode, 400)

      val v = post(s"$url/api/bundle/verify", exp.body)
      assertEquals(v.statusCode, 200, v.body)
      assert(decode[BundleVerifyResult](v.body).ok, v.body)
      assertEquals(post(s"$url/api/bundle/verify", exp.body, None).statusCode, 401)

      val i = post(s"$url/api/bundle/import", exp.body)
      assertEquals(i.statusCode, 200, i.body)
      val res = decode[BundleImportResult](i.body)
      assert(res.applied, i.body)
      assertEquals(res.changed, 0, i.body)

      assertEquals(post(s"$url/api/bundle/import", Array.emptyByteArray).statusCode, 400)
      val _ = put(s"$url/api/instance", """{"label":""}""", Some(Key))
    }
