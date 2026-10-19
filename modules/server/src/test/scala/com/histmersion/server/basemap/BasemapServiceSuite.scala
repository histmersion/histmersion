package com.histmersion.server.basemap

import com.histmersion.core.*
import com.histmersion.db.{JobRepo, Migrate}
import com.histmersion.server.Deps

import java.nio.file.{Files, Path}

/** The fetch JOB end to end, with a fake provider (a local archive behind [[BasemapFixtures.LocalRange]])
  * and a fake disk: a dry run, a fetch that writes both layers, the free-space refusal, the provider's
  * list of builds. Job rows need a test database (gated on HISTMERSION_TEST_DB_URL). */
class BasemapServiceSuite extends com.histmersion.db.DbSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = testDb.get))
    Migrate.run(d); d

  private def await(id: String, left: Int = 400): JobInfo =
    val j = JobRepo.get(ds, id).getOrElse(fail(s"no job $id"))
    if j.isTerminal || left == 0 then j else { Thread.sleep(25); await(id, left - 1) }

  /** A service over `dir`, its provider serving `vector`/`terrain` fixture archives from `src`. */
  private def service(dir: Path, free: Long = Long.MaxValue / 4): (BasemapService, BasemapFixtures.LocalRange, BasemapFixtures.LocalRange) =
    val src = Files.createTempDirectory("basemap-src")
    val v = BasemapFixtures.LocalRange(BasemapFixtures.vectorArchive(src.resolve("v.pmtiles"), 5),
      builds = """[{"key":"20261018.pmtiles","size":1},{"key":"20261019.pmtiles","size":-1}]""")
    val t = BasemapFixtures.LocalRange(BasemapFixtures.terrainArchive(src.resolve("t.pmtiles"), 5))
    val base = AppConfig.load()
    val cfg = base.copy(db = base.db.copy(url = testDb.get), basemap = base.basemap.copy(dir = dir.toString, requestGapMs = 0,
      bbox = "22,35,29,41", vector = base.basemap.vector.copy(url = "fake://v/{build}", maxZoom = 4),
      terrain = base.basemap.terrain.copy(url = "fake://t", maxZoom = 3),
      labels = base.basemap.labels.copy(url = "fake://ne/{file}.geojson", files = List("ranges", "rivers"), maxZoom = 6)))
    val router = new RangeHttp:
      private def pick(url: String) = if url.startsWith("fake://v") then v else t
      def text(url: String) =
        if url == "fake://ne/ranges.geojson" then Right(BasemapServiceSuite.Ranges)
        else if url == "fake://ne/rivers.geojson" then Right(BasemapServiceSuite.Rivers)
        else v.text(url)
      def bytes(url: String, o: Long, l: Long) = pick(url).bytes(url, o, l)
      def download(url: String, o: Long, l: Long, to: Path) = pick(url).download(url, o, l, to)
    (BasemapService(Deps(cfg, ds), () => router, _ => free), v, t)

  test("a dry run reads directories only and reports the sizes; nothing is written"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, v, _) = service(dir)
    val j = await(svc.fetch(BasemapFetchQuery(dryRun = true)).fold(e => fail(e.message), _.id))
    assert(j.succeeded, j.toString)
    assert(j.progress.exists(p => p.contains("would download") && p.contains("20261019.pmtiles")), j.progress.toString)
    assertEquals(v.downloads.get, 0)
    assert(!Files.exists(dir.resolve("vector.pmtiles")))

  test("a fetch writes every layer; the status and the tiles follow; rm removes them"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, _, _) = service(dir)
    val j = await(svc.fetch(BasemapFetchQuery()).fold(e => fail(e.message), _.id))
    assert(j.succeeded, j.toString)
    val st = svc.status
    assert(st.available, st.toString)
    assertEquals(st.layers.map(l => (l.layer, l.maxZoom)), List(("vector", Some(4)), ("terrain", Some(3)), ("labels", Some(6))))
    assertEquals(st.hints, Nil)
    assert(st.layers.head.kept.exists(_.startsWith("all but: roads; transit; buildings")), st.layers.head.kept.toString)
    val names = st.layers.find(_.layer == "labels").get
    assertEquals((names.source, names.tileType), (Some("Natural Earth"), Some("application/vnd.mapbox-vector-tile")))
    assert(names.attribution.contains("Natural Earth"), names.attribution)
    // the Pindus, its name in the tile of zoom 5 that holds 21°E 40°N (it shows from zoom 6)
    val (nb, _, nenc, _, ncode) = svc.tile("labels", 5, Basemap.tileX(21, 5), Basemap.tileY(40, 5)).fold(e => fail(e.message), identity)
    assertEquals((ncode.code, nenc), (200, Some("gzip")))
    assertEquals(Mvt.kinds(PmTiles.gunzip(nb).toOption.get, "physical"), Right(List("range")))
    assertEquals(Mvt.kinds(PmTiles.gunzip(nb).toOption.get, "rivers"), Right(List("river")))
    assertEquals(st.layers.head.source, Some("20261019.pmtiles"))
    assert(!Files.exists(dir.resolve(".vector.part")), "the parts are removed once assembled")
    val (bytes, mime, enc, _, code) = svc.tile("vector", 4, 9, 6).fold(e => fail(e.message), identity)
    assertEquals((mime, enc, code.code), ("application/vnd.mapbox-vector-tile", Some("gzip"), 200))
    val layers = Mvt.layerNames(PmTiles.gunzip(bytes).toOption.get).toOption.get
    assert(!layers.contains("roads") && !layers.contains("places"), layers.toString)
    assertEquals(svc.tile("vector", 4, 0, 0).map(_._5.code), Right(204))
    val gone = svc.remove("all").fold(e => fail(e.message), identity)
    assertEquals(gone.removed.size, 3)
    assert(!svc.status.available)

  test("an extract without the names: the status says a fetch of the labels adds them, and that fetch adds only them"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, v, _) = service(dir)
    assert(await(svc.fetch(BasemapFetchQuery(layer = "vector")).fold(e => fail(e.message), _.id)).succeeded)
    val before = svc.status
    assertEquals(before.hints.size, 1)
    assert(before.hints.head.contains("histmersion basemap fetch --layer labels"), before.hints.toString)
    val downloads = v.downloads.get
    val dry = await(svc.fetch(BasemapFetchQuery(layer = "labels", dryRun = true)).fold(e => fail(e.message), _.id))
    assert(dry.progress.exists(_.contains("would download 2 files")), dry.progress.toString)
    assert(!Files.exists(dir.resolve("labels.pmtiles")))
    val j = await(svc.fetch(BasemapFetchQuery(layer = "labels")).fold(e => fail(e.message), _.id))
    assert(j.succeeded, j.toString)
    assert(j.progress.exists(_.contains("labels (Natural Earth): 2 names in")), j.progress.toString)
    assertEquals(v.downloads.get, downloads, "the vector extract is not downloaded again")
    assertEquals(svc.status.hints, Nil)

  test("--keep-all stores the vector layer unfiltered; an extract from before the drop list is said, with the one fetch that broadens it"):
    val dir = Files.createTempDirectory("basemap")
    // an older extract: no record of what it kept
    BasemapFixtures.vectorArchive(dir.resolve("vector.pmtiles"), maxZoom = 3)
    val (svc, _, _) = service(dir)
    val old = svc.status
    assert(old.layers.head.kept.exists(_.contains("before the drop list")), old.layers.head.toString)
    assert(old.hints.exists(_.contains("histmersion basemap fetch --layer vector")), old.hints.toString)
    val j = await(svc.fetch(BasemapFetchQuery(layer = "vector", keepAll = true)).fold(e => fail(e.message), _.id))
    assert(j.succeeded, j.toString)
    val st = svc.status
    assertEquals(st.layers.head.kept, Some("everything (unfiltered)"))
    assert(!st.hints.exists(_.contains("--layer vector")), st.hints.toString)
    val (bytes, _, _, _, _) = svc.tile("vector", 4, 9, 6).fold(e => fail(e.message), identity)
    assert(Mvt.layerNames(PmTiles.gunzip(bytes).toOption.get).toOption.get.contains("roads"))

  test("refused when the disk would be left with less than basemap.min-free-gb"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, v, _) = service(dir, free = 1000)
    val j = await(svc.fetch(BasemapFetchQuery(layer = "vector")).fold(e => fail(e.message), _.id))
    assert(!j.succeeded)
    assert(j.error.exists(e => e.contains("refused") && e.contains("min-free-gb")), j.error.toString)
    assertEquals(v.downloads.get, 0)

  test("bad options are refused before a job starts"):
    val (svc, _, _) = service(Files.createTempDirectory("basemap"))
    assert(svc.fetch(BasemapFetchQuery(layer = "roads")).isLeft)
    assert(svc.fetch(BasemapFetchQuery(bbox = Some("10,10,5,5"))).isLeft)
    assert(svc.fetch(BasemapFetchQuery(maxZoom = Some(15))).isLeft)

