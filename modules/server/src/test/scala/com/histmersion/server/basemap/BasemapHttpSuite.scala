package com.histmersion.server.basemap

import com.histmersion.core.*
import com.histmersion.server.Main
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}

/** The terrain base map over HTTP, on a server whose `basemap.dir` holds a fixture extract: the
  * status, a tile as stored (gzip-encoded vector, image terrain), 204 outside the extract, 404 for an
  * unknown layer, the fetch's refusals, and the removal (behind the API key, as every write). Skipped
  * without HISTMERSION_TEST_DB_URL. */
class BasemapHttpSuite extends com.histmersion.db.DbSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def get(url: String): HttpResponse[Array[Byte]] =
    http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray())
  private def send(method: String, url: String, key: Option[String]): (Int, String) =
    val b = HttpRequest.newBuilder(URI.create(url)).method(method, HttpRequest.BodyPublishers.noBody())
    val r = http.send(key.fold(b)(k => b.header("Authorization", s"Bearer $k")).build(), HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  private def withServer(dir: Path)(body: String => Unit): Unit =
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get), auth = base.auth.copy(apiKey = "k3y"),
      basemap = base.basemap.copy(dir = dir.toString))
    supervised {
      val b = Main.server(cfg, "127.0.0.1", 0).start()
      try body(s"http://127.0.0.1:${b.port}/api/basemap")
      finally b.stop()
    }

  test("status, tiles as stored, 204 outside, 404 for no such layer, and the refusals"):
    val dir = Files.createTempDirectory("basemap-http")
    withServer(dir) { url =>
      // nothing downloaded yet: the status says so, and how to fetch it
      val s0 = io.circe.parser.decode[BasemapStatus](String(get(url).body)).fold(e => fail(e.toString), identity)
      assert(!s0.available)
      assertEquals(s0.fetchCommand, "histmersion basemap fetch")
      assertEquals(s0.dir, dir.toString)
      assertEquals(get(s"$url/vector/0/0/0").statusCode, 404)

      BasemapFixtures.vectorArchive(dir.resolve("vector.pmtiles"), maxZoom = 3)
      BasemapFixtures.terrainArchive(dir.resolve("terrain.pmtiles"), maxZoom = 2)
      val s1 = io.circe.parser.decode[BasemapStatus](String(get(url).body)).fold(e => fail(e.toString), identity)
      assert(s1.available, s1.toString)
      val v = s1.layers.find(_.layer == "vector").get
      assertEquals((v.present, v.maxZoom, v.tileType), (true, Some(3), Some("application/vnd.mapbox-vector-tile")))
      assert(v.version.nonEmpty && v.attribution.nonEmpty, v.toString)

      val t = get(s"$url/vector/2/1/1?v=${v.version.get}")
      assertEquals(t.statusCode, 200)
      assertEquals(t.headers.firstValue("content-encoding").orElse(""), "gzip")
      assertEquals(t.headers.firstValue("content-type").orElse(""), "application/vnd.mapbox-vector-tile")
      assert(t.headers.firstValue("cache-control").orElse("").contains("max-age"))
      assert(PmTiles.isGzip(t.body))
      val r = get(s"$url/terrain/2/3/1")
      assertEquals((r.statusCode, r.headers.firstValue("content-type").orElse("")), (200, "image/webp"))
      assertEquals(String(r.body), "RIFF2/3/1")
      assert(r.headers.firstValue("content-encoding").isEmpty)
      assertEquals(get(s"$url/terrain/5/0/0").statusCode, 204, "deeper than the extract")
      assertEquals(get(s"$url/roads/1/0/0").statusCode, 404)

      // the fetch is a write: the key; a bad region is refused before any job starts
      assertEquals(send("POST", s"$url/fetch?bbox=1,2,3", None)._1, 401)
      val (c, b) = send("POST", s"$url/fetch?bbox=1,2,3", Some("k3y"))
      assertEquals(c, 400, b)
      assert(b.contains("minLon,minLat,maxLon,maxLat"), b)
      assertEquals(send("POST", s"$url/fetch?maxZoom=40", Some("k3y"))._1, 400)

      // removing: the files go, and the status follows
      val (rc, rb) = send("DELETE", s"$url?layer=terrain", Some("k3y"))
      assertEquals(rc, 200, rb)
      assert(rb.contains("terrain.pmtiles"), rb)
      assert(!Files.exists(dir.resolve("terrain.pmtiles")))
      assertEquals(get(s"$url/terrain/0/0/0").statusCode, 404)
      assertEquals(get(s"$url/vector/0/0/0").statusCode, 200)
    }

  test("ui.base-map-source auto: online until the extract can draw the default style whole, then local; a cut's properties"):
    val dir = Files.createTempDirectory("basemap-http")
    withServer(dir) { url =>
      val config = url.stripSuffix("/basemap") + "/config"
      def source() = io.circe.parser.decode[UiConfig](String(get(config).body)).fold(e => fail(e.toString), _.baseMapSource)
      assertEquals(source(), "online", "nothing downloaded")
      BasemapFixtures.vectorArchive(dir.resolve("vector.pmtiles"), maxZoom = 3,
        io.circe.Json.obj("histmersion" -> io.circe.Json.obj("drop" -> io.circe.Json.fromString("roads; places; boundaries"))))
      val st = io.circe.parser.decode[BasemapStatus](String(get(url).body)).fold(e => fail(e.toString), identity)
      assertEquals(st.missing("standard"), List("boundaries", "places", "roads"))
      assertEquals(source(), "online", "the default local style lacks roads, places and borders")
      Thread.sleep(5)   // a new stamp: the archive is opened again
      BasemapFixtures.vectorArchive(dir.resolve("vector.pmtiles"), maxZoom = 3,
        io.circe.Json.obj("histmersion" -> io.circe.Json.obj("keepAll" -> io.circe.Json.True)))
      assertEquals(source(), "local", "a --keep-all extract: the page asks no other host")
      // the page's cut: only the kinds and properties it reads
      val t = get(s"$url/vector/3/4/3?layers=water:p~sea&keys=kind,name")
      assertEquals(t.statusCode, 200)
      val fs = Mvt.features(PmTiles.gunzip(t.body).toOption.get).fold(fail(_), identity)
      assert(fs.nonEmpty && fs.forall((l, g, p) => l == "water" && g == 1 && p.get("kind").contains("sea") && p.keySet.subsetOf(Set("kind", "name"))),
        fs.toString)
    }
