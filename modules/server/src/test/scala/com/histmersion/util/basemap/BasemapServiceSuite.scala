package com.histmersion
package util.basemap
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.database.Transactor

import com.histmersion.{ByteCount, ByteOffset}
import com.histmersion.api.BBox
import com.histmersion.api.basemap.BasemapApi
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.Migrate
import com.histmersion.util.jobs.JobRepo
import com.histmersion.server.{BasemapWiring, Deps}
import com.histmersion.util.storage.Reservation

import java.nio.file.{Files, Path}
import com.histmersion.api.basemap.{Basemap, BasemapMarkdown, BaseMapSource}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.util.jobs.Job
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The fetch JOB end to end, with a fake provider (a local archive behind [[BasemapFixtures.LocalRange]]) and a fake
  * disk: a dry run, a fetch that writes both layers, the free-space refusal, the provider's list of builds. Job rows
  * need a test database (gated on HISTMERSION_TEST_DB_URL).
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class BasemapServiceSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def await(id: SpatialHierarchyResult.Job.Id, left: Int = 400): JobInfo =
    val j = Transactor(ds)
      .read(JobRepo.get(Job.Id.applyUnsafe(id.value)))
      .getOrElse(fail(s"no job $id"))
    if j.isTerminal || left == 0 then j else { Thread.sleep(25); await(id, left - 1) }

  /** The tile as a vector tile's bytes, gunzipped. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def plain(b: BasemapApi.Tile.Bytes): PmTiles.PlainBytes =
    PmTiles.gunzip(PmTiles.CompressedBytes(b.value)).toOption.get

  /** A service over `dir`, its provider serving `vector`/`terrain` fixture archives from `src`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def service(
      dir: Path,
      free: ByteCount = ByteCount.applyUnsafe(Long.MaxValue / 4),
      seaAndMap: Boolean = true
  ): (BasemapService, BasemapFixtures.LocalRange, BasemapFixtures.LocalRange) =
    val src = Files.createTempDirectory("basemap-src")
    val v = BasemapFixtures.LocalRange(
      BasemapFixtures.vectorArchive(src.resolve("v.pmtiles"), PmTiles.Zoom(5)),
      builds = RangeHttp.ResponseText("""[{"key":"20261018.pmtiles","size":1},{"key":"20261019.pmtiles","size":-1}]""")
    )
    val t = BasemapFixtures.LocalRange(BasemapFixtures.terrainArchive(src.resolve("t.pmtiles"), PmTiles.Zoom(5)))
    val base = AppConfig.load()
    val cfg = base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.basemap)
      .setTo(
        base.basemap
          .modify(_.dir)
          .setTo(BasemapConfig.Dir(dir.toString))
          .modify(_.requestGapMs)
          .setTo(BasemapConfig.RequestGapMs(0))
          .modify(_.bbox)
          .setTo(BasemapConfig.Bbox("22,35,29,41"))
          .modify(_.vector)
          .setTo(
            base.basemap.vector
              .modify(_.url)
              .setTo(BasemapVectorConfig.Url("fake://v/{build}"))
              .modify(_.maxZoom)
              .setTo(BasemapVectorConfig.MaxZoom(4))
              .modify(_.onDemand)
              .setTo(
                base.basemap.vector.onDemand
                  .modify(_.enabled)
                  .setTo(BasemapOnDemandConfig.Enabled(seaAndMap))
                  .modify(_.maxZoom)
                  .setTo(BasemapOnDemandConfig.MaxZoom(5))
                  .modify(_.requestsPerSecond)
                  .setTo(BasemapOnDemandConfig.RequestsPerSecond(1000))
              )
          )
          .modify(_.terrain)
          .setTo(
            base.basemap.terrain
              .modify(_.url)
              .setTo(BasemapTerrainConfig.Url("fake://t"))
              .modify(_.maxZoom)
              .setTo(BasemapTerrainConfig.MaxZoom(3))
              .modify(_.onDemand)
              .setTo(
                base.basemap.terrain.onDemand
                  .modify(_.maxZoom)
                  .setTo(BasemapOnDemandConfig.MaxZoom(5))
                  .modify(_.requestsPerSecond)
                  .setTo(BasemapOnDemandConfig.RequestsPerSecond(1000))
              )
              .modify(_.focusRegions)
              .setTo(List(BasemapTerrainConfig.FocusRegions("crete 23,34.8,26.5,35.7")))
          )
          .modify(_.labels)
          .setTo(
            base.basemap.labels
              .modify(_.url)
              .setTo(BasemapLabelsConfig.Url("fake://ne/{file}.geojson"))
              .modify(_.files)
              .setTo(List(BasemapLabelsConfig.Files("ranges"), BasemapLabelsConfig.Files("rivers")))
              .modify(_.maxZoom)
              .setTo(BasemapLabelsConfig.MaxZoom(6))
          )
          .modify(_.bathymetry)
          .setTo(
            base.basemap.bathymetry
              .modify(_.url)
              .setTo(BasemapBathymetryConfig.Url("fake://sea/{z}/{x}/{y}.png"))
              .modify(_.maxZoom)
              .setTo(BasemapGebcoConfig.MaxZoom(3))
              .modify(_.requestGapMs)
              .setTo(BasemapBathymetryConfig.RequestGapMs(0))
              .modify(_.onDemand)
              .setTo(
                base.basemap.bathymetry.onDemand
                  .modify(_.enabled)
                  .setTo(BasemapOnDemandConfig.Enabled(seaAndMap))
                  .modify(_.maxZoom)
                  .setTo(BasemapOnDemandConfig.MaxZoom(3))
                  .modify(_.requestsPerSecond)
                  .setTo(BasemapOnDemandConfig.RequestsPerSecond(1000))
              )
          )
      )
    val router = new RangeHttp:
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      private def pick(url: RangeHttp.Url) = if url.value.startsWith("fake://v") then v else t
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def text(url: RangeHttp.Url) =
        if url.value == "fake://ne/ranges.geojson" then Right(BasemapServiceSuite.Ranges)
        else if url.value == "fake://ne/rivers.geojson" then Right(BasemapServiceSuite.Rivers)
        else v.text(url)
      def bytes(url: RangeHttp.Url, o: ByteOffset, l: ByteCount) = pick(url).bytes(url, o, l)
      def download(url: RangeHttp.Url, o: ByteOffset, l: ByteCount, to: Reservation) = pick(url).download(url, o, l, to)
      // the sea floor's tiles: whole files, one a tile; the bucket has none for x = 0
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
      override def file(url: RangeHttp.Url) =
        val Sea = "fake://sea/(\\d+)/(\\d+)/(\\d+).png".r
        url.value match
          case Sea(z, x, y) =>
            BasemapServiceSuite.seaGets.incrementAndGet()
            Right(Option.when(x != "0")(RangeHttp.ResponseBytes(s"PNG$z/$x/$y".getBytes)))
          case _ => Left(Problem.Unclassified(Legacy.Text(s"${url.value}: unexpected")))
    (
      BasemapWiring.service(
        Deps(
          cfg,
          ds,
          ds,
          clock = java.time.Clock.fixed(java.time.Instant.parse("2000-01-02T03:04:05Z"), java.time.ZoneOffset.UTC)
        ),
        () => router,
        _ => free
      ),
      v,
      t
    )

  test("a dry run reads directories only and reports the sizes; nothing is written"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, v, _) = service(dir)
    val j = await(
      svc
        .fetch(Basemap.FetchQuery(dryRun = Basemap.FetchQuery.DryRun.DryRun))
        .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
    )
    assert(j.succeeded, j.toString)
    assert(
      j.progress.exists(p => p.value.contains("would download") && p.value.contains("20261019.pmtiles")),
      j.progress.toString
    )
    // the size of each zoom (#3760): a download ahead can stop at a cheaper zoom
    assert(
      j.progress.exists(p => p.value.contains("by zoom — zoom 0: 1 tile, ") && p.value.contains(" · zoom 4: ")),
      j.progress.toString
    )
    assertEquals(v.downloads.get, 0)
    assert(!Files.exists(dir.resolve("vector.pmtiles")))

  test("a fetch writes every layer; the status and the tiles follow; rm removes them"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, _, _) = service(dir)
    val j = await(svc.fetch(Basemap.FetchQuery()).fold(e => fail(e.problems.sentences.mkString("; ")), _.id))
    assert(j.succeeded, j.toString)
    val st = svc.status
    locally {
      assert(st.available.available, st.toString)
      assertEquals(
        st.layers.map(l => l.layer -> l.fetchedAt),
        List("vector", "terrain", "labels", "bathymetry").map(_ -> Some("2000-01-02T03:04:05Z"))
      )
    }
    assertEquals(
      st.layers.map(l => (l.layer, l.maxZoom)),
      List(("vector", Some(4)), ("terrain", Some(3)), ("labels", Some(6)), ("bathymetry", Some(3)))
    )
    // the default download draws Physical whole; Readable's roads, places and borders need --keep-all, and the status says so
    assertEquals(st.hints.size, 1, st.hints.toString)
    assert(
      st.hints.head.value.startsWith("Readable needs boundaries, places and roads") && st.hints.head.value
        .contains("--keep-all"),
      st.hints.toString
    )
    assert(
      st.layers.head.kept.exists(_.value.startsWith("all but: roads; transit; buildings")),
      st.layers.head.kept.toString
    )
    val names = st.layers.find(_.layer == Basemap.Layer.Wire("labels")).get
    assertEquals((names.source, names.tileType), (Some("Natural Earth"), Some("application/vnd.mapbox-vector-tile")))
    assert(names.attribution.value.contains("Natural Earth"), names.attribution)
    // the Pindus, its name in the tile of zoom 5 that holds 21°E 40°N (it shows from zoom 6)
    val (nb, _, nenc, _, ncode) =
      svc
        .tile(
          Basemap.Layer.Wire("labels"),
          Basemap.Zoom(5),
          Basemap.tileX(Longitude(21), Basemap.Zoom(5)),
          Basemap.tileY(Latitude(40), Basemap.Zoom(5))
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((ncode.code, nenc), (200, Some("gzip")))
    assertEquals(Mvt.kinds(plain(nb), Mvt.LayerName("physical")), Right(List("range")))
    assertEquals(Mvt.kinds(plain(nb), Mvt.LayerName("rivers")), Right(List("river")))
    assertEquals(st.layers.head.source, Some("20261019.pmtiles"))
    assert(!Files.exists(dir.resolve(".vector.part")), "the parts are removed once assembled")
    val (bytes, mime, enc, _, code) = svc
      .tile(Basemap.Layer.Wire("vector"), Basemap.Zoom(4), Basemap.Tile.Column(9), Basemap.Tile.Row(6))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((mime, enc, code.code), ("application/vnd.mapbox-vector-tile", Some("gzip"), 200))
    val layers = Mvt.layerNames(plain(bytes)).toOption.get
    assert(!layers.contains(Mvt.LayerName("roads")) && !layers.contains(Mvt.LayerName("places")), layers.toString)
    // outside the region: read on demand (#3760; switched off it is a 204 — the on-demand test)
    assertEquals(
      svc
        .tile(Basemap.Layer.Wire("vector"), Basemap.Zoom(4), Basemap.Tile.Column(0), Basemap.Tile.Row(0))
        .map(_._5.code),
      Right(200)
    )
    // a subset: only the layers and geometries a style draws — the sea's name without the polygons
    val (sb, _, senc, _, scode) =
      svc
        .tile(
          Basemap.Layer.Wire("vector"),
          Basemap.Zoom(4),
          Basemap.Tile.Column(9),
          Basemap.Tile.Row(6),
          Some(Basemap.Subset.Spec("water:p,earth"))
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((scode.code, senc), (200, Some("gzip")))
    val st4 = plain(sb)
    assertEquals(Mvt.layerNames(st4), Right(List("earth", "water")))
    assertEquals(Mvt.kinds(st4, Mvt.LayerName("water")), Right(List("sea")))
    assert(sb.value.length < bytes.value.length, s"${sb.value.length} < ${bytes.value.length}")
    assertEquals(
      svc
        .tile(
          Basemap.Layer.Wire("vector"),
          Basemap.Zoom(4),
          Basemap.Tile.Column(9),
          Basemap.Tile.Row(6),
          Some(Basemap.Subset.Spec("roads"))
        )
        .map(_._5.code),
      Right(204),
      "nothing left: no content"
    )
    assert(
      svc
        .tile(
          Basemap.Layer.Wire("vector"),
          Basemap.Zoom(4),
          Basemap.Tile.Column(9),
          Basemap.Tile.Row(6),
          Some(Basemap.Subset.Spec("water:q"))
        )
        .isLeft
    )
    assertEquals(
      svc
        .tile(
          Basemap.Layer.Wire("terrain"),
          Basemap.Zoom(3),
          Basemap.Tile.Column(4),
          Basemap.Tile.Row(3),
          Some(Basemap.Subset.Spec("water"))
        )
        .map(_._3),
      Right(None),
      "an image is never cut"
    )
    val gone = svc.remove(Basemap.RemoveQuery()).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    // the four layers, and the map cache the tile outside the region filled
    assertEquals(
      gone.removed.map(r => Path.of(r.value).getFileName.toString).sorted,
      List("bathymetry.pmtiles", "labels.pmtiles", "terrain.pmtiles", "vector-cache", "vector.pmtiles")
    )
    assert(!svc.status.available.available)

  test("an extract without the names: the status says a fetch of the labels adds them, and that fetch adds only them"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, v, _) = service(dir)
    assert(
      await(
        svc
          .fetch(Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("vector")))
          .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
      ).succeeded
    )
    val before = svc.status
    // the names' hint, beside Readable's (the default download leaves its roads, places and borders out)
    assertEquals(before.hints.size, 2, before.hints.toString)
    assert(before.hints.exists(_.value.contains("histmersion basemap fetch --layer labels")), before.hints.toString)
    val readable = before.hints.filter(_.value.startsWith("Readable needs"))
    assertEquals(readable.size, 1, before.hints.toString)
    val downloads = v.downloads.get
    val dry = await(
      svc
        .fetch(
          Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("labels"), dryRun = Basemap.FetchQuery.DryRun.DryRun)
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
    )
    assert(dry.progress.exists(_.value.contains("would download 2 files")), dry.progress.toString)
    assert(!Files.exists(dir.resolve("labels.pmtiles")))
    val j = await(
      svc
        .fetch(Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("labels")))
        .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
    )
    assert(j.succeeded, j.toString)
    assert(j.progress.exists(_.value.contains("labels (Natural Earth): 2 names in")), j.progress.toString)
    assertEquals(v.downloads.get, downloads, "the vector extract is not downloaded again")
    assertEquals(svc.status.hints, readable)

  test(
    "--keep-all stores the vector layer unfiltered; an extract from before the drop list is said, with the one fetch that broadens it"
  ):
    val dir = Files.createTempDirectory("basemap")
    // an older extract: no record of what it kept
    BasemapFixtures.vectorArchive(dir.resolve("vector.pmtiles"), maxZoom = PmTiles.Zoom(3))
    val (svc, _, _) = service(dir)
    val old = svc.status
    assert(old.layers.head.kept.exists(_.value.contains("before the drop list")), old.layers.head.toString)
    assert(old.hints.exists(_.value.contains("histmersion basemap fetch --layer vector")), old.hints.toString)
    val j = await(
      svc
        .fetch(
          Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("vector"), keepAll = Basemap.FetchQuery.KeepAll.KeepAll)
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
    )
    assert(j.succeeded, j.toString)
    val st = svc.status
    assertEquals(st.layers.head.kept, Some("everything (unfiltered)"))
    assert(!st.hints.exists(_.value.contains("--layer vector")), st.hints.toString)
    val (bytes, _, _, _, _) = svc
      .tile(Basemap.Layer.Wire("vector"), Basemap.Zoom(4), Basemap.Tile.Column(9), Basemap.Tile.Row(6))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(Mvt.layerNames(plain(bytes)).toOption.get.contains(Mvt.LayerName("roads")))

  test(
    "Readable needs roads, places and boundaries: the default download lacks them, the status says so and what to run; --keep-all has them"
  ):
    val dir = Files.createTempDirectory("basemap")
    val (svc, _, _) = service(dir)
    assertEquals(
      svc.status.styles.flatMap(_.missing),
      Nil,
      "nothing downloaded: nothing said to be missing (the whole map is)"
    )
    assert(
      await(
        svc
          .fetch(Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("vector")))
          .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
      ).succeeded
    )
    val st = svc.status
    assertEquals(st.layers.head.dropped, List("boundaries", "buildings", "places", "roads", "transit"))
    assertEquals(st.missing(Basemap.StyleState.Id("readable")), List("boundaries", "places", "roads"))
    assertEquals(st.missing(Basemap.StyleState.Id("physical")), Nil, "Physical is whole with the default download")
    val hint = st.hints.find(_.value.contains("--keep-all")).map(_.value).getOrElse(fail(st.hints.toString))
    assert(hint.startsWith("Readable needs boundaries, places and roads"), hint)
    assert(hint.contains("histmersion basemap fetch --layer vector --keep-all"), hint)
    assertEquals(BaseMapSource.resolve(BaseMapSource.Wire("auto"), st), "local", "auto: Physical can be drawn whole")
    assert(
      BasemapMarkdown.status(st).contains("Readable (lacks boundaries, places, roads)"),
      BasemapMarkdown.status(st)
    )
    assert(
      await(
        svc
          .fetch(
            Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("vector"), keepAll = Basemap.FetchQuery.KeepAll.KeepAll)
          )
          .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
      ).succeeded
    )
    val all = svc.status
    assertEquals((all.layers.head.dropped, all.styles.flatMap(_.missing)), (Nil, Nil))
    assert(!all.hints.exists(_.value.contains("--keep-all")), all.hints.toString)
    assertEquals(BaseMapSource.resolve(BaseMapSource.Wire("auto"), all), "local")
    assertEquals(BaseMapSource.resolve(BaseMapSource.Wire("online"), all), "online")

  test("refused when the disk would be left with less than basemap.min-free-gb"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, v, _) = service(dir, free = ByteCount(1000))
    val j = await(
      svc
        .fetch(Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("vector")))
        .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
    )
    assert(!j.succeeded)
    assert(
      j.problems.exists(_.sentences.exists(e => e.contains("refused") && e.contains("min-free-gb"))),
      j.problems.toString
    )
    assertEquals(v.downloads.get, 0)

  test("the relief deeper than the extract: a focus region kept apart, the rest read on demand and cached; rm of each"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, _, t) = service(dir)
    assert(
      await(
        svc
          .fetch(Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("terrain")))
          .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
      ).succeeded
    )
    val base = svc.status.layers.find(_.layer == Basemap.Layer.Wire("terrain")).get
    assertEquals((base.maxZoom, base.deepest, base.onDemand.map(_.tiles)), (Some(3), Some(5), Some(0L)))
    // a focus region (named in the config): zooms 4–5 only, beside the extract, which stays as it was
    val dry =
      await(
        svc
          .fetch(
            Basemap.FetchQuery(focus = Some(Basemap.Focus.Name("crete")), dryRun = Basemap.FetchQuery.DryRun.DryRun)
          )
          .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
      )
    assert(
      dry.progress.exists(p => p.value.contains("focus 'crete'") && p.value.contains("would download")),
      dry.progress.toString
    )
    val j = await(
      svc
        .fetch(Basemap.FetchQuery(focus = Some(Basemap.Focus.Name("crete"))))
        .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
    )
    assert(j.succeeded, j.toString)
    val st = svc.status.layers.find(_.layer == Basemap.Layer.Wire("terrain")).get
    assertEquals(st.maxZoom, Some(3), "the extract is not replaced")
    assertEquals(st.focus.map(f => (f.name, f.minZoom, f.maxZoom)), List(("crete", 4, 5)))
    // a tile of the focus region: from its archive, the provider not asked
    val asked = t.requests.get
    val (fb, fmime, _, _, fcode) =
      svc
        .tile(
          Basemap.Layer.Wire("terrain"),
          Basemap.Zoom(5),
          Basemap.tileX(Longitude(24.5), Basemap.Zoom(5)),
          Basemap.tileY(Latitude(35.2), Basemap.Zoom(5))
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(
      (fcode.code, fmime, String(fb.value)),
      (
        200,
        "image/webp",
        s"RIFF5/${Basemap.tileX(Longitude(24.5), Basemap.Zoom(5))}/${Basemap.tileY(Latitude(35.2), Basemap.Zoom(5))}"
      )
    )
    assertEquals(t.requests.get, asked)
    // elsewhere at zoom 5: read now from the provider, then cached
    val (ob, _, _, _, ocode) = svc
      .tile(Basemap.Layer.Wire("terrain"), Basemap.Zoom(5), Basemap.Tile.Column(0), Basemap.Tile.Row(0))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((ocode.code, String(ob.value)), (200, "RIFF5/0/0"))
    assertEquals(svc.status.layers.find(_.layer == Basemap.Layer.Wire("terrain")).get.onDemand.map(_.tiles), Some(1L))
    // deeper than on-demand goes: none
    assertEquals(
      svc
        .tile(Basemap.Layer.Wire("terrain"), Basemap.Zoom(6), Basemap.Tile.Column(0), Basemap.Tile.Row(0))
        .map(_._5.code),
      Right(204)
    )
    // a regional relief fetch deeper than the extract becomes a focus region of its own
    assert(
      await(
        svc
          .fetch(
            Basemap.FetchQuery(
              layer = Basemap.FetchQuery.Layer("terrain"),
              maxZoom = Some(Basemap.Focus.MaxZoom(4)),
              bbox = Some(BBox.Text("22.5,37.4,23.2,37.9"))
            )
          )
          .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
      ).succeeded
    )
    assertEquals(
      svc.status.layers.find(_.layer == Basemap.Layer.Wire("terrain")).get.focus.map(_.name),
      List("bbox-22p5-37p4-23p2-37p9", "crete")
    )
    assert(
      svc
        .fetch(
          Basemap.FetchQuery(
            focus = Some(Basemap.Focus.Name("crete")),
            terrainMaxZoom = Some(Basemap.FetchQuery.TerrainMaxZoom(3))
          )
        )
        .isLeft,
      "not deeper than the extract"
    )
    val f = svc
      .remove(Basemap.RemoveQuery(Basemap.RemoveQuery.Layer("none"), focus = Some(Basemap.Focus.Name("crete"))))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(f.removed.map(r => java.nio.file.Path.of(r.value).getFileName.toString), List("crete.pmtiles"))
    val c = svc
      .remove(Basemap.RemoveQuery(Basemap.RemoveQuery.Layer("none"), cache = Basemap.RemoveQuery.Cache.Cache))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(c.removed.size, 1)
    assertEquals(svc.status.layers.find(_.layer == Basemap.Layer.Wire("terrain")).get.onDemand.map(_.tiles), Some(0L))
    assert(svc.status.layers.find(_.layer == Basemap.Layer.Wire("terrain")).get.present.present, "the extract stays")
    assert(
      svc
        .remove(Basemap.RemoveQuery(Basemap.RemoveQuery.Layer("none"), focus = Some(Basemap.Focus.Name("nowhere"))))
        .isLeft
    )

  test(
    "the sea floor: one GET a tile of the region, those the bucket has kept; a status entry, its tiles served; resumed, not read again"
  ):
    val dir = Files.createTempDirectory("basemap")
    val (svc, _, _) = service(dir)
    val dry = await(
      svc
        .fetch(
          Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("bathymetry"), dryRun = Basemap.FetchQuery.DryRun.DryRun)
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
    )
    assert(dry.progress.exists(_.value.contains("bathymetry (zoom 0–3): would download")), dry.progress.toString)
    val before = BasemapServiceSuite.seaGets.get
    val j = await(
      svc
        .fetch(Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("bathymetry")))
        .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
    )
    assert(j.succeeded, j.toString)
    val asked = BasemapServiceSuite.seaGets.get - before
    assertEquals(
      asked.toLong,
      Basemap.tileCount(
        BBox(Longitude(22), Latitude(35), Longitude(29), Latitude(41)),
        Basemap.Zoom(0),
        Basemap.Zoom(3)
      )
    )
    val sea = svc.status.layers.find(_.layer == Basemap.Layer.Wire("bathymetry")).get
    assertEquals((sea.present, sea.maxZoom, sea.tileType), (true, Some(3), Some("image/png")))
    assert(sea.attribution.value.contains("ETOPO1"), sea.attribution)
    val (b, mime, _, _, code) = svc
      .tile(Basemap.Layer.Wire("bathymetry"), Basemap.Zoom(3), Basemap.Tile.Column(4), Basemap.Tile.Row(3))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((code.code, mime, String(b.value)), (200, "image/png", "PNG3/4/3"))
    assertEquals(
      svc
        .tile(Basemap.Layer.Wire("bathymetry"), Basemap.Zoom(0), Basemap.Tile.Column(0), Basemap.Tile.Row(0))
        .map(_._5.code),
      Right(204),
      "the bucket had none"
    )
    assert(!Files.exists(dir.resolve(".bathymetry.part")), "the parts are removed once written")

  test(
    "#2719: the sea floor from GEBCO — its GeoTIFF rows read by range, made into tiles; ETOPO1 outside the region; GEBCO credited"
  ):
    val dir = Files.createTempDirectory("basemap")
    // the grid's 90° tile north-east of 0,0 at a coarse 0.25° (360 × 360): its depth falls 1 m a row southwards
    val tif = Files.createTempDirectory("gebco").resolve("n90_s0_w0_e90.tif")
    Files.write(
      tif,
      GeoTiffSuite.tiff(
        PixelCount(360),
        PixelCount(360),
        west = Degrees(0),
        north = Degrees(90),
        step = Degrees.PerPixel(0.25),
        value = (r, _) => (-r).toShort
      )
    )
    val grid = BasemapFixtures.LocalRange(tif)
    val etopoAsked = java.util.concurrent.atomic.AtomicInteger(0)
    val outside = TerrariumPng.encode(Array.fill(256 * 256)(-42f), TerrariumPng.TileSize(256))
    val router = new RangeHttp:
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
      private def mine(url: RangeHttp.Url) =
        Either.cond(
          url.value == "fake://gebco/n90.0_s0.0_w0.0_e90.0.tif",
          grid,
          Problem.Unclassified(Legacy.Text(s"${url.value}: unexpected"))
        )
      @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
      def text(url: RangeHttp.Url) = Left(Problem.Unclassified(Legacy.Text("unexpected")))
      def bytes(url: RangeHttp.Url, o: ByteOffset, l: ByteCount) = mine(url).flatMap(_.bytes(url, o, l))
      def download(url: RangeHttp.Url, o: ByteOffset, l: ByteCount, to: Reservation) =
        mine(url).flatMap(_.download(url, o, l, to))
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      override def file(url: RangeHttp.Url) = {
        etopoAsked.incrementAndGet(); Right(Some(RangeHttp.ResponseBytes(outside.value)))
      }
    val base = AppConfig.load()
    val cfg = base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.basemap)
      .setTo(
        base.basemap
          .modify(_.dir)
          .setTo(BasemapConfig.Dir(dir.toString))
          .modify(_.requestGapMs)
          .setTo(BasemapConfig.RequestGapMs(0))
          .modify(_.bbox)
          .setTo(BasemapConfig.Bbox("22,35,29,41"))
          .modify(_.maxRequestMb)
          .setTo(BasemapConfig.MaxRequestMb(1))
          .modify(_.bathymetry)
          .setTo(
            base.basemap.bathymetry
              .modify(_.url)
              .setTo(BasemapBathymetryConfig.Url("fake://sea/{z}/{x}/{y}.png"))
              .modify(_.source)
              .setTo(SeaFloorSource.Gebco)
              .modify(_.gebco)
              .setTo(
                base.basemap.bathymetry.gebco
                  .modify(_.url)
                  .setTo(BasemapGebcoConfig.Url("fake://gebco/n{north}_s{south}_w{west}_e{east}.tif"))
                  .modify(_.maxZoom)
                  .setTo(BasemapGebcoConfig.MaxZoom(3))
              )
          )
      )
    val svc = BasemapWiring.service(
      Deps(
        cfg,
        ds,
        ds,
        clock = java.time.Clock.fixed(java.time.Instant.parse("2000-01-02T03:04:05Z"), java.time.ZoneOffset.UTC)
      ),
      () => router
    )
    val dry = await(
      svc
        .fetch(
          Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("bathymetry"), dryRun = Basemap.FetchQuery.DryRun.DryRun)
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
    )
    assert(dry.succeeded, dry.toString)
    // rows 196–219 (latitudes 41–35) of 720 bytes, in requests of at most 1 MB: one
    assert(
      dry.progress.exists(p =>
        p.value.contains(
          "bathymetry from GEBCO (zoom 0–3): would read 17.3 kB of 1 GeoTIFF in 1 range request, and write 5 tiles"
        )
          && p.value.contains("zoom 0: 1 · zoom 1: 1")
      ),
      dry.progress.toString
    )
    assertEquals(grid.downloads.get, 0)
    val j = await(
      svc
        .fetch(Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("bathymetry")))
        .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
    )
    assert(j.succeeded, j.toString)
    assertEquals(grid.downloads.get, 1)
    val sea = svc.status.layers.find(_.layer == Basemap.Layer.Wire("bathymetry")).get
    assertEquals((sea.present, sea.maxZoom, sea.source), (true, Some(3), Some("GEBCO grid")))
    assert(
      sea.attribution.value.contains("GEBCO_2026 Grid") && sea.attribution.value
        .contains("outside it") && sea.attribution.value.contains(
        "ETOPO1"
      ),
      sea.attribution
    )
    def elevation(z: Basemap.Zoom, x: Basemap.Tile.Column, y: Basemap.Tile.Row, px: Int, py: Int): Float =
      val (b, mime, _, _, code) =
        svc
          .tile(Basemap.Layer.Wire("bathymetry"), z, x, y)
          .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
      assertEquals((code.code, mime), (200, "image/png"))
      val (m, w) = TerrariumPng.decode(Png.PngBytes(b.value)).fold(e => fail(e.sentence), identity)
      m(py * w.value + px)
    // zoom 3, tile 4/3 holds 0–45°E, 0–41°N: pixel (142, 22) is about 25°E 38°N, inside the region (the grid's
    // row 208); pixel (2, 254), near 0°N 0°E, far outside it (ETOPO1's tile)
    assertEqualsDouble(
      elevation(Basemap.Zoom(3), Basemap.Tile.Column(4), Basemap.Tile.Row(3), 142, 22).toDouble,
      -208.0,
      1.5
    )
    assertEqualsDouble(
      elevation(Basemap.Zoom(3), Basemap.Tile.Column(4), Basemap.Tile.Row(3), 2, 254).toDouble,
      -42.0,
      0.01
    )
    assert(
      etopoAsked.get > 0 && etopoAsked.get <= Basemap
        .tileCount(BBox(Longitude(22), Latitude(35), Longitude(29), Latitude(41)), Basemap.Zoom(0), Basemap.Zoom(3))
        .value
    )
    assert(!Files.exists(dir.resolve(".bathymetry.part")), "the rows read are removed once the tiles are written")

  test(
    "#3760: the map and the sea floor outside the region (or deeper) are read on demand, thinned and cached; off, none"
  ):
    val dir = Files.createTempDirectory("basemap")
    val (svc, v, _) = service(dir)
    assert(
      await(
        svc
          .fetch(Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("vector")))
          .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
      ).succeeded
    )
    assert(
      await(
        svc
          .fetch(Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("bathymetry")))
          .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
      ).succeeded
    )
    val vec = svc.status.layers.find(_.layer == Basemap.Layer.Wire("vector")).get
    assertEquals(
      (vec.maxZoom, vec.deepest, vec.onDemand.map(o => (o.enabled, o.tiles))),
      (Some(4), Some(5), Some((true, 0L)))
    )
    // a tile far outside the region (x 0 at zoom 3 is the Pacific): from the build the extract came from, thinned
    val asked = v.requests.get
    val (b, mime, enc, _, code) = svc
      .tile(Basemap.Layer.Wire("vector"), Basemap.Zoom(3), Basemap.Tile.Column(0), Basemap.Tile.Row(0))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((code.code, mime, enc), (200, "application/vnd.mapbox-vector-tile", Some("gzip")))
    val text = String(plain(b).value, "ISO-8859-1")
    assert(text.contains("earth") && !text.contains("highway"), "thinned as the download is: the roads dropped")
    assert(v.requests.get > asked, "read from the provider")
    // again: from the cache, nothing asked; deeper than the extract inside the region too
    val again = v.requests.get
    assertEquals(
      svc
        .tile(Basemap.Layer.Wire("vector"), Basemap.Zoom(3), Basemap.Tile.Column(0), Basemap.Tile.Row(0))
        .map(_._5.code),
      Right(200)
    )
    assertEquals(v.requests.get, again)
    assertEquals(
      svc
        .tile(
          Basemap.Layer.Wire("vector"),
          Basemap.Zoom(5),
          Basemap.tileX(Longitude(24.5), Basemap.Zoom(5)),
          Basemap.tileY(Latitude(37.5), Basemap.Zoom(5))
        )
        .map(_._5.code),
      Right(200)
    )
    assertEquals(svc.status.layers.find(_.layer == Basemap.Layer.Wire("vector")).get.onDemand.map(_.tiles), Some(2L))
    // inside the region at its zooms: the extract's own tile, the provider not asked
    val inside = v.requests.get
    assertEquals(
      svc
        .tile(
          Basemap.Layer.Wire("vector"),
          Basemap.Zoom(4),
          Basemap.tileX(Longitude(24.5), Basemap.Zoom(4)),
          Basemap.tileY(Latitude(37.5), Basemap.Zoom(4))
        )
        .map(_._5.code),
      Right(200)
    )
    assertEquals(v.requests.get, inside)
    // the sea floor outside the region: one GET, then cached
    val gets = BasemapServiceSuite.seaGets.get
    val (sb, smime, _, _, scode) = svc
      .tile(Basemap.Layer.Wire("bathymetry"), Basemap.Zoom(3), Basemap.Tile.Column(1), Basemap.Tile.Row(1))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((scode.code, smime, String(sb.value)), (200, "image/png", "PNG3/1/1"))
    assertEquals(
      svc
        .tile(Basemap.Layer.Wire("bathymetry"), Basemap.Zoom(3), Basemap.Tile.Column(1), Basemap.Tile.Row(1))
        .map(_._5.code),
      Right(200)
    )
    assertEquals(BasemapServiceSuite.seaGets.get - gets, 1)
    // the caches are removed with the others
    val c = svc
      .remove(Basemap.RemoveQuery(Basemap.RemoveQuery.Layer("none"), cache = Basemap.RemoveQuery.Cache.Cache))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(c.removed.map(r => Path.of(r.value).getFileName.toString).sorted, List("sea-cache", "vector-cache"))
    // switched off: nothing outside the region
    val dir2 = Files.createTempDirectory("basemap")
    val (off, v2, _) = service(dir2, seaAndMap = false)
    assert(
      await(
        off
          .fetch(Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("vector")))
          .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
      ).succeeded
    )
    val n = v2.requests.get
    assertEquals(
      off
        .tile(Basemap.Layer.Wire("vector"), Basemap.Zoom(3), Basemap.Tile.Column(0), Basemap.Tile.Row(0))
        .map(_._5.code),
      Right(204)
    )
    assertEquals(v2.requests.get, n)

  test("#3760: --bbox world plans the whole world, its size given zoom by zoom"):
    val dir = Files.createTempDirectory("basemap")
    val (svc, _, _) = service(dir)
    val j = await(
      svc
        .fetch(
          Basemap.FetchQuery(
            layer = Basemap.FetchQuery.Layer("vector"),
            bbox = Some(BBox.Text("world")),
            maxZoom = Some(Basemap.Focus.MaxZoom(2)),
            dryRun = Basemap.FetchQuery.DryRun.DryRun
          )
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
    )
    assert(j.succeeded, j.toString)
    // zooms 0–2 of the world: 1 + 4 + 16 tiles
    assert(
      j.progress.exists(p =>
        p.value.contains("21 tiles") && p.value.contains("zoom 1: 4 tiles") && p.value.contains("zoom 2: 16 tiles")
      ),
      j.progress.toString
    )

  test("bad options are refused before a job starts"):
    val (svc, _, _) = service(Files.createTempDirectory("basemap"))
    assert(svc.fetch(Basemap.FetchQuery(layer = Basemap.FetchQuery.Layer("roads"))).isLeft)
    assert(svc.fetch(Basemap.FetchQuery(bbox = Some(BBox.Text("10,10,5,5")))).isLeft)
    assert(svc.fetch(Basemap.FetchQuery(maxZoom = Some(Basemap.Focus.MaxZoom(15)))).isLeft)

object BasemapServiceSuite:
  val seaGets = java.util.concurrent.atomic.AtomicInteger(0)

  /** Two Natural Earth-shaped files: a range in the region and one far away; a river in it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val Ranges: RangeHttp.ResponseText = RangeHttp.ResponseText("""{"type":"FeatureCollection","features":[
      |{"type":"Feature","properties":{"FEATURECLA":"Range/mtn","NAME":"Pindus Mts.","NAME_EN":"Pindus","MIN_LABEL":6},
      | "geometry":{"type":"Polygon","coordinates":[[[20.6,39.4],[21.4,39.4],[21.4,40.6],[20.6,40.6],[20.6,39.4]]]}},
      |{"type":"Feature","properties":{"FEATURECLA":"Range/mtn","NAME":"Andes"},
      | "geometry":{"type":"Point","coordinates":[-70,-30]}}]}""".stripMargin)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val Rivers: RangeHttp.ResponseText = RangeHttp.ResponseText("""{"type":"FeatureCollection","features":[
      |{"type":"Feature","properties":{"featurecla":"River","name":"Aliákmon","name_en":"Haliacmon","min_label":5},
      | "geometry":{"type":"LineString","coordinates":[[20.9,40.4],[21.6,40.2],[22.6,40.5]]}},
      |{"type":"Feature","properties":{"featurecla":"Canal","name":"Corinth Canal"},
      | "geometry":{"type":"LineString","coordinates":[[22.95,37.93],[23.0,37.9]]}}]}""".stripMargin)
