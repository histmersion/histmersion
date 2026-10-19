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
      terrain = base.basemap.terrain.copy(url = "fake://t", maxZoom = 3)))
    val router = new RangeHttp:
      private def pick(url: String) = if url.startsWith("fake://v") then v else t
      def text(url: String) = v.text(url)
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

  test("a fetch writes both layers; the status and the tiles follow; rm removes them"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, _, _) = service(dir)
    val j = await(svc.fetch(BasemapFetchQuery()).fold(e => fail(e.message), _.id))
    assert(j.succeeded, j.toString)
    val st = svc.status
    assert(st.available, st.toString)
    assertEquals(st.layers.map(l => (l.layer, l.maxZoom)), List(("vector", Some(4)), ("terrain", Some(3))))
    assertEquals(st.layers.head.source, Some("20261019.pmtiles"))
    assert(!Files.exists(dir.resolve(".vector.part")), "the parts are removed once assembled")
    val (bytes, mime, enc, _, code) = svc.tile("vector", 4, 9, 6).fold(e => fail(e.message), identity)
    assertEquals((mime, enc, code.code), ("application/vnd.mapbox-vector-tile", Some("gzip"), 200))
    val layers = Mvt.layerNames(PmTiles.gunzip(bytes).toOption.get).toOption.get
    assert(!layers.contains("roads") && !layers.contains("places"), layers.toString)
    assertEquals(svc.tile("vector", 4, 0, 0).map(_._5.code), Right(204))
    val gone = svc.remove("all").fold(e => fail(e.message), identity)
    assertEquals(gone.removed.size, 2)
    assert(!svc.status.available)

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
