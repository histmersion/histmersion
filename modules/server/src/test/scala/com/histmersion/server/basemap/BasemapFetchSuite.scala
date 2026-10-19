package com.histmersion.server.basemap

import com.histmersion.core.*

import java.nio.file.{Files, Path}

/** The base-map extract (plan → download → assemble) against a local archive behind a fake range
  * server — no network — and its pure parts. */
class BasemapFetchSuite extends munit.FunSuite:

  private val noPause = BasemapFetch.Pace(0, 1L << 20, 0.05, _ => ())
  private val aegean  = BBox(22, 35, 29, 41)
  private val keep = Basemap.parseLayers(AppConfig.load().basemap.vector.layers).fold(fail(_), identity)
    .map(r => r.layer -> Mvt.Rule(r.keep, r.drop)).toMap

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

  test("the layers setting: kinds kept and dropped, a layer named twice refused"):
    assertEquals(Basemap.parseLayers("earth; water -canal -ditch; landcover forest"),
      Right(List(Basemap.LayerRule("earth", Set(), Set()), Basemap.LayerRule("water", Set(), Set("canal", "ditch")),
        Basemap.LayerRule("landcover", Set("forest"), Set()))))
    assert(Basemap.parseLayers(" ; ").isLeft)
    assert(Basemap.parseLayers("earth; earth").isLeft)
    assert(Basemap.validBBox("29,35,22,41").isLeft)
    assert(Basemap.validBBox("-200,0,10,10").isLeft)
    assertEquals(Basemap.validBBox("22,35,29,41"), Right(aegean))

  test("a vector tile is thinned: no roads, places or land use; canals and farmland gone; rivers and forest kept"):
    val raw  = BasemapFixtures.vectorTile(3, 4, 3)
    val thin = BasemapService.thin(PmTiles.gzip(raw), PmTiles.Compression.Gzip, keep).fold(fail(_), identity).get
    val t    = PmTiles.gunzip(thin).fold(fail(_), identity)
    assertEquals(Mvt.layerNames(t), Right(List("earth", "water", "landcover")))
    assertEquals(Mvt.kinds(t, "water"), Right(List("ocean", "river")))
    assertEquals(Mvt.kinds(t, "landcover"), Right(List("forest")))
    // nothing left: the tile is dropped
    val roadsOnly = Mvt.tile(List(Mvt.layer("roads", List((2, Vector(9, 0, 0, 10, 5, 5), Map("kind" -> "highway"))))))
    assertEquals(BasemapService.thin(roadsOnly, PmTiles.Compression.None, keep), Right(None))

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
      BasemapFetch.localHeader(plan), BasemapFetch.localMetadata(plan, Some(keep.keySet), "© OSM", "2026-10-19T00:00:00Z",
        BasemapLayer.Vector), _ => ()).fold(fail(_), identity)
    assertEquals(n, plan.tiles.size.toLong)
    val f = PmTilesFile.open(out).fold(fail(_), identity)
    try
      assertEquals(f.header.maxZoom, 5)
      assertEquals((f.header.minLon, f.header.maxLat), (22.0, 41.0))
      val t = f.tile(5, 18, 12).fold(fail(_), identity).map(b => PmTiles.gunzip(b).fold(fail(_), identity)).get
      assert(!Mvt.layerNames(t).toOption.get.contains("roads"))
      assertEquals(f.tile(5, 0, 0), Right(None), "outside the region")
      assertEquals(f.metadata.hcursor.downField("histmersion").get[String]("build"), Right("planet"))
      assertEquals(f.metadata.hcursor.downField("vector_layers").as[List[Json]].map(_.size), Right(3))
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
