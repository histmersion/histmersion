package com.histmersion.server.basemap

import com.histmersion.core.*

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger

/** The on-demand relief: the bounded tile cache (eviction by last use, kept across a restart) and the
  * reads from a fake provider — once per tile, a missing tile remembered, nothing asked while offline,
  * a replaced planet file read again, the request rate kept. No network, no database. */
class OnDemandTilesSuite extends munit.FunSuite:

  private def cfg(enabled: Boolean = true, maxZoom: Int = 5, cacheBytes: Long = 1_000_000_000L, rps: Double = 1000) =
    BasemapOnDemandConfig(enabled, maxZoom, cacheBytes / 1e9, concurrency = 2, requestsPerSecond = rps, retryAfterSeconds = 30)

  test("the cache keeps tiles to its ceiling, dropping the least recently used first; the order survives a restart"):
    val root = Files.createTempDirectory("lru")
    val lru = DiskLru(root, () => 30)
    lru.put("1/0/0", Some(Array.fill(10)(1)))
    Thread.sleep(5)
    lru.put("1/0/1", Some(Array.fill(10)(2)))
    Thread.sleep(5)
    lru.put("1/1/0", None)                              // a missing tile: remembered, no bytes
    assertEquals((lru.entries, lru.bytes), (3L, 20L))
    Thread.sleep(5)
    assert(lru.get("1/0/0").exists(_.exists(_.head == 1)), "read: now the most recently used")
    Thread.sleep(5)
    lru.put("1/1/1", Some(Array.fill(15)(3)))           // 35 > 30: the least recently used (1/0/1) goes
    assertEquals(lru.get("1/0/1"), None)
    assert(!Files.exists(root.resolve("1/0/1.webp")))
    assertEquals(lru.get("1/1/0"), Some(None))
    assertEquals(lru.bytes, 25L)
    // a restart: the index is read from the disk, in the order of use
    val again = DiskLru(root, () => 20)
    assertEquals(again.entries, 1L, "over the new ceiling at once: the eldest dropped (the missing 1/1/0, then 1/0/0)")
    assert(again.get("1/1/1").isDefined)
    assertEquals(again.clear(), 15L)
    assertEquals(again.entries, 0L)

  /** A provider serving a terrain archive of zooms 0–5, counting requests; `fail` makes it unreachable. */
  private final class Provider(file: Path) extends RangeHttp:
    val local = BasemapFixtures.LocalRange(file)
    @volatile var fail = false
    def text(url: String) = local.text(url)
    def bytes(url: String, o: Long, l: Long) = if fail then Left(s"$url unreachable") else local.bytes(url, o, l)
    def download(url: String, o: Long, l: Long, to: Path) = local.download(url, o, l, to)

  private def setup(c: BasemapOnDemandConfig = cfg()) =
    val src = Files.createTempDirectory("planet")
    val p = Provider(BasemapFixtures.terrainArchive(src.resolve("planet.pmtiles"), 5))
    val dir = Files.createTempDirectory("ondemand")
    val clock = AtomicInteger(1000)
    val slept = AtomicInteger(0)
    val od = OnDemandTiles(dir, () => c, OnDemandTiles.Source.Planet(() => "fake://planet"), () => p, () => clock.get.toLong, ms => { slept.addAndGet(ms.toInt); () })
    (od, p, dir, clock, slept)

  test("a tile is read once from the provider and then from the cache — also by a new instance (offline)"):
    val (od, p, dir, _, _) = setup()
    assertEquals(od.tile(5, 18, 12).map(_.map(String(_))), Right(Some("RIFF5/18/12")))
    val first = p.local.requests.get
    assert(first >= 2, "the header and root, then the tile")
    assertEquals(od.tile(5, 18, 12).map(_.map(String(_))), Right(Some("RIFF5/18/12")))
    assertEquals(p.local.requests.get, first, "the second time from the cache")
    assertEquals(od.tile(5, 18, 13).map(_.map(String(_))), Right(Some("RIFF5/18/13")))
    assertEquals(p.local.requests.get, first + 1, "the planet's directory is read once: one request a tile")
    p.fail = true
    val restarted = OnDemandTiles(dir, () => cfg(), OnDemandTiles.Source.Planet(() => "fake://planet"), () => p)
    assertEquals(restarted.tile(5, 18, 12).map(_.map(String(_))), Right(Some("RIFF5/18/12")), "an area seen once, offline")
    assertEquals(restarted.status.tiles, 2L)

  test("outside the configured zooms or switched off: nothing is asked"):
    val (od, p, _, _, _) = setup(cfg(maxZoom = 4))
    assertEquals(od.tile(5, 18, 12), Right(None))
    assert(!od.serves(5, 3) && od.serves(4, 3) && !od.serves(3, 3))
    val (off, p2, _, _, _) = setup(cfg(enabled = false))
    assertEquals(off.tile(4, 9, 6), Right(None))
    assertEquals(p.local.requests.get + p2.local.requests.get, 0)

  test("offline: one failed request, then none for retry-after-seconds; the page gets no tile meanwhile"):
    val (od, p, _, clock, _) = setup()
    p.fail = true
    assert(od.tile(5, 18, 12).isLeft)
    assert(od.status.offlineUntil.isDefined)
    p.fail = false
    val before = p.local.requests.get
    assert(od.tile(5, 18, 12).isLeft, "still within the pause")
    assertEquals(p.local.requests.get, before)
    clock.addAndGet(31000)
    assert(od.tile(5, 18, 12).toOption.flatten.isDefined, "asked again after the pause")

  test("the provider replaced its file (another ETag): its directory is read again, the tile still served"):
    val (od, p, _, _, _) = setup()
    assert(od.tile(5, 18, 12).toOption.flatten.isDefined)
    p.local.etag = "\"v2\""
    assertEquals(od.tile(5, 18, 13).map(_.map(String(_))), Right(Some("RIFF5/18/13")))

  test("the request rate: requests begin at most requests-per-second apart"):
    val (od, _, _, _, slept) = setup(cfg(rps = 4))
    assert(od.tile(5, 18, 12).toOption.flatten.isDefined)
    assert(od.tile(5, 18, 13).toOption.flatten.isDefined)
    assert(slept.get >= 2 * 250, s"slept ${slept.get} ms for 3 requests at 4 a second (the clock stands still)")

  test("a tile asked for by several requests at once is read once"):
    val (od, p, _, _, _) = setup()
    assert(od.tile(5, 0, 0).isRight)  // the directory read
    val before = p.local.requests.get
    val ts = (1 to 8).map(_ => Thread.ofVirtual().start(() => { val _ = od.tile(5, 20, 12) }))
    ts.foreach(_.join())
    assertEquals(p.local.requests.get, before + 1)

  test("focus regions: a named one, a new one with its box, one named after a deep regional relief fetch; refused without a box"):
    val t = BasemapTerrainConfig("u", 8, "", cfg(maxZoom = 12), List("aegean 21.5,35,28.5,41.5", "argolid 22.5,37.4,23.2,37.9"))
    val box = BBox(22.5, 37.4, 23.2, 37.9)
    assertEquals(BasemapService.focusOf(Some("Aegean"), None, false, None, t), Right(Some(("aegean", BBox(21.5, 35, 28.5, 41.5)))))
    assertEquals(BasemapService.focusOf(Some("mine"), Some(box), false, None, t), Right(Some(("mine", box))))
    assert(BasemapService.focusOf(Some("nowhere"), None, false, None, t).left.exists(_.contains("give its --bbox")))
    assert(BasemapService.focusOf(Some("bad name!"), Some(box), false, None, t).isLeft)
    assertEquals(BasemapService.focusOf(None, Some(box), true, Some(12), t), Right(Some(("bbox-22p5-37p4-23p2-37p9", box))))
    assertEquals(BasemapService.focusOf(None, Some(box), true, Some(8), t), Right(None), "not deeper than the extract: the extract itself")
    assertEquals(BasemapService.focusOf(None, Some(box), false, Some(12), t), Right(None), "with other layers: the extract")
    assertEquals(Basemap.focusRegion("nile-delta 29,30,32.5,31.7"), Right(("nile-delta", BBox(29, 30, 32.5, 31.7))))
    assert(Basemap.focusRegion("x").isLeft && Basemap.focusRegion("x 1,2,3").isLeft)

  test("#3760: a tile URL source — one GET a tile, a 404 kept as none, each transformed before it is kept"):
    val dir = Files.createTempDirectory("ondemand-get")
    val gets = AtomicInteger(0)
    val http = new RangeHttp:
      def text(url: String) = Left("no")
      def bytes(url: String, o: Long, l: Long) = Left("no range requests here")
      def download(url: String, o: Long, l: Long, to: Path) = Left("no")
      override def file(url: String) =
        gets.incrementAndGet()
        Right(Option.when(!url.endsWith("/0/0.png"))(url.getBytes))
    val od = OnDemandTiles(dir, () => cfg(), OnDemandTiles.Source.Template(() => "fake://sea/{z}/{x}/{y}.png"), () => http,
      ext = "png", transform = b => Right(Some(("T:" + String(b)).getBytes)))
    assertEquals(od.tile(2, 1, 3).map(_.map(String(_))), Right(Some("T:fake://sea/2/1/3.png")))
    assertEquals(od.tile(2, 1, 3).map(_.map(String(_))), Right(Some("T:fake://sea/2/1/3.png")))
    assertEquals(od.tile(1, 0, 0), Right(None))
    assertEquals(od.tile(1, 0, 0), Right(None))
    assertEquals(gets.get, 2, "each tile asked once")
    assert(Files.isRegularFile(dir.resolve("tiles/2/1/3.png")) && Files.isRegularFile(dir.resolve("tiles/1/0/0.none")))
    assert(od.servesAt(3) && !od.servesAt(cfg().maxZoom + 1))