object BasemapServiceSuite:
  /** Two Natural Earth-shaped files: a range in the region and one far away; a river in it. */
  val Ranges: String =
    """{"type":"FeatureCollection","features":[
      |{"type":"Feature","properties":{"FEATURECLA":"Range/mtn","NAME":"Pindus Mts.","NAME_EN":"Pindus","MIN_LABEL":6},
      | "geometry":{"type":"Polygon","coordinates":[[[20.6,39.4],[21.4,39.4],[21.4,40.6],[20.6,40.6],[20.6,39.4]]]}},
      |{"type":"Feature","properties":{"FEATURECLA":"Range/mtn","NAME":"Andes"},
      | "geometry":{"type":"Point","coordinates":[-70,-30]}}]}""".stripMargin
  val Rivers: String =
    """{"type":"FeatureCollection","features":[
      |{"type":"Feature","properties":{"featurecla":"River","name":"Aliákmon","name_en":"Haliacmon","min_label":5},
      | "geometry":{"type":"LineString","coordinates":[[20.9,40.4],[21.6,40.2],[22.6,40.5]]}},
      |{"type":"Feature","properties":{"featurecla":"Canal","name":"Corinth Canal"},
      | "geometry":{"type":"LineString","coordinates":[[22.95,37.93],[23.0,37.9]]}}]}""".stripMargin
