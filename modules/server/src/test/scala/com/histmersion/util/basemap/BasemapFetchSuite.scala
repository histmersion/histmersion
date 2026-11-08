package com.histmersion
package util.basemap

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.{ByteCount, ByteOffset}
import com.histmersion.api.BBox
import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.{Files, Path}
import com.histmersion.api.basemap.Basemap
import com.histmersion.util.storage.Store
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude

/** The base-map extract (plan → download → assemble) against a local archive behind a fake range server — no network —
  * and its pure parts.
  */
class BasemapFetchSuite extends munit.FunSuite:

  private val noPause = BasemapFetch.Pace(
    BasemapFetch.Pace.GapMs(0),
    ByteCount.applyUnsafe(1L << 20),
    BasemapFetch.Pace.Overfetch(0.05),
    _ => ()
  )
  private val overfetch = BasemapFetch.Pace.Overfetch(0.05)
  private val aegean = BBox(Longitude(22), Latitude(35), Longitude(29), Latitude(41))
  private val dropRules = Basemap
    .parseDrop(Basemap.DropRule.Spec(AppConfig.load().basemap.vector.drop.value))
    .fold(e => fail(e.value), identity)
  private val keep = BasemapService.mvtRules(dropRules)

  private val Kind = Mvt.PropKey("kind")
  private val KindDetail = Mvt.PropKey("kind_detail")
  private val Name = Mvt.PropKey("name")
  private val NameEn = Mvt.PropKey("name:en")
  private val NameDe = Mvt.PropKey("name:de")
  private val NameFr = Mvt.PropKey("name:fr")
  private val MinZoom = Mvt.PropKey("min_zoom")
  private val Wikidata = Mvt.PropKey("wikidata")
  private val Ref = Mvt.PropKey("ref")

  private def commands(cs: Int*): Vector[Mvt.GeometryCommand] = cs.map(Mvt.GeometryCommand(_)).toVector

  private def tmp(): Path = Files.createTempDirectory("basemap")

  private def check(
      code: Int,
      range: Option[String],
      offset: Long,
      length: Long
  ): Either[RangeHttp.Problem, ByteCount] =
    RangeHttp.check(
      RangeHttp.Url("u"),
      RangeHttp.StatusCode(code),
      range.map(RangeHttp.ContentRangeHeader(_)),
      ByteOffset.applyUnsafe(offset),
      ByteCount.applyUnsafe(length)
    )

  test("tile ranges and counts follow Web Mercator"):
    assertEquals(
      Basemap.tileRange(
        BBox(
          Longitude(-180),
          Latitude(-85),
          Longitude(180),
          Latitude(85)
        ),
        Basemap.Zoom(2)
      ),
      ((0L, 3L), (0L, 3L))
    )
    assertEquals(Basemap.tileRange(aegean, Basemap.Zoom(6)), ((35L, 37L), (23L, 25L)))
    assertEquals(Basemap.tileCount(aegean, Basemap.Zoom(0), Basemap.Zoom(2)), 3L)
    assertEquals(
      BasemapFetch.wantedIds(aegean, BasemapFetch.Plan.MinZoom(0), BasemapFetch.Plan.MaxZoom(1)).toList,
      List(
        PmTiles.tileId(PmTiles.Zoom(0), PmTiles.TileColumn(0), PmTiles.TileRow(0)),
        PmTiles.tileId(PmTiles.Zoom(1), PmTiles.TileColumn(1), PmTiles.TileRow(0))
      )
    )

  test("contents are merged into few requests: small gaps read through, the size capped"):
    assertEquals(
      BasemapFetch.coalesce(
        List(
          (ByteOffset(0), ByteCount(10)),
          (ByteOffset(10), ByteCount(10)),
          (ByteOffset(30), ByteCount(5))
        ),
        ByteCount(1000),
        overfetch
      ),
      Vector((0L, 35L))
    )
    assertEquals(
      BasemapFetch.coalesce(
        List((ByteOffset(0), ByteCount(10)), (ByteOffset(200000), ByteCount(10))),
        ByteCount.applyUnsafe(1L << 20),
        overfetch
      ),
      Vector((0L, 10L), (200000L, 10L))
    )
    assertEquals(
      BasemapFetch.coalesce(
        List((ByteOffset(0), ByteCount(600)), (ByteOffset(600), ByteCount(600))),
        ByteCount(1000),
        overfetch
      ),
      Vector((0L, 600L), (600L, 600L))
    )
    assertEquals(BasemapFetch.coalesce(Nil, ByteCount(10), overfetch), Vector.empty)

  test("the drop list: whole layers, some kinds, all but some kinds; nothing named twice or mixed"):
    assertEquals(
      Basemap.parseDrop(Basemap.DropRule.Spec("roads; water swimming_pool fountain; pois +peak +volcano")),
      Right(
        List(
          Basemap.DropRule(Basemap.DropRule.Layer("roads"), Basemap.DropRule.Whole(true)),
          Basemap.DropRule(
            Basemap.DropRule.Layer("water"),
            Basemap.DropRule.Whole(false),
            drop = Set(Basemap.DropRule.Drop("swimming_pool"), Basemap.DropRule.Drop("fountain"))
          ),
          Basemap.DropRule(
            Basemap.DropRule.Layer("pois"),
            Basemap.DropRule.Whole(false),
            spare = Set(Basemap.DropRule.Spare("peak"), Basemap.DropRule.Spare("volcano"))
          )
        )
      )
    )
    assertEquals(Basemap.parseDrop(Basemap.DropRule.Spec(" ; ")), Right(Nil), "nothing dropped")
    assert(Basemap.parseDrop(Basemap.DropRule.Spec("roads; roads")).isLeft)
    assert(Basemap.parseDrop(Basemap.DropRule.Spec("pois peak +volcano")).isLeft)
    assertEquals(
      Basemap.keptLine(keepAll = Basemap.FetchQuery.KeepAll(true), Some(Basemap.DropRule.Spec("roads"))),
      "everything (unfiltered)"
    )
    assertEquals(
      Basemap.keptLine(keepAll = Basemap.FetchQuery.KeepAll(false), Some(Basemap.DropRule.Spec("roads; places"))),
      "all but: roads; places"
    )
    assert(Basemap.keptLine(keepAll = Basemap.FetchQuery.KeepAll(false), None).value.contains("before the drop list"))
    assert(Basemap.validBBox(Basemap.Focus.Bbox("29,35,22,41")).isLeft)
    assert(Basemap.validBBox(Basemap.Focus.Bbox("-200,0,10,10")).isLeft)
    assertEquals(Basemap.validBBox(Basemap.Focus.Bbox("22,35,29,41")), Right(aegean))

  test(
    "a vector tile is thinned by the drop list: no roads, places or modern POIs; everything else kept for the style"
  ):
    val raw = BasemapFixtures.vectorTile(PmTiles.Zoom(3), PmTiles.TileColumn(4), PmTiles.TileRow(3))
    val stored = PmTiles.TileBytes(PmTiles.gzip(raw).value)
    val thin = BasemapService.thin(stored, PmTiles.Compression.Gzip, keep).fold(e => fail(e.value), identity).get
    val t = PmTiles.gunzip(PmTiles.CompressedBytes(thin.value)).fold(e => fail(e.value), identity)
    assertEquals(Mvt.layerNames(t), Right(List("earth", "water", "landcover", "landuse", "pois", "physical_point")))
    assertEquals(Mvt.kinds(t, Mvt.LayerName("water")), Right(List("ocean", "river", "canal", "sea")))
    assertEquals(Mvt.kinds(t, Mvt.LayerName("landcover")), Right(List("forest", "farmland")))
    assertEquals(Mvt.kinds(t, Mvt.LayerName("pois")), Right(List("peak")), "a peak spared, the restaurant dropped")
    // nothing filtered at all: --keep-all stores the tile as it is
    assertEquals(
      BasemapService
        .thin(stored, PmTiles.Compression.Gzip, Map.empty)
        .map(_.map(b => PmTiles.gunzip(PmTiles.CompressedBytes(b.value)).toOption.get.value.toList)),
      Right(Some(raw.value.toList))
    )
    // nothing left: the tile is dropped
    val roadsOnly = Mvt.tile(
      List(
        Mvt.layer(
          Mvt.LayerName("roads"),
          List((Mvt.GeomType(2), commands(9, 0, 0, 10, 5, 5), Map(Kind -> Mvt.PropText("highway"))))
        )
      )
    )
    assertEquals(
      BasemapService.thin(PmTiles.TileBytes(roadsOnly.value), PmTiles.Compression.None, keep),
      Right(None)
    )

  test("a served cut: only the kinds and properties the page reads, geometry byte for byte, the tables rebuilt"):
    val pt = Mvt.pointGeometry(Mvt.PointX(10), Mvt.PointY(20))
    val line = Mvt.lineGeometry(
      List(List((Mvt.PointX(0), Mvt.PointY(0)), (Mvt.PointX(5), Mvt.PointY(5)), (Mvt.PointX(9), Mvt.PointY(2))))
    )
    val tile = Mvt.tile(
      List(
        Mvt.typedLayer(
          Mvt.LayerName("places"),
          List(
            (
              Mvt.GeomType(1),
              pt,
              List(
                Kind -> "locality",
                KindDetail -> "city",
                Name -> "Αθήνα",
                NameEn -> "Athens",
                NameDe -> "Athen",
                NameFr -> "Athènes",
                MinZoom -> 4L,
                Wikidata -> "Q1524"
              )
            ),
            (
              Mvt.GeomType(1),
              pt,
              List(Kind -> "country", Name -> "Ελλάδα", NameEn -> "Greece", MinZoom -> 2L)
            )
          )
        ),
        Mvt.layer(
          Mvt.LayerName("roads"),
          List(
            (Mvt.GeomType(2), line, Map(Kind -> Mvt.PropText("highway"), Ref -> Mvt.PropText("A1"))),
            (Mvt.GeomType(2), line, Map(Kind -> Mvt.PropText("minor_road"))),
            (Mvt.GeomType(2), line, Map(Kind -> Mvt.PropText("rail")))
          )
        ),
        Mvt.layer(
          Mvt.LayerName("landuse"),
          List((Mvt.GeomType(3), commands(9, 0, 0, 18, 4, 0, 0, 4, 15), Map(Kind -> Mvt.PropText("wood"))))
        )
      )
    )
    val stored = PmTiles.TileBytes(PmTiles.gzip(tile).value)
    val rules = Basemap.parseSubset(Basemap.Subset.Spec("places:p,roads:l~highway|rail")).fold(e => fail(e.value), identity)
    val cut = BasemapService
      .subset(
        stored,
        PmTiles.Compression.Gzip,
        rules,
        Some(
          Set(
            Mvt.Rule.Props("kind"),
            Mvt.Rule.Props("kind_detail"),
            Mvt.Rule.Props("min_zoom"),
            Mvt.Rule.Props("name:de"),
            Mvt.Rule.Props("name:en"),
            Mvt.Rule.Props("name")
          )
        )
      )
      .fold(e => fail(e.value), identity)
    val got = Mvt
      .features(PmTiles.gunzip(PmTiles.CompressedBytes(cut.value)).fold(e => fail(e.value), identity))
      .fold(e => fail(e.value), identity)
    assertEquals(got.map(_._1), List("places", "places", "roads", "roads"), "no land use; the minor road gone")
    assertEquals(
      got.head._3,
      Map(
        "kind" -> "locality",
        "kind_detail" -> "city",
        "name" -> "Αθήνα",
        "name:en" -> "Athens",
        "name:de" -> "Athen",
        "min_zoom" -> "4"
      ),
      "French and the Wikidata id dropped; a number stays a number"
    )
    assertEquals(got(1)._3, Map("kind" -> "country", "name" -> "Ελλάδα", "name:en" -> "Greece", "min_zoom" -> "2"))
    assertEquals(got.drop(2).map(_._3), List(Map("kind" -> "highway"), Map("kind" -> "rail")))
    assertEquals(got.map(_._2), List(1, 1, 2, 2))
    // the geometry is untouched: the same bytes as a cut that keeps every property
    val whole = BasemapService.subset(stored, PmTiles.Compression.Gzip, rules).fold(e => fail(e.value), identity)
    assert(cut.value.length < whole.value.length, s"${cut.value.length} < ${whole.value.length}")
    val wholeFeatures = Mvt.features(PmTiles.gunzip(PmTiles.CompressedBytes(whole.value)).toOption.get).toOption.get
    assertEquals(wholeFeatures.map(_._2), got.map(_._2))
    assertEquals(wholeFeatures.head._3(NameFr), "Athènes")

  test("Content-Range is checked: a 200 to a range request is refused, a short answer named"):
    assertEquals(
      RangeHttp.contentRange(RangeHttp.ContentRangeHeader("bytes 0-99/1000")),
      Some((0L, 99L, Some(1000L)))
    )
    assertEquals(check(206, Some("bytes 10-19/1000"), 10, 10), Right(1000L))
    assertEquals(check(206, Some("bytes 0-49/50"), 0, 16384), Right(50L))
    assert(check(200, None, 0, 10).left.exists(_.value.contains("ignores range requests")))
    assert(check(206, Some("bytes 0-9/1000"), 10, 10).isLeft)
    assert(check(404, None, 0, 10).isLeft)

  test("the provider's list of builds: the newest last, only archives"):
    val json = """[{"key":"20261018.pmtiles","size":10},{"key":"20261019.pmtiles","size":11},{"key":"notes.txt"}]"""
    assertEquals(
      BasemapService.builds(BasemapService.JsonText(json)).map(_.lastOption),
      Right(Some(("20261019.pmtiles", 11L)))
    )
    assert(BasemapService.builds(BasemapService.JsonText("{")).isLeft)

  test("plan, download and assemble a region of a vector archive: only its tiles, thinned"):
    val dir = tmp()
    val src = BasemapFixtures.vectorArchive(dir.resolve("planet.pmtiles"), maxZoom = PmTiles.Zoom(6))
    val http = BasemapFixtures.LocalRange(src)
    val plan = BasemapFetch
      .plan(
        http,
        BasemapFetch.Source(
          RangeHttp.Url("fake://planet"),
          BasemapFetch.Source.Label("planet"),
          Some(BasemapFetch.Source.ExpectedSize(Files.size(src)))
        ),
        aegean,
        BasemapFetch.Plan.MaxZoom(5),
        noPause,
        _ => ()
      )
      .fold(e => fail(e.value), identity)
    assertEquals(plan.maxZoom, 5)
    assertEquals(plan.tiles.size.toLong, Basemap.tileCount(aegean, Basemap.Zoom(0), Basemap.Zoom(5)))
    assert(plan.requests.size <= 3, plan.requests.toString)
    val work = BasemapFixtures.store(dir.resolve("work"))
    BasemapFetch.prepare(work, BasemapFetch.stateOf(plan, BasemapFetch.Note("x")))
    BasemapFetch.download(http, plan, work, noPause, _ => (), _ => Right(())).fold(e => fail(e.value), identity)
    val out = dir.resolve("vector.pmtiles")
    val (_, n) = BasemapFetch
      .assemble(
        plan,
        work,
        BasemapFixtures.store(dir),
        Store.Key.applyUnsafe("vector.pmtiles"),
        b => BasemapService.thin(b, PmTiles.Compression.Gzip, keep),
        BasemapFetch.localHeader(plan),
        BasemapFetch.localMetadata(
          plan,
          dropRules.filter(_.whole.value).map(_.layer).toSet,
          BasemapFetch.Attribution("© OSM"),
          BasemapFetch.FetchedAt("2026-10-19T00:00:00Z"),
          Basemap.Layer.Vector,
          Some((BasemapFetch.Filtered(false), BasemapFetch.FilterSpec("roads; places")))
        ),
        _ => ()
      )
      .fold(e => fail(e.value), identity)
    assertEquals(n, plan.tiles.size.toLong)
    val f = BasemapFixtures.open(out).fold(e => fail(e.value), identity)
    try
      assertEquals(f.header.maxZoom, 5)
      assertEquals((f.header.minLon, f.header.maxLat), (22.0, 41.0))
      val t = f
        .tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(12))
        .fold(e => fail(e.value), identity)
        .map(b => PmTiles.gunzip(PmTiles.CompressedBytes(b.value)).fold(e => fail(e.value), identity))
        .get
      assert(!Mvt.layerNames(t).toOption.get.contains(Mvt.LayerName("roads")))
      assertEquals(
        f.tile(PmTiles.Zoom(5), PmTiles.TileColumn(0), PmTiles.TileRow(0)),
        Right(None),
        "outside the region"
      )
      assertEquals(f.metadata.hcursor.downField("histmersion").get[String]("build"), Right("planet"))
      assertEquals(
        f.metadata.hcursor.downField("vector_layers").as[List[Json]].map(_.size),
        Right(4),
        "earth, water, landcover, landuse"
      )
      assertEquals(f.metadata.hcursor.downField("histmersion").get[String]("drop"), Right("roads; places"))
    finally f.close()

  test("an interrupted download resumes: the parts already there are not asked for again"):
    val dir = tmp()
    val src = BasemapFixtures.terrainArchive(dir.resolve("planet.pmtiles"), maxZoom = PmTiles.Zoom(6))
    val http = BasemapFixtures.LocalRange(src)
    val tiny = noPause.modify(_.maxRequestBytes).setTo(ByteCount(200)) // many small requests
    val plan =
      BasemapFetch
        .plan(
          http,
          BasemapFetch.Source(RangeHttp.Url("fake://t"), BasemapFetch.Source.Label("t")),
          aegean,
          BasemapFetch.Plan.MaxZoom(6),
          tiny,
          _ => ()
        )
        .fold(e => fail(e.value), identity)
    assert(plan.requests.size > 3, plan.requests.toString)
    val work = BasemapFixtures.store(dir.resolve("work"))
    assertEquals(BasemapFetch.prepare(work, BasemapFetch.stateOf(plan, BasemapFetch.Note(""))), 0)
    // the first two parts arrive, then the disk check stops the fetch
    var allowed = 2 // the parts the fake disk has room for
    val stopped = BasemapFetch.download(
      http,
      plan,
      work,
      tiny,
      _ => (),
      _ =>
        if allowed > 0 then { allowed -= 1; Right(()) }
        else Left(RangeHttp.Problem("full"))
    )
    assertEquals(stopped, Left("full"))
    assertEquals(http.downloads.get, 2)
    // again, with the same plan: the two parts are kept, the rest fetched
    assertEquals(BasemapFetch.prepare(work, BasemapFetch.stateOf(plan, BasemapFetch.Note(""))), 2)
    BasemapFetch.download(http, plan, work, tiny, _ => (), _ => Right(())).fold(e => fail(e.value), identity)
    assertEquals(http.downloads.get, plan.requests.size)
    // another plan (a deeper zoom) empties the work directory first
    val deeper = plan.modify(_.maxZoom).setTo(BasemapFetch.Plan.MaxZoom(7))
    assertEquals(BasemapFetch.prepare(work, BasemapFetch.stateOf(deeper, BasemapFetch.Note(""))), 0)

  test("a build replaced during the download is refused, not mixed"):
    val dir = tmp()
    val src = BasemapFixtures.terrainArchive(dir.resolve("planet.pmtiles"), maxZoom = PmTiles.Zoom(5))
    val http = BasemapFixtures.LocalRange(src)
    val plan =
      BasemapFetch
        .plan(
          http,
          BasemapFetch.Source(RangeHttp.Url("fake://t"), BasemapFetch.Source.Label("t")),
          aegean,
          BasemapFetch.Plan.MaxZoom(5),
          noPause,
          _ => ()
        )
        .fold(e => fail(e.value), identity)
    http.etag = RemoteFile.Etag("\"v2\"")
    val work = BasemapFixtures.store(dir.resolve("work"))
    BasemapFetch.prepare(work, BasemapFetch.stateOf(plan, BasemapFetch.Note("")))
    val r = BasemapFetch.download(http, plan, work, noPause, _ => (), _ => Right(()))
    assert(r.left.exists(_.value.contains("changed during the download")), r.toString)
    assertEquals(BasemapFetch.pending(plan, work).size, plan.requests.size)

  test("a provider size that differs from the listed one is refused"):
    val dir = tmp()
    val src = BasemapFixtures.terrainArchive(dir.resolve("planet.pmtiles"), maxZoom = PmTiles.Zoom(3))
    val http = BasemapFixtures.LocalRange(src)
    val r = BasemapFetch.plan(
      http,
      BasemapFetch.Source(
        RangeHttp.Url("fake://t"),
        BasemapFetch.Source.Label("b"),
        Some(BasemapFetch.Source.ExpectedSize(1L))
      ),
      aegean,
      BasemapFetch.Plan.MaxZoom(3),
      noPause,
      _ => ()
    )
    assert(r.left.exists(_.value.contains("the provider lists 1")), r.toString)

  test("the extracts live in the XDG cache unless basemap.dir moves them"):
    val cfg = AppConfig.load()
    assertEquals(
      UserPaths.basemapDir(
        UserPaths.BasemapDir(cfg.basemap.dir.value),
        RuntimeConfig.DataDir(cfg.init.dataDir.value),
        Map(Environment.VariablesKey("XDG_CACHE_HOME") -> Environment.VariablesValue("/x/cache")).get
      ),
      Path.of("/x/cache/histmersion/basemap")
    )
    assertEquals(
      UserPaths.basemapDir(
        UserPaths.BasemapDir("/y/maps"),
        RuntimeConfig.DataDir(cfg.init.dataDir.value),
        Map.empty[Environment.VariablesKey, Environment.VariablesValue].get
      ),
      Path.of("/y/maps")
    )

  private type Json = io.circe.Json
