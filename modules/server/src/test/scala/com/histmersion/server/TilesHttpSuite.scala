package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.TilesApi
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.maptiles.{TileCoord, VectorTileReader}
import com.histmersion.util.database.Migrate

import com.augustnagro.magnum.*
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.util.database.Db
import com.histmersion.config.ServerConfig
import com.histmersion.util.database.TestPermits.given

/** `GET /api/tiles/{layer}/{z}/{x}/{y}` through the wire: a vector tile of the layer, its ETag and the browser's
  * revalidation (304), a write that makes the kept tile stale, a filter that changes the tile, and a bad layer or tile
  * refused. Fixtures `zztilehttp` at 100°E 70°S. Skipped without HISTMERSION_TEST_DB_URL.
  */
class TilesHttpSuite extends munit.FunSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def get(url: String, etag: Option[String] = None): (Int, Array[Byte], Option[String], Option[String]) =
    val b = HttpRequest.newBuilder(URI.create(url))
    etag.foreach(t => b.header("If-None-Match", t))
    val r = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray())
    (r.statusCode, r.body, opt(r.headers.firstValue("ETag")), opt(r.headers.firstValue("Content-Type")))
  private def opt(o: java.util.Optional[String]): Option[String] = if o.isPresent then Some(o.get) else None

  private val lon = Longitude(100.0)
  private val lat = Latitude(-70.0)

  private def withServer(f: (String, javax.sql.DataSource) => Unit): Unit =
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    val ds = Db.pool(cfg.db)
    try
      Migrate.run(ds)
      connect(ds):
        val _ = sql"DELETE FROM places WHERE id LIKE 'zztilehttp%'".update.run()
        val _ = sql"""INSERT INTO places (id, label, kind, period, geom) VALUES
                      ('zztilehttp_a', 'Zztilehttp A', 'site', int8range(-2000, -1500, '[]'), ST_SetSRID(ST_MakePoint(${lon.value: Double}, ${lat.value: Double}), 4326))""".update
          .run()
      supervised {
        val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
        try f(s"http://127.0.0.1:${b.port}", ds)
        finally b.stop()
      }
    finally
      connect(ds)(sql"DELETE FROM places WHERE id LIKE 'zztilehttp%'".update.run())
      ds.close()

  private def tilePath(z: Int): String =
    val (mx, my) = TileCoord.mercator(lat, lon)
    s"$z/${math.floor(mx * (1 << z)).toLong}/${math.floor(my * (1 << z)).toLong}"

  test("a sites tile: its layer and site, revalidated by ETag, stale after a write, narrowed by the span"):
    withServer { (url, ds) =>
      val u = s"$url/api/tiles/sites/${tilePath(8)}?from=-1800&to=-1700"
      val (status, body, etag, ct) = get(u)
      assertEquals(status, 200)
      assertEquals(ct, Some(TilesApi.ContentType))
      val layers = VectorTileReader.read(body)
      assertEquals(layers.map(_.name), Vector("sites"))
      val f = layers.head.features.find(_.text("k").contains("zztilehttp_a"))
      assertEquals(f.flatMap(_.text("label")), Some("Zztilehttp A"))
      assertEquals(f.flatMap(_.whole("periodStart")), Some(-2000L))
      assertEquals(f.flatMap(_.whole("z")), Some(8L))
      // the browser's copy is current: 304, no body
      val (s2, b2, e2, _) = get(u, etag)
      assertEquals((s2, b2.length, e2), (304, 0, etag))
      // a write to what the layer reads: the kept tile is stale at once
      connect(ds)(sql"UPDATE places SET label = 'Zztilehttp B' WHERE id = 'zztilehttp_a'".update.run())
      val (s3, b3, e3, _) = get(u, etag)
      assertEquals(s3, 200)
      assertNotEquals(e3, etag)
      assert(VectorTileReader.read(b3).flatMap(_.features).exists(_.text("label").contains("Zztilehttp B")))
      // another span is another tile: the site is not in it
      val (_, b4, _, _) = get(s"$url/api/tiles/sites/${tilePath(8)}?from=-1000&to=-900")
      assert(!VectorTileReader.read(b4).flatMap(_.features).exists(_.text("k").contains("zztilehttp_a")))
    }

  test("an unknown layer and a tile outside the grid are refused"):
    withServer { (url, _) =>
      assertEquals(get(s"$url/api/tiles/castles/0/0/0")._1, 400)
      assertEquals(get(s"$url/api/tiles/sites/2/4/0")._1, 400)
      assertEquals(get(s"$url/api/tiles/media/30/0/0")._1, 400)
      assertEquals(get(s"$url/api/tiles/media/0/0/0?from=0&to=10")._1, 200)
    }
