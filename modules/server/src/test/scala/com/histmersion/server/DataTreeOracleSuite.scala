package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.database.Transactor

import com.histmersion.util.settings.ConfigSource

import com.histmersion.config.*
import com.histmersion.db.{DbSuite, TestDb}
import com.histmersion.util.database.Migrate
import com.histmersion.util.jobs.JobRepo
import com.histmersion.util.basemap.{BasemapFixtures, BasemapServiceSuite, RangeHttp}
import com.histmersion.util.storage.Reservation
import com.histmersion.{ByteCount, ByteOffset}
import org.ekrich.config.ConfigFactory
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.basemap.Basemap
import com.histmersion.config.ServerConfig
import com.histmersion.util.basemap.PmTiles
import com.histmersion.config.BasemapConfig
import com.histmersion.config.BasemapVectorConfig
import com.histmersion.config.BasemapOnDemandConfig
import com.histmersion.config.BasemapTerrainConfig
import com.histmersion.config.BasemapLabelsConfig
import com.histmersion.config.BasemapBathymetryConfig
import com.histmersion.config.BasemapGebcoConfig
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.util.jobs.Job
import com.histmersion.api.BBox
import com.histmersion.domain.library.acquisition.Spool
import com.histmersion.util.storage.Storage
import com.histmersion.util.storage.Location
import com.histmersion.domain.library.Citekey

/** The files a run of the server leaves in its directories, as a smoke run makes them: one scratch root holding the
  * settings file, the data directory, the converter's and the acquisition's spools, the backups and bundles directories
  * and the base map; a server started over it, a setting changed twice, a config reload, a document uploaded and
  * converted, a file spooled by an acquisition; the base map fetched, a focus region added, tiles read from the extract
  * and on demand, a focus region removed. Then every path under the root, relative, with its kind and a file's size, is
  * compared with `oracle/data-tree.txt`, recorded on the code before the file code moved behind the storage area.
  * `HISTMERSION_UPDATE_ORACLE=1` rewrites it. Skipped without HISTMERSION_TEST_DB_URL.
  */
