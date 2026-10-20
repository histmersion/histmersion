package com.histmersion.server.basemap

import com.histmersion.core.*

import java.nio.file.{Files, Path}

/** The base-map extract (plan → download → assemble) against a local archive behind a fake range
  * server — no network — and its pure parts. */
class BasemapFetchSuite extends munit.FunSuite:

  private val noPause = BasemapFetch.Pace(0, 1L << 20, 0.05, _ => ())
  private val aegean  = BBox(22, 35, 29, 41)
  private val dropRules = Basemap.parseDrop(AppConfig.load().basemap.vector.drop).fold(fail(_), identity)
  private val keep = BasemapService.mvtRules(dropRules)

  private def tmp(): Path = Files.createTempDirectory("basemap")

  test("tile ranges and counts follow Web Mercator"):
    assertEquals(Basemap.tileRange(BBox(-180, -85, 180, 85), 2), ((0L, 3L), (0L, 3L)))
    assertEquals(Basemap.tileRange(aegean, 6), ((35L, 37L), (23L, 25L)))
    assertEquals(Basemap.tileCount(aegean, 0, 2), 3L)
    assertEquals(BasemapFetch.wantedIds(aegean, 0, 1).toList, List(PmTiles.tileId(0, 0, 0), PmTiles.tileId(1, 1, 0)))

  test("contents are merged into few requests: small gaps read through, the size capped"):
    assertEquals(BasemapFetch.coalesce(List((0L, 10L), (10L, 10L), (30L, 5L)), 1000, 0.05), Vector((0L, 35L)))
    assertEquals(BasemapFetch.coalesce(List((0L, 10L), (200000L, 10L)), 1L << 20, 0.05), Vector((0L, 10L), (200000L, 10L)))
    assertEquals(BasemapFetch.coalesce(List((0L, 600L), (600L, 600L)), 1000, 0.05), Vector((0L, 600L), (600L, 600L)))
    assertEquals(BasemapFetch.coalesce(Nil, 10, 0.05), Vector.empty)

  test("the drop list: whole layers, some kinds, all but some kinds; nothing named twice or mixed"):
    assertEquals(Basemap.parseDrop("roads; water swimming_pool fountain; pois +peak +volcano"),
      Right(List(Basemap.DropRule("roads", whole = true), Basemap.DropRule("water", whole = false, drop = Set("swimming_pool", "fountain")),
        Basemap.DropRule("pois", whole = false, spare = Set("peak", "volcano")))))
    assertEquals(Basemap.parseDrop(" ; "), Right(Nil), "nothing dropped")
    assert(Basemap.parseDrop("roads; roads").isLeft)
    assert(Basemap.parseDrop("pois peak +volcano").isLeft)
    assertEquals(Basemap.keptLine(keepAll = true, Some("roads")), "everything (unfiltered)")
    assertEquals(Basemap.keptLine(keepAll = false, Some("roads; places")), "all but: roads; places")
    assert(Basemap.keptLine(keepAll = false, None).contains("before the drop list"))
    assert(Basemap.validBBox("29,35,22,41").isLeft)
    assert(Basemap.validBBox("-200,0,10,10").isLeft)
    assertEquals(Basemap.validBBox("22,35,29,41"), Right(aegean))

  test("a vector tile is thinned by the drop list: no roads, places or modern POIs; everything else kept for the style"):
    val raw  = BasemapFixtures.vectorTile(3, 4, 3)
    val thin = BasemapService.thin(PmTiles.gzip(raw), PmTiles.Compression.Gzip, keep).fold(fail(_), identity).get
    val t    = PmTiles.gunzip(thin).fold(fail(_), identity)
    assertEquals(Mvt.layerNames(t), Right(List("earth", "water", "landcover", "landuse", "pois", "physical_point")))
    assertEquals(Mvt.kinds(t, "water"), Right(List("ocean", "river", "canal", "sea")))
    assertEquals(Mvt.kinds(t, "landcover"), Right(List("forest", "farmland")))
    assertEquals(Mvt.kinds(t, "pois"), Right(List("peak")), "a peak spared, the restaurant dropped")
    // nothing filtered at all: --keep-all stores the tile as it is
    assertEquals(BasemapService.thin(PmTiles.gzip(raw), PmTiles.Compression.Gzip, Map.empty).map(_.map(b => PmTiles.gunzip(b).toOption.get.toList)),
      Right(Some(raw.toList)))
    // nothing left: the tile is dropped
    val roadsOnly = Mvt.tile(List(Mvt.layer("roads", List((2, Vector(9, 0, 0, 10, 5, 5), Map("kind" -> "highway"))))))
    assertEquals(BasemapService.thin(roadsOnly, PmTiles.Compression.None, keep), Right(None))

  test("a served cut: only the kinds and properties the page reads, geometry byte for byte, the tables rebuilt"):
    val pt = Mvt.pointGeometry(10, 20)
    val line = Mvt.lineGeometry(List(List((0, 0), (5, 5), (9, 2))))
    val tile = Mvt.tile(List(
      Mvt.typedLayer("places", List(
        (1, pt, List("kind" -> "locality", "kind_detail" -> "city", "name" -> "Αθήνα", "name:en" -> "Athens", "name:de" -> "Athen",
          "name:fr" -> "Athènes", "min_zoom" -> 4L, "wikidata" -> "Q1524")),
        (1, pt, List("kind" -> "country", "name" -> "Ελλάδα", "name:en" -> "Greece", "min_zoom" -> 2L)))),
      Mvt.layer("roads", List((2, line, Map("kind" -> "highway", "ref" -> "A1")), (2, line, Map("kind" -> "minor_road")),
        (2, line, Map("kind" -> "rail")))),
      Mvt.layer("landuse", List((3, Vector(9, 0, 0, 18, 4, 0, 0, 4, 15), Map("kind" -> "wood"))))))
    val rules = Basemap.parseSubset("places:p,roads:l~highway|rail").fold(fail(_), identity)
    val cut = BasemapService.subset(PmTiles.gzip(tile), PmTiles.Compression.Gzip, rules,
      Some(Set("kind", "kind_detail", "min_zoom", "name:de", "name:en", "name"))).fold(fail(_), identity)
    val got = Mvt.features(PmTiles.gunzip(cut).fold(fail(_), identity)).fold(fail(_), identity)
    assertEquals(got.map(_._1), List("places", "places", "roads", "roads"), "no land use; the minor road gone")
    assertEquals(got.head._3, Map("kind" -> "locality", "kind_detail" -> "city", "name" -> "Αθήνα", "name:en" -> "Athens",
      "name:de" -> "Athen", "min_zoom" -> "4"), "French and the Wikidata id dropped; a number stays a number")
    assertEquals(got(1)._3, Map("kind" -> "country", "name" -> "Ελλάδα", "name:en" -> "Greece", "min_zoom" -> "2"))
    assertEquals(got.drop(2).map(_._3), List(Map("kind" -> "highway"), Map("kind" -> "rail")))
    assertEquals(got.map(_._2), List(1, 1, 2, 2))
    // the geometry is untouched: the same bytes as a cut that keeps every property
    val whole = BasemapService.subset(PmTiles.gzip(tile), PmTiles.Compression.Gzip, rules).fold(fail(_), identity)
    assert(cut.length < whole.length, s"${cut.length} < ${whole.length}")
    assertEquals(Mvt.features(PmTiles.gunzip(whole).toOption.get).toOption.get.map(_._2), got.map(_._2))
    assertEquals(Mvt.features(PmTiles.gunzip(whole).toOption.get).toOption.get.head._3("name:fr"), "Athènes")

  test("Content-Range is checked: a 200 to a range request is refused, a short answer named"):
    assertEquals(RangeHttp.contentRange("bytes 0-99/1000"), Some((0L, 99L, Some(1000L))))
    assertEquals(RangeHttp.check("u", 206, Some("bytes 10-19/1000"), 10, 10), Right(1000L))
    assertEquals(RangeHttp.check("u", 206, Some("bytes 0-49/50"), 0, 16384), Right(50L))
    assert(RangeHttp.check("u", 200, None, 0, 10).left.exists(_.contains("ignores range requests")))
    assert(RangeHttp.check("u", 206, Some("bytes 0-9/1000"), 10, 10).isLeft)
    assert(RangeHttp.check("u", 404, None, 0, 10).isLeft)

  test("the provider's list of builds: the newest last, only archives"):
    val json = """[{"key":"20261018.pmtiles","size":10},{"key":"20261019.pmtiles","size":11},{"key":"notes.txt"}]"""
    assertEquals(BasemapService.builds(json).map(_.lastOption), Right(Some(("20261019.pmtiles", 11L))))
    assert(BasemapService.builds("{").isLeft)

  test("plan, download and assemble a region of a vector archive: only its tiles, thinned"):
    val dir  = tmp()
    val src  = BasemapFixtures.vectorArchive(dir.resolve("planet.pmtiles"), maxZoom = 6)
    val http = BasemapFixtures.LocalRange(src)
    val plan = BasemapFetch.plan(http, BasemapFetch.Source("fake://planet", "planet", Some(Files.size(src))), aegean, 5,
      noPause, _ => ()).fold(fail(_), identity)
    assertEquals(plan.maxZoom, 5)
    assertEquals(plan.tiles.size.toLong, Basemap.tileCount(aegean, 0, 5))
    assert(plan.requests.size <= 3, plan.requests.toString)
    val work = dir.resolve("work")
    BasemapFetch.prepare(work, BasemapFetch.stateOf(plan, "x"))
    BasemapFetch.download(http, plan, work, noPause, _ => (), _ => Right(())).fold(fail(_), identity)
    val out = dir.resolve("vector.pmtiles")
    val (_, n) = BasemapFetch.assemble(plan, work, out, b => BasemapService.thin(b, PmTiles.Compression.Gzip, keep),
      BasemapFetch.localHeader(plan), BasemapFetch.localMetadata(plan, dropRules.filter(_.whole).map(_.layer).toSet, "© OSM",
        "2026-10-19T00:00:00Z", BasemapLayer.Vector, Some((false, "roads; places"))), _ => ()).fold(fail(_), identity)
    assertEquals(n, plan.tiles.size.toLong)
    val f = PmTilesFile.open(out).fold(fail(_), identity)
    try
      assertEquals(f.header.maxZoom, 5)
      assertEquals((f.header.minLon, f.header.maxLat), (22.0, 41.0))
      val t = f.tile(5, 18, 12).fold(fail(_), identity).map(b => PmTiles.gunzip(b).fold(fail(_), identity)).get
      assert(!Mvt.layerNames(t).toOption.get.contains("roads"))
      assertEquals(f.tile(5, 0, 0), Right(None), "outside the region")
      assertEquals(f.metadata.hcursor.downField("histmersion").get[String]("build"), Right("planet"))
      assertEquals(f.metadata.hcursor.downField("vector_layers").as[List[Json]].map(_.size), Right(4), "earth, water, landcover, landuse")
      assertEquals(f.metadata.hcursor.downField("histmersion").get[String]("drop"), Right("roads; places"))
    finally f.close()

  test("an interrupted download resumes: the parts already there are not asked for again"):
    val dir  = tmp()
    val src  = BasemapFixtures.terrainArchive(dir.resolve("planet.pmtiles"), maxZoom = 6)
    val http = BasemapFixtures.LocalRange(src)
    val tiny = noPause.copy(maxRequestBytes = 200)   // many small requests
    val plan = BasemapFetch.plan(http, BasemapFetch.Source("fake://t", "t"), aegean, 6, tiny, _ => ()).fold(fail(_), identity)
    assert(plan.requests.size > 3, plan.requests.toString)
    val work = dir.resolve("work")
    assertEquals(BasemapFetch.prepare(work, BasemapFetch.stateOf(plan, "")), 0)
    // the first two parts arrive, then the disk check stops the fetch
    var allowed = 2 // the parts the fake disk has room for
    val stopped = BasemapFetch.download(http, plan, work, tiny, _ => (), _ => if allowed > 0 then { allowed -= 1; Right(()) } else Left("full"))
    assertEquals(stopped, Left("full"))
    assertEquals(http.downloads.get, 2)
    // again, with the same plan: the two parts are kept, the rest fetched
    assertEquals(BasemapFetch.prepare(work, BasemapFetch.stateOf(plan, "")), 2)
    BasemapFetch.download(http, plan, work, tiny, _ => (), _ => Right(())).fold(fail(_), identity)
    assertEquals(http.downloads.get, plan.requests.size)
    // another plan (a deeper zoom) empties the work directory first
    val deeper = plan.copy(maxZoom = 7)
    assertEquals(BasemapFetch.prepare(work, BasemapFetch.stateOf(deeper, "")), 0)

  test("a build replaced during the download is refused, not mixed"):
    val dir  = tmp()
    val src  = BasemapFixtures.terrainArchive(dir.resolve("planet.pmtiles"), maxZoom = 5)
    val http = BasemapFixtures.LocalRange(src)
    val plan = BasemapFetch.plan(http, BasemapFetch.Source("fake://t", "t"), aegean, 5, noPause, _ => ()).fold(fail(_), identity)
    http.etag = "\"v2\""
    val work = dir.resolve("work")
    BasemapFetch.prepare(work, BasemapFetch.stateOf(plan, ""))
    val r = BasemapFetch.download(http, plan, work, noPause, _ => (), _ => Right(()))
    assert(r.left.exists(_.contains("changed during the download")), r.toString)
    assertEquals(BasemapFetch.pending(plan, work).size, plan.requests.size)

  test("a provider size that differs from the listed one is refused"):
    val dir  = tmp()
    val src  = BasemapFixtures.terrainArchive(dir.resolve("planet.pmtiles"), maxZoom = 3)
    val http = BasemapFixtures.LocalRange(src)
    val r = BasemapFetch.plan(http, BasemapFetch.Source("fake://t", "b", Some(1L)), aegean, 3, noPause, _ => ())
    assert(r.left.exists(_.contains("the provider lists 1")), r.toString)

  test("the extracts live in the XDG cache unless basemap.dir moves them"):
    val cfg = AppConfig.load()
    assertEquals(BasemapService.dir(cfg, Map("XDG_CACHE_HOME" -> "/x/cache").get), Path.of("/x/cache/histmersion/basemap"))
    assertEquals(BasemapService.dir(cfg.copy(basemap = cfg.basemap.copy(dir = "/y/maps")), Map.empty[String, String].get),
      Path.of("/y/maps"))

  private type Json = io.circe.Json
