package com.histmersion
package util.basemap
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.server.Composition
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.instance.UiConfig
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.server.KeyedLoopback
import com.histmersion.config.ServerConfig

/** The terrain base map over HTTP, on a server whose `basemap.dir` holds a fixture extract: the status, a tile as
  * stored (gzip-encoded vector, image terrain), 204 outside the extract, 404 for an unknown layer, the fetch's
  * refusals, and the removal (behind the API key, as every write). Skipped without HISTMERSION_TEST_DB_URL.
  */
class BasemapHttpSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def get(url: String): HttpResponse[Array[Byte]] =
    http.send(
      HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer k3y").build(),
      HttpResponse.BodyHandlers.ofByteArray()
    )
  private def send(method: String, url: String, key: Option[String]): (Int, String) =
    val b = HttpRequest.newBuilder(URI.create(url)).method(method, HttpRequest.BodyPublishers.noBody())
    val r =
      http.send(key.fold(b)(k => b.header("Authorization", s"Bearer $k")).build(), HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  private def withServer(dir: Path, onDemand: Boolean = true)(body: String => Unit): Unit =
    val base = AppConfig.load()
    val cfg = base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.auth)
      .setTo(KeyedLoopback(base.auth, "k3y"))
      .modify(_.basemap)
      .setTo(
        base.basemap.copy(
          dir = BasemapConfig.Dir(dir.toString),
          terrain = base.basemap.terrain
            .modify(_.onDemand)
            .setTo(
              base.basemap.terrain.onDemand
                .modify(_.enabled)
                .setTo(BasemapOnDemandConfig.Enabled(onDemand && base.basemap.terrain.onDemand.enabled.value))
            ),
          // the map and the sea floor outside the extract are read from the network: never in this suite (#3760)
          vector = base.basemap.vector
            .modify(_.onDemand)
            .setTo(base.basemap.vector.onDemand.modify(_.enabled).setTo(BasemapOnDemandConfig.Enabled(false))),
          bathymetry = base.basemap.bathymetry
            .modify(_.onDemand)
            .setTo(base.basemap.bathymetry.onDemand.modify(_.enabled).setTo(BasemapOnDemandConfig.Enabled(false)))
        )
      )
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      try body(s"http://127.0.0.1:${b.port}/api/basemap")
      finally b.stop()
    }

  test("status, tiles as stored, 204 outside, 404 for no such layer, and the refusals"):
    val dir = Files.createTempDirectory("basemap-http")
    withServer(dir) { url =>
      // nothing downloaded yet: the status says so, and how to fetch it
      val s0 = RawJson.decode[Basemap.Status](String(get(url).body)).fold(e => fail(e.toString), identity)
      assert(!s0.available.value)
      assertEquals(s0.fetchCommand, "histmersion basemap fetch")
      assertEquals(s0.dir, dir.toString)
      assertEquals(get(s"$url/vector/0/0/0").statusCode, 404)

      BasemapFixtures.vectorArchive(dir.resolve("vector.pmtiles"), maxZoom = PmTiles.Zoom(3))
      BasemapFixtures.terrainArchive(dir.resolve("terrain.pmtiles"), maxZoom = PmTiles.Zoom(2))
      val s1 = RawJson.decode[Basemap.Status](String(get(url).body)).fold(e => fail(e.toString), identity)
      assert(s1.available.value, s1.toString)
      val v = s1.layers.find(_.layer == Basemap.Layer.Wire("vector")).get
      assertEquals((v.present, v.maxZoom, v.tileType), (true, Some(3), Some("application/vnd.mapbox-vector-tile")))
      assert(v.version.nonEmpty && v.attribution.value.nonEmpty, v.toString)

      val t = get(s"$url/vector/2/1/1?v=${v.version.get}")
      assertEquals(t.statusCode, 200)
      assertEquals(t.headers.firstValue("content-encoding").orElse(""), "gzip")
      assertEquals(t.headers.firstValue("content-type").orElse(""), "application/vnd.mapbox-vector-tile")
      assert(t.headers.firstValue("cache-control").orElse("").contains("max-age"))
      assert(PmTiles.isGzip(PmTiles.TileBytes(t.body)))
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
      assert(b.contains("is not a focus bounding box (e.g. 19,34,30,42, world)"), b)
      assertEquals(send("POST", s"$url/fetch?maxZoom=40", Some("k3y"))._1, 400)

      // removing: the files go, and the status follows
      val (rc, rb) = send("DELETE", s"$url?layer=terrain", Some("k3y"))
      assertEquals(rc, 200, rb)
      assert(rb.contains("terrain.pmtiles"), rb)
      assert(!Files.exists(dir.resolve("terrain.pmtiles")))
      assertEquals(get(s"$url/terrain/0/0/0").statusCode, 404)
      assertEquals(get(s"$url/vector/0/0/0").statusCode, 200)
    }

  test(
    "ui.base-map-source auto: online until the extract can draw a vector style whole, then local; a cut's properties"
  ):
    val dir = Files.createTempDirectory("basemap-http")
    withServer(dir) { url =>
      val config = url.stripSuffix("/basemap") + "/config"
      def source() =
        RawJson.decode[UiConfig](String(get(config).body)).fold(e => fail(e.toString), _.baseMapSource)
      assertEquals(source(), "online", "nothing downloaded")
      BasemapFixtures.vectorArchive(
        dir.resolve("vector.pmtiles"),
        maxZoom = PmTiles.Zoom(3),
        Json.obj(
          "histmersion" -> Json.obj("drop" -> Json.fromString("roads; places; boundaries"))
        )
      )
      val st = RawJson.decode[Basemap.Status](String(get(url).body)).fold(e => fail(e.toString), identity)
      assertEquals(st.missing(Basemap.StyleState.Id("readable")), List("boundaries", "places", "roads"))
      assertEquals(st.missing(Basemap.StyleState.Id("physical")), Nil)
      assertEquals(source(), "local", "Physical draws nothing the default drop list leaves out")
      Thread.sleep(5) // a new stamp: the archive is opened again
      BasemapFixtures.vectorArchive(
        dir.resolve("vector.pmtiles"),
        maxZoom = PmTiles.Zoom(3),
        Json.obj("histmersion" -> Json.obj("keepAll" -> Json.True))
      )
      assertEquals(source(), "local", "a --keep-all extract: the page asks no other host")
      // the page's cut: only the kinds and properties it reads
      val t = get(s"$url/vector/3/4/3?layers=water:p~sea&keys=kind,name")
      assertEquals(t.statusCode, 200)
      val fs =
        Mvt.features(PmTiles.gunzip(PmTiles.CompressedBytes(t.body)).toOption.get).fold(e => fail(e.sentence), identity)
      assert(
        fs.nonEmpty && fs.forall((l, g, p) =>
          l == Mvt.LayerName("water") && g == Mvt.GeomType(1) && p
            .get(Mvt.PropKey("kind"))
            .contains(Mvt.PropText("sea")) && p.keySet.subsetOf(Set(Mvt.PropKey("kind"), Mvt.PropKey("name")))
        ),
        fs.toString
      )
    }

  /** The browser fixture's base map (modules/web/test/fixtures/basemap). */
  private def fixture: Path =
    Iterator
      .iterate(Path.of(sys.props("user.dir")).toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .map(_.resolve("modules/web/test/fixtures/basemap"))
      .find(d => Files.isRegularFile(d.resolve("terrain.pmtiles")))
      .getOrElse(fail("the browser fixture is not in the repository"))

  test("the status names the relief deeper than the extract on the wire: each focus region and the on-demand relief"):
    // the page decides the contours from these keys (layers[terrain].focus, .onDemand): a status without them
    // keeps the contours off everywhere
    val dir = Files.createTempDirectory("basemap-http")
    Files.copy(fixture.resolve("terrain.pmtiles"), dir.resolve("terrain.pmtiles"))
    Files.createDirectories(dir.resolve("terrain-focus"))
    Files.copy(fixture.resolve("terrain-focus/argolid.pmtiles"), dir.resolve("terrain-focus/argolid.pmtiles"))
    def terrain(url: String): Cursor =
      val json = RawJson.parse(String(get(url).body)).fold(e => fail(e.toString), identity)
      val layers = json.cursor.downField("layers").values.getOrElse(fail(json.noSpaces))
      layers.find(_.cursor.get[String]("layer").contains("terrain")).getOrElse(fail(json.noSpaces)).cursor
    for onDemand <- List(true, false) do
      withServer(dir, onDemand) { url =>
        val t = terrain(url)
        val focus = t.downField("focus").downArray
        assertEquals(focus.get[String]("name"), Right("argolid"))
        assertEquals(focus.get[String]("bbox").map(b => BBox.parse(BBox.Text(b)).isDefined), Right(true))
        assertEquals((focus.get[Int]("minZoom"), focus.get[Int]("maxZoom")), (Right(9), Right(11)))
        assert(focus.get[Long]("bytes").exists(_ > 0), focus.focus.toString)
        val od = t.downField("onDemand")
        assertEquals(
          od.get[Boolean]("enabled"),
          Right(onDemand && AppConfig.load().basemap.terrain.onDemand.enabled.value)
        )
        assertEquals(od.get[Int]("maxZoom"), Right(AppConfig.load().basemap.terrain.onDemand.maxZoom))
        assert(od.get[Long]("maxBytes").isRight && od.get[String]("dir").isRight, od.focus.toString)
        // and the typed status a page decodes says the same
        val st = RawJson.decode[Basemap.Status](String(get(url).body)).fold(e => fail(e.toString), identity)
        val info = st.layers.find(_.layer == Basemap.Layer.Wire("terrain")).get
        assertEquals(info.focus.map(_.name), List("argolid"))
        assertEquals(
          info.onDemand.map(_.enabled),
          Some(onDemand && AppConfig.load().basemap.terrain.onDemand.enabled.value)
        )
      }

  test("the relief deeper than any archive holds: cut from its nearest ancestor, resampled in metres, a terrarium PNG"):
    // the browser fixture's relief: the extract to zoom 6, the Argolid focus region at 9–11; nothing on demand
    val dir = Files.createTempDirectory("basemap-http")
    Files.copy(fixture.resolve("terrain.pmtiles"), dir.resolve("terrain.pmtiles"))
    Files.createDirectories(dir.resolve("terrain-focus"))
    Files.copy(fixture.resolve("terrain-focus/argolid.pmtiles"), dir.resolve("terrain-focus/argolid.pmtiles"))
    withServer(dir, onDemand = false) { url =>
      val st = RawJson.decode[Basemap.Status](String(get(url).body)).fold(e => fail(e.toString), identity)
      val t = st.layers.find(_.layer == Basemap.Layer.Wire("terrain")).get
      assertEquals((t.maxZoom, t.deepest), (Some(6), Some(11)))
      def tile(z: Basemap.Zoom, lon: Longitude, lat: Latitude) =
        get(s"$url/terrain/$z/${Basemap.tileX(lon, z)}/${Basemap.tileY(lat, z)}")
      def heights(r: HttpResponse[Array[Byte]]) =
        TerrainResample
          .decode(Png.PngBytes(r.body))
          .fold(
            e => fail(e.sentence),
            i => {
              assertEquals((i.width, i.height), (512, 512))
              i.pixels.value.map(px => TerrainResample.metres(TerrainResample.Pixel(px)).value)
            }
          )
      // zoom 8 over Naxos (no focus region there): from the extract's zoom-6 tile, a PNG
      val naxos = tile(Basemap.Zoom(8), Longitude(25.45), Latitude(37.05))
      assertEquals(naxos.statusCode, 200)
      assertEquals(naxos.headers.firstValue("content-type").orElse(""), "image/png")
      val hs = heights(naxos)
      assert(hs.max > 300 && hs.min < 5, s"the island and its sea: ${hs.min} … ${hs.max}")
      // zoom 10 inside the Argolid: the focus region's own tile, as stored
      val mycenae = tile(Basemap.Zoom(10), Longitude(22.756), Latitude(37.73))
      assertEquals((mycenae.statusCode, mycenae.headers.firstValue("content-type").orElse("")), (200, "image/webp"))
      // zoom 11 at the Argolid's edge outside it (Nafplio's gulf, south of the region): cut from the extract
      assertEquals(
        tile(Basemap.Zoom(11), Longitude(22.80), Latitude(37.50)).headers.firstValue("content-type").orElse(""),
        "image/png"
      )
      // deeper than every archive: none (the map draws its deepest tile larger)
      assertEquals(tile(Basemap.Zoom(12), Longitude(25.45), Latitude(37.05)).statusCode, 204)
      // outside every region: none
      assertEquals(tile(Basemap.Zoom(9), Longitude(-40.0), Latitude(10.0)).statusCode, 204)
      // the tiles' URLs change with the focus regions: a resampled tile is never kept past a new region
      Files.delete(dir.resolve("terrain-focus/argolid.pmtiles"))
      val after = RawJson.decode[Basemap.Status](String(get(url).body)).fold(e => fail(e.toString), identity)
      assertNotEquals(after.layers.find(_.layer == Basemap.Layer.Wire("terrain")).get.version, t.version)
    }