class DataTreeOracleSuite extends DbSuite:
  private given CanEqual[Path, Path] = CanEqual.derived

  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(180, "s")

  private val oracle = Path.of("modules/server/src/test/resources/oracle/data-tree.txt")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private val root = Files.createTempDirectory("histmersion-data-tree")
  private val configFile = root.resolve("config/histmersion.conf")
  private val env = ConfigFactory.empty

  private def dir(name: String): Path = root.resolve(name)

  private def settingsText(extra: String) =
    s"""# the oracle's settings
       |histmersion {
       |  db.url = "${testDb.getOrElse("")}"
       |  runtime.data-dir = "${dir("data")}"
       |  converter.spool-dir = "${dir("spool")}"
       |  acquire.spool-dir = "${dir("acquire-spool")}"
       |  backups.dir = "${dir("backups")}"
       |  bundles.dir = "${dir("bundles")}"
       |  basemap.dir = "${dir("basemap")}"
       |  agents.runtimes.dir = "${dir("data/agents")}"
       |  $extra
       |}
       |""".stripMargin

  private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def post(url: String, body: String) =
    send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    )
  private def upload(url: String, bytes: Array[Byte]) =
    send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
        .build()
    )
  private def field(json: String, name: String): Option[String] =
    s""""$name":"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1))

  private def awaitHttp(url: String, id: String, left: Int = 300): String =
    val (_, b) = get(s"$url/api/jobs/$id")
    val st = field(b, "status").getOrElse("")
    if st == "done" || st == "failed" || left == 0 then b
    else { Thread.sleep(100); awaitHttp(url, id, left - 1) }

  /** The server over the scratch root: two setting changes, a reload, an upload converted. */
  private def serverRun(): Unit =
    Files.createDirectories(configFile.getParent)
    Files.writeString(configFile, settingsText("limits.search = 5"))
    val l = AppConfig.loadWithUserFile(env, Some(configFile)).fold(e => fail(e.value), identity)
    supervised {
      val b = Composition
        .server(
          l.config,
          ServerConfig.Host("127.0.0.1"),
          ServerConfig.Port(0),
          OfflineConverter(),
          source = Some((l, ConfigSource(() => AppConfig.loadWithUserFile(env, Some(configFile)).left.map(p => ConfigSource.Problem(p.value)))))
        )
        .start()
      try
        val url = s"http://127.0.0.1:${b.port}"
        assertEquals(get(s"$url/api/health")._1, 200)
        assertEquals(get(s"$url/api/about")._1, 200)
        List("7", "9").foreach { v =>
          val (_, page) = get(s"$url/api/settings?prefix=limits")
          val version =
            """"version":"([0-9a-f]{64})"""".r.findFirstMatchIn(page).map(_.group(1)).getOrElse(fail(page.take(300)))
          val (s, w) =
            post(s"$url/api/settings", s"""{"changes":[{"key":"limits.search","value":"$v"}],"version":"$version"}""")
          assertEquals(s, 200, w)
        }
        assertEquals(post(s"$url/api/config/reload", "")._1, 200)
        val (us, ub) = upload(s"$url/api/ingest?citekey=zz_data_tree&force=true", "%PDF-1.4 data tree".getBytes(UTF_8))
        assertEquals(us, 202, ub)
        val done = awaitHttp(url, field(ub, "jobId").getOrElse(fail(ub)))
        assert(done.contains("\"done\"") || done.contains("\"failed\""), done)
      finally b.stop()
    }

  /** The base map over fixture archives, as the base-map suite fetches it. */
  private def basemapRun(): Unit =
    val src = Files.createTempDirectory("histmersion-data-tree-src")
    val v = BasemapFixtures.LocalRange(
      BasemapFixtures.vectorArchive(src.resolve("v.pmtiles"), PmTiles.Zoom(5)),
      builds = RangeHttp.ResponseText("""[{"key":"20261018.pmtiles","size":1},{"key":"20261019.pmtiles","size":-1}]""")
    )
    val t = BasemapFixtures.LocalRange(BasemapFixtures.terrainArchive(src.resolve("t.pmtiles"), PmTiles.Zoom(5)))
    val base = AppConfig.loadWithUserFile(env, Some(configFile)).fold(e => fail(e.value), identity).config
    val cfg = base.modify(
      _.basemap).setTo(base.basemap.modify(
        _.requestGapMs).setTo(BasemapConfig.RequestGapMs(0)).modify(_.bbox).setTo(BasemapConfig.Bbox("22,35,29,41")).modify(_.vector).setTo(base.basemap.vector.modify(
          _.url).setTo(BasemapVectorConfig.Url("fake://v/{build}")).modify(_.maxZoom).setTo(BasemapVectorConfig.MaxZoom(4)).modify(_.onDemand).setTo(base.basemap.vector.onDemand.modify(_.enabled).setTo(BasemapOnDemandConfig.Enabled(true)).modify(_.maxZoom).setTo(BasemapOnDemandConfig.MaxZoom(5)).modify(_.requestsPerSecond).setTo(BasemapOnDemandConfig.RequestsPerSecond((1000: Double)))
        )).modify(_.terrain).setTo(base.basemap.terrain.modify(
          _.url).setTo(BasemapTerrainConfig.Url("fake://t")).modify(_.maxZoom).setTo(BasemapTerrainConfig.MaxZoom(3)).modify(_.onDemand).setTo(base.basemap.terrain.onDemand.modify(_.maxZoom).setTo(BasemapOnDemandConfig.MaxZoom(5)).modify(_.requestsPerSecond).setTo(BasemapOnDemandConfig.RequestsPerSecond((1000: Double)))).modify(_.focusRegions).setTo(List(BasemapTerrainConfig.FocusRegions("crete 23,34.8,26.5,35.7"))
        )).modify(_.labels).setTo(base.basemap.labels.modify(_.url).setTo(BasemapLabelsConfig.Url("fake://ne/{file}.geojson")).modify(_.files).setTo(List("ranges", "rivers").map(BasemapLabelsConfig.Files(_))).modify(_.maxZoom).setTo(BasemapLabelsConfig.MaxZoom(6))).modify(_.bathymetry).setTo(base.basemap.bathymetry.modify(
          _.url).setTo(BasemapBathymetryConfig.Url("fake://sea/{z}/{x}/{y}.png")).modify(_.maxZoom).setTo(BasemapGebcoConfig.MaxZoom(3)).modify(_.requestGapMs).setTo(BasemapBathymetryConfig.RequestGapMs(0)).modify(_.onDemand).setTo(base.basemap.bathymetry.onDemand.modify(_.enabled).setTo(BasemapOnDemandConfig.Enabled(true)).modify(_.maxZoom).setTo(BasemapOnDemandConfig.MaxZoom(3)).modify(_.requestsPerSecond).setTo(BasemapOnDemandConfig.RequestsPerSecond((1000: Double)))
        )
      )
    )
    val router = new RangeHttp:
      private def pick(url: RangeHttp.Url) = if url.value.startsWith("fake://v") then v else t
      def text(url: RangeHttp.Url) =
        if url.value == "fake://ne/ranges.geojson" then Right(BasemapServiceSuite.Ranges)
        else if url.value == "fake://ne/rivers.geojson" then Right(BasemapServiceSuite.Rivers)
        else v.text(url)
      def bytes(url: RangeHttp.Url, o: ByteOffset, l: ByteCount) = pick(url).bytes(url, o, l)
      def download(url: RangeHttp.Url, o: ByteOffset, l: ByteCount, to: Reservation) = pick(url).download(url, o, l, to)
      override def file(url: RangeHttp.Url) =
        val Sea = "fake://sea/(\\d+)/(\\d+)/(\\d+).png".r
        url.value match
          case Sea(z, x, y) => Right(Option.when(x != "0")(RangeHttp.ResponseBytes(s"PNG$z/$x/$y".getBytes)))
          case _            => Left(RangeHttp.Problem(s"${url.value}: unexpected"))
    val ds =
      val d = testPool(cfg.db)
      Migrate.run(d); d
    def await(id: SpatialHierarchyResult.Job.Id, left: Int = 400): JobInfo =
      val j = Transactor(ds).read(JobRepo.get(Job.Id.applyUnsafe(id.value))).getOrElse(fail(s"no job $id"))
      if j.isTerminal || left == 0 then j else { Thread.sleep(25); await(id, left - 1) }
    val svc = BasemapWiring.service(Deps(cfg, ds), () => router, _ => ByteCount.applyUnsafe(Long.MaxValue / 4))
    def fetched(q: Basemap.FetchQuery): Unit =
      val j = await(svc.fetch(q).fold(e => fail(e.message.value), _.id))
      assert(j.succeeded, j.toString)
    fetched(Basemap.FetchQuery())
    fetched(Basemap.FetchQuery(focus = Some(Basemap.Focus.Name("crete"))))
    fetched(Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("terrain"), maxZoom = Some(Basemap.Focus.MaxZoom(4)), bbox = Some(BBox.Text("22.5,37.4,23.2,37.9"))))
    List(
      (Basemap.Layer.Wire("vector"), Basemap.Zoom(4), Basemap.Tile.Column(9L), Basemap.Tile.Row(6L)),
      (Basemap.Layer.Wire("vector"), Basemap.Zoom(4), Basemap.Tile.Column(0L), Basemap.Tile.Row(0L)),
      (Basemap.Layer.Wire("terrain"), Basemap.Zoom(5), Basemap.Tile.Column(0L), Basemap.Tile.Row(0L)),
      (Basemap.Layer.Wire("terrain"), Basemap.Zoom(5), Basemap.Tile.Column(1L), Basemap.Tile.Row(1L)),
      (Basemap.Layer.Wire("bathymetry"), Basemap.Zoom(3), Basemap.Tile.Column(4L), Basemap.Tile.Row(3L))
    )
      .foreach((layer, z, x, y) => assert(svc.tile(layer, z, x, y).isRight, s"$layer $z/$x/$y"))
    assert(svc.status.available.value)
    assert(svc.remove(Basemap.RemoveQuery(Basemap.RemoveQuery.Layer("none"), focus = Some(Basemap.Focus.Name("crete")))).isRight)

  /** A file the acquisition spools for the converter. */
  private def spoolRun(): Unit =
    val base = AppConfig.loadWithUserFile(env, Some(configFile)).fold(e => fail(e.value), identity).config
    assert(
      Spool(
          Storage
            .at(Location.Spool, StorageLocations.spool(base.acquire.spoolDir))
        )
        .write(Citekey("zz_data_tree"), Spool.Extension("pdf"), Spool.Bytes("%PDF-1.4 spooled".getBytes(UTF_8)))
        .isRight
    )

  /** Every path under the root: `<kind> <relative path>[ <size>]`, sorted. A text file's size is taken with the root's
    * own path written as `ROOT` (the settings name it); an archive's is left out (its metadata carries the time it was
    * made).
    */
  private def tree(): String =
    Files
      .walk(root)
      .iterator
      .asScala
      .filter(_ != root)
      .map { p =>
        val rel = root.relativize(p).toString.replace('\\', '/')
        if Files.isSymbolicLink(p) then s"l $rel"
        else if Files.isDirectory(p) then s"d $rel/"
        else if rel.endsWith(".pmtiles") then s"f $rel"
        else if List(".conf", ".bak", ".json").exists(rel.endsWith) then
          s"f $rel ${Files.readString(p, UTF_8).replace(root.toString, "ROOT").getBytes(UTF_8).length}"
        else s"f $rel ${Files.size(p)}"
      }
      .toList
      .sortBy(_.drop(2))
      .mkString("", "\n", "\n")

  test("data-tree.txt: the files a run leaves in the data, cache and scratch directories"):
    serverRun()
    basemapRun()
    spoolRun()
    val actual = tree()
    if update then
      Files.createDirectories(oracle.getParent)
      val _ = Files.writeString(oracle, actual, UTF_8)
    else
      assert(Files.exists(oracle), s"$oracle is missing; write it with HISTMERSION_UPDATE_ORACLE=1")
      assertNoDiff(actual, Files.readString(oracle, UTF_8))
