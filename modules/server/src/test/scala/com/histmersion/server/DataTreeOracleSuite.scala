package com.histmersion.server

import com.histmersion.util.settings.ConfigSource

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.db.{DbSuite, Migrate, TestDb}
import com.histmersion.util.jobs.JobRepo
import com.histmersion.util.basemap.{BasemapFixtures, BasemapServiceSuite, RangeHttp}
import com.histmersion.util.storage.Reservation
import org.ekrich.config.ConfigFactory
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The files a run of the server leaves in its directories, as a smoke run makes them: one scratch root holding the
  * settings file, the data directory, the converter's and the acquisition's spools, the backups and bundles directories
  * and the base map; a server started over it, a setting changed twice, a config reload, a document uploaded and
  * converted, a file spooled by an acquisition; the base map fetched, a focus region added, tiles read from the extract
  * and on demand, a focus region removed. Then every path under the root, relative, with its kind and a file's size, is
  * compared with `oracle/data-tree.txt`, recorded on the code before the file code moved behind the storage area.
  * `HISTMERSION_UPDATE_ORACLE=1` rewrites it. Skipped without HISTMERSION_TEST_DB_URL.
  */
class DataTreeOracleSuite extends DbSuite:
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
    val l = AppConfig.loadWithUserFile(env, Some(configFile)).fold(e => fail(e), identity)
    supervised {
      val b = Composition
        .server(
          l.config,
          "127.0.0.1",
          0,
          OfflineConverter(),
          source = Some((l, ConfigSource(() => AppConfig.loadWithUserFile(env, Some(configFile)))))
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
      BasemapFixtures.vectorArchive(src.resolve("v.pmtiles"), 5),
      builds = """[{"key":"20261018.pmtiles","size":1},{"key":"20261019.pmtiles","size":-1}]"""
    )
    val t = BasemapFixtures.LocalRange(BasemapFixtures.terrainArchive(src.resolve("t.pmtiles"), 5))
    val base = AppConfig.loadWithUserFile(env, Some(configFile)).fold(e => fail(e), identity).config
    val cfg = base.copy(
      basemap = base.basemap.copy(
        requestGapMs = 0,
        bbox = "22,35,29,41",
        vector = base.basemap.vector.copy(
          url = "fake://v/{build}",
          maxZoom = 4,
          onDemand = base.basemap.vector.onDemand.copy(enabled = true, maxZoom = 5, requestsPerSecond = 1000)
        ),
        terrain = base.basemap.terrain.copy(
          url = "fake://t",
          maxZoom = 3,
          onDemand = base.basemap.terrain.onDemand.copy(maxZoom = 5, requestsPerSecond = 1000),
          focusRegions = List("crete 23,34.8,26.5,35.7")
        ),
        labels =
          base.basemap.labels.copy(url = "fake://ne/{file}.geojson", files = List("ranges", "rivers"), maxZoom = 6),
        bathymetry = base.basemap.bathymetry.copy(
          url = "fake://sea/{z}/{x}/{y}.png",
          maxZoom = 3,
          requestGapMs = 0,
          onDemand = base.basemap.bathymetry.onDemand.copy(enabled = true, maxZoom = 3, requestsPerSecond = 1000)
        )
      )
    )
    val router = new RangeHttp:
      private def pick(url: String) = if url.startsWith("fake://v") then v else t
      def text(url: String) =
        if url == "fake://ne/ranges.geojson" then Right(BasemapServiceSuite.Ranges)
        else if url == "fake://ne/rivers.geojson" then Right(BasemapServiceSuite.Rivers)
        else v.text(url)
      def bytes(url: String, o: Long, l: Long) = pick(url).bytes(url, o, l)
      def download(url: String, o: Long, l: Long, to: Reservation) = pick(url).download(url, o, l, to)
      override def file(url: String) =
        val Sea = "fake://sea/(\\d+)/(\\d+)/(\\d+).png".r
        url match
          case Sea(z, x, y) => Right(Option.when(x != "0")(s"PNG$z/$x/$y".getBytes))
          case _            => Left(s"$url: unexpected")
    val ds =
      val d = testPool(cfg.db)
      Migrate.run(d); d
    def await(id: String, left: Int = 400): JobInfo =
      val j = JobRepo.get(ds, id).getOrElse(fail(s"no job $id"))
      if j.isTerminal || left == 0 then j else { Thread.sleep(25); await(id, left - 1) }
    val svc = BasemapWiring.service(Deps(cfg, ds), () => router, _ => Long.MaxValue / 4)
    def fetched(q: BasemapFetchQuery): Unit =
      val j = await(svc.fetch(q).fold(e => fail(e.message), _.id))
      assert(j.succeeded, j.toString)
    fetched(BasemapFetchQuery())
    fetched(BasemapFetchQuery(focus = Some("crete")))
    fetched(BasemapFetchQuery(layer = "terrain", maxZoom = Some(4), bbox = Some("22.5,37.4,23.2,37.9")))
    List(
      ("vector", 4, 9L, 6L),
      ("vector", 4, 0L, 0L),
      ("terrain", 5, 0L, 0L),
      ("terrain", 5, 1L, 1L),
      ("bathymetry", 3, 4L, 3L)
    )
      .foreach((layer, z, x, y) => assert(svc.tile(layer, z, x, y).isRight, s"$layer $z/$x/$y"))
    assert(svc.status.available)
    assert(svc.remove(BasemapRemoveQuery("none", focus = Some("crete"))).isRight)

  /** A file the acquisition spools for the converter. */
  private def spoolRun(): Unit =
    val base = AppConfig.loadWithUserFile(env, Some(configFile)).fold(e => fail(e), identity).config
    assert(
      com.histmersion.util.ocr
        .Spool(
          com.histmersion.util.storage.Storage
            .at(com.histmersion.util.storage.Location.Spool, StorageLocations.spool(base.acquire.spoolDir))
        )
        .write("zz_data_tree", "pdf", "%PDF-1.4 spooled".getBytes(UTF_8))
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
