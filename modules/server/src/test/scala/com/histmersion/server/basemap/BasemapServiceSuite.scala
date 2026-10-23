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
      terrain = base.basemap.terrain.copy(url = "fake://t", maxZoom = 3,
        onDemand = base.basemap.terrain.onDemand.copy(maxZoom = 5, requestsPerSecond = 1000), focusRegions = List("crete 23,34.8,26.5,35.7")),
      labels = base.basemap.labels.copy(url = "fake://ne/{file}.geojson", files = List("ranges", "rivers"), maxZoom = 6),
      bathymetry = base.basemap.bathymetry.copy(url = "fake://sea/{z}/{x}/{y}.png", maxZoom = 3, requestGapMs = 0)))
    val router = new RangeHttp:
      private def pick(url: String) = if url.startsWith("fake://v") then v else t
      def text(url: String) =
        if url == "fake://ne/ranges.geojson" then Right(BasemapServiceSuite.Ranges)
        else if url == "fake://ne/rivers.geojson" then Right(BasemapServiceSuite.Rivers)
        else v.text(url)
      def bytes(url: String, o: Long, l: Long) = pick(url).bytes(url, o, l)
      def download(url: String, o: Long, l: Long, to: Path) = pick(url).download(url, o, l, to)
      // the sea floor's tiles: whole files, one a tile; the bucket has none for x = 0
      override def file(url: String) =
        val Sea = "fake://sea/(\\d+)/(\\d+)/(\\d+).png".r
        url match
          case Sea(z, x, y) => BasemapServiceSuite.seaGets.incrementAndGet(); Right(Option.when(x != "0")(s"PNG$z/$x/$y".getBytes))
          case _            => Left(s"$url: unexpected")
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
    assertEquals(st.layers.map(l => (l.layer, l.maxZoom)), List(("vector", Some(4)), ("terrain", Some(3)), ("labels", Some(6)), ("bathymetry", Some(3))))
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
    // a subset: only the layers and geometries a style draws — the sea's name without the polygons
    val (sb, _, senc, _, scode) = svc.tile("vector", 4, 9, 6, Some("water:p,earth")).fold(e => fail(e.message), identity)
    assertEquals((scode.code, senc), (200, Some("gzip")))
    val st4 = PmTiles.gunzip(sb).toOption.get
    assertEquals(Mvt.layerNames(st4), Right(List("earth", "water")))
    assertEquals(Mvt.kinds(st4, "water"), Right(List("sea")))
    assert(sb.length < bytes.length, s"${sb.length} < ${bytes.length}")
    assertEquals(svc.tile("vector", 4, 9, 6, Some("roads")).map(_._5.code), Right(204), "nothing left: no content")
    assert(svc.tile("vector", 4, 9, 6, Some("water:q")).isLeft)
    assertEquals(svc.tile("terrain", 3, 4, 3, Some("water")).map(_._3), Right(None), "an image is never cut")
    val gone = svc.remove(BasemapRemoveQuery()).fold(e => fail(e.message), identity)
    assertEquals(gone.removed.size, 4)
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

  test("Readable needs roads, places and boundaries: the default download lacks them, the status says so and what to run; --keep-all has them"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, _, _) = service(dir)
    assertEquals(svc.status.styles.flatMap(_.missing), Nil, "nothing downloaded: nothing said to be missing (the whole map is)")
    assert(await(svc.fetch(BasemapFetchQuery(layer = "vector")).fold(e => fail(e.message), _.id)).succeeded)
    val st = svc.status
    assertEquals(st.layers.head.dropped, List("boundaries", "buildings", "places", "roads", "transit"))
    assertEquals(st.missing("readable"), List("boundaries", "places", "roads"))
    assertEquals(st.missing("physical"), Nil, "Physical is whole with the default download")
    val hint = st.hints.find(_.contains("--keep-all")).getOrElse(fail(st.hints.toString))
    assert(hint.startsWith("Readable needs boundaries, places and roads"), hint)
    assert(hint.contains("histmersion basemap fetch --layer vector --keep-all"), hint)
    assertEquals(BaseMapSource.resolve("auto", st), "local", "auto: Physical can be drawn whole")
    assert(BasemapMarkdown.status(st).contains("Readable (lacks boundaries, places, roads)"), BasemapMarkdown.status(st))
    assert(await(svc.fetch(BasemapFetchQuery(layer = "vector", keepAll = true)).fold(e => fail(e.message), _.id)).succeeded)
    val all = svc.status
    assertEquals((all.layers.head.dropped, all.styles.flatMap(_.missing)), (Nil, Nil))
    assert(!all.hints.exists(_.contains("--keep-all")), all.hints.toString)
    assertEquals(BaseMapSource.resolve("auto", all), "local")
    assertEquals(BaseMapSource.resolve("online", all), "online")

  test("refused when the disk would be left with less than basemap.min-free-gb"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, v, _) = service(dir, free = 1000)
    val j = await(svc.fetch(BasemapFetchQuery(layer = "vector")).fold(e => fail(e.message), _.id))
    assert(!j.succeeded)
    assert(j.error.exists(e => e.contains("refused") && e.contains("min-free-gb")), j.error.toString)
    assertEquals(v.downloads.get, 0)

  test("the relief deeper than the extract: a focus region kept apart, the rest read on demand and cached; rm of each"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, _, t) = service(dir)
    assert(await(svc.fetch(BasemapFetchQuery(layer = "terrain")).fold(e => fail(e.message), _.id)).succeeded)
    val base = svc.status.layers.find(_.layer == "terrain").get
    assertEquals((base.maxZoom, base.deepest, base.onDemand.map(_.tiles)), (Some(3), Some(5), Some(0L)))
    // a focus region (named in the config): zooms 4–5 only, beside the extract, which stays as it was
    val dry = await(svc.fetch(BasemapFetchQuery(focus = Some("crete"), dryRun = true)).fold(e => fail(e.message), _.id))
    assert(dry.progress.exists(p => p.contains("focus 'crete'") && p.contains("would download")), dry.progress.toString)
    val j = await(svc.fetch(BasemapFetchQuery(focus = Some("crete"))).fold(e => fail(e.message), _.id))
    assert(j.succeeded, j.toString)
    val st = svc.status.layers.find(_.layer == "terrain").get
    assertEquals(st.maxZoom, Some(3), "the extract is not replaced")
    assertEquals(st.focus.map(f => (f.name, f.minZoom, f.maxZoom)), List(("crete", 4, 5)))
    // a tile of the focus region: from its archive, the provider not asked
    val asked = t.requests.get
    val (fb, fmime, _, _, fcode) = svc.tile("terrain", 5, Basemap.tileX(24.5, 5), Basemap.tileY(35.2, 5)).fold(e => fail(e.message), identity)
    assertEquals((fcode.code, fmime, String(fb)), (200, "image/webp", s"RIFF5/${Basemap.tileX(24.5, 5)}/${Basemap.tileY(35.2, 5)}"))
    assertEquals(t.requests.get, asked)
    // elsewhere at zoom 5: read now from the provider, then cached
    val (ob, _, _, _, ocode) = svc.tile("terrain", 5, 0, 0).fold(e => fail(e.message), identity)
    assertEquals((ocode.code, String(ob)), (200, "RIFF5/0/0"))
    assertEquals(svc.status.layers.find(_.layer == "terrain").get.onDemand.map(_.tiles), Some(1L))
    // deeper than on-demand goes: none
    assertEquals(svc.tile("terrain", 6, 0, 0).map(_._5.code), Right(204))
    // a regional relief fetch deeper than the extract becomes a focus region of its own
    assert(await(svc.fetch(BasemapFetchQuery(layer = "terrain", maxZoom = Some(4), bbox = Some("22.5,37.4,23.2,37.9")))
      .fold(e => fail(e.message), _.id)).succeeded)
    assertEquals(svc.status.layers.find(_.layer == "terrain").get.focus.map(_.name), List("bbox-22p5-37p4-23p2-37p9", "crete"))
    assert(svc.fetch(BasemapFetchQuery(focus = Some("crete"), terrainMaxZoom = Some(3))).isLeft, "not deeper than the extract")
    val f = svc.remove(BasemapRemoveQuery("none", focus = Some("crete"))).fold(e => fail(e.message), identity)
    assertEquals(f.removed.map(java.nio.file.Path.of(_).getFileName.toString), List("crete.pmtiles"))
    val c = svc.remove(BasemapRemoveQuery("none", cache = true)).fold(e => fail(e.message), identity)
    assertEquals(c.removed.size, 1)
    assertEquals(svc.status.layers.find(_.layer == "terrain").get.onDemand.map(_.tiles), Some(0L))
    assert(svc.status.layers.find(_.layer == "terrain").get.present, "the extract stays")
    assert(svc.remove(BasemapRemoveQuery("none", focus = Some("nowhere"))).isLeft)

  test("the sea floor: one GET a tile of the region, those the bucket has kept; a status entry, its tiles served; resumed, not read again"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, _, _) = service(dir)
    val dry = await(svc.fetch(BasemapFetchQuery(layer = "bathymetry", dryRun = true)).fold(e => fail(e.message), _.id))
    assert(dry.progress.exists(_.contains("bathymetry (zoom 0–3): would download")), dry.progress.toString)
    val before = BasemapServiceSuite.seaGets.get
    val j = await(svc.fetch(BasemapFetchQuery(layer = "bathymetry")).fold(e => fail(e.message), _.id))
    assert(j.succeeded, j.toString)
    val asked = BasemapServiceSuite.seaGets.get - before
    assertEquals(asked.toLong, Basemap.tileCount(BBox(22, 35, 29, 41), 0, 3))
    val sea = svc.status.layers.find(_.layer == "bathymetry").get
    assertEquals((sea.present, sea.maxZoom, sea.tileType), (true, Some(3), Some("image/png")))
    assert(sea.attribution.contains("ETOPO1"), sea.attribution)
    val (b, mime, _, _, code) = svc.tile("bathymetry", 3, 4, 3).fold(e => fail(e.message), identity)
    assertEquals((code.code, mime, String(b)), (200, "image/png", "PNG3/4/3"))
    assertEquals(svc.tile("bathymetry", 0, 0, 0).map(_._5.code), Right(204), "the bucket had none")
    assert(!Files.exists(dir.resolve(".bathymetry.part")), "the parts are removed once written")

  test("bad options are refused before a job starts"):
    val (svc, _, _) = service(Files.createTempDirectory("basemap"))
    assert(svc.fetch(BasemapFetchQuery(layer = "roads")).isLeft)
    assert(svc.fetch(BasemapFetchQuery(bbox = Some("10,10,5,5"))).isLeft)
    assert(svc.fetch(BasemapFetchQuery(maxZoom = Some(15))).isLeft)

object BasemapServiceSuite:
  val seaGets = java.util.concurrent.atomic.AtomicInteger(0)
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
