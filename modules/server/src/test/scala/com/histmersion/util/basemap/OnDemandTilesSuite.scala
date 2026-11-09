package com.histmersion
package util.basemap
import com.histmersion.api.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.{ByteCount, ByteOffset}
import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.config.*
import com.histmersion.domain.*

import com.histmersion.util.storage.{ByteSource, Reservation}

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import com.histmersion.api.basemap.Basemap

/** The on-demand relief: the bounded tile cache (eviction by last use, kept across a restart) and the reads from a fake
  * provider — once per tile, a missing tile remembered, nothing asked while offline, a replaced planet file read again,
  * the request rate kept. No network, no database.
  */
class OnDemandTilesSuite extends munit.FunSuite:

  private def cfg(
      enabled: BasemapOnDemandConfig.Enabled = BasemapOnDemandConfig.Enabled(true),
      maxZoom: BasemapOnDemandConfig.MaxZoom = BasemapOnDemandConfig.MaxZoom(5),
      cacheBytes: ByteCount = ByteCount(1_000_000_000L),
      rps: BasemapOnDemandConfig.RequestsPerSecond = BasemapOnDemandConfig.RequestsPerSecond(1000)
  ) =
    BasemapOnDemandConfig(
      enabled,
      maxZoom,
      BasemapOnDemandConfig.CacheGb(cacheBytes.value / 1e9),
      concurrency = BasemapOnDemandConfig.Concurrency(2),
      requestsPerSecond = rps,
      retryAfterSeconds = BasemapOnDemandConfig.RetryAfterSeconds(30)
    )

  test("the cache keeps tiles to its ceiling, dropping the least recently used first; the order survives a restart"):
    val root = Files.createTempDirectory("lru")
    val lru = DiskLru(BasemapFixtures.store(root), () => DiskLru.MaxBytes(30))(using java.time.Clock.systemUTC())
    lru.put(DiskLru.Key("1/0/0"), Some(ByteSource.Content(Array.fill(10)(1))))
    Thread.sleep(5)
    lru.put(DiskLru.Key("1/0/1"), Some(ByteSource.Content(Array.fill(10)(2))))
    Thread.sleep(5)
    lru.put(DiskLru.Key("1/1/0"), None) // a missing tile: remembered, no bytes
    assertEquals((lru.entries, lru.bytes), (3L, 20L))
    Thread.sleep(5)
    assert(lru.get(DiskLru.Key("1/0/0")).exists(_.exists(_.value.head == 1)), "read: now the most recently used")
    Thread.sleep(5)
    lru.put(
      DiskLru.Key("1/1/1"),
      Some(ByteSource.Content(Array.fill(15)(3)))
    ) // 35 > 30: the least recently used (1/0/1) goes
    assertEquals(lru.get(DiskLru.Key("1/0/1")), None)
    assert(!Files.exists(root.resolve("1/0/1.webp")))
    assertEquals(lru.get(DiskLru.Key("1/1/0")), Some(None))
    assertEquals(lru.bytes, 25L)
    // a restart: the index is read from the disk, in the order of use
    val again = DiskLru(BasemapFixtures.store(root), () => DiskLru.MaxBytes(20))(using java.time.Clock.systemUTC())
    assertEquals(again.entries, 1L, "over the new ceiling at once: the eldest dropped (the missing 1/1/0, then 1/0/0)")
    assert(again.get(DiskLru.Key("1/1/1")).isDefined)
    assertEquals(again.clear(), 15L)
    assertEquals(again.entries, 0L)

  /** A provider serving a terrain archive of zooms 0–5, counting requests; `fail` makes it unreachable. */
  final private class Provider(file: Path) extends RangeHttp:
    val local = BasemapFixtures.LocalRange(file)
    @volatile var fail = false
    def text(url: RangeHttp.Url) = local.text(url)
    def bytes(url: RangeHttp.Url, o: ByteOffset, l: ByteCount) =
      if fail then Left(Problem.Unclassified(Legacy.Text(s"${url.value} unreachable"))) else local.bytes(url, o, l)
    def download(url: RangeHttp.Url, o: ByteOffset, l: ByteCount, to: Reservation) = local.download(url, o, l, to)

  private def setup(c: BasemapOnDemandConfig = cfg()) =
    val src = Files.createTempDirectory("planet")
    val p = Provider(BasemapFixtures.terrainArchive(src.resolve("planet.pmtiles"), PmTiles.Zoom(5)))
    val dir = Files.createTempDirectory("ondemand")
    val clock = AtomicInteger(1000)
    val slept = AtomicInteger(0)
    val od = OnDemandTiles(
      BasemapFixtures.store(dir),
      () => c,
      OnDemandTiles.Source.Planet(() => RangeHttp.Url("fake://planet")),
      () => p,
      () => OnDemandTiles.Instant(clock.get.toLong),
      ms => { slept.addAndGet(ms.value.toInt); () }
    )(using java.time.Clock.systemUTC())
    (od, p, dir, clock, slept)

  test("a tile is read once from the provider and then from the cache — also by a new instance (offline)"):
    val (od, p, dir, _, _) = setup()
    assertEquals(
      od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(12)).map(_.map(b => String(b.value))),
      Right(Some("RIFF5/18/12"))
    )
    val first = p.local.requests.get
    assert(first >= 2, "the header and root, then the tile")
    assertEquals(
      od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(12)).map(_.map(b => String(b.value))),
      Right(Some("RIFF5/18/12"))
    )
    assertEquals(p.local.requests.get, first, "the second time from the cache")
    assertEquals(
      od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(13)).map(_.map(b => String(b.value))),
      Right(Some("RIFF5/18/13"))
    )
    assertEquals(p.local.requests.get, first + 1, "the planet's directory is read once: one request a tile")
    p.fail = true
    val restarted =
      OnDemandTiles(
        BasemapFixtures.store(dir),
        () => cfg(),
        OnDemandTiles.Source.Planet(() => RangeHttp.Url("fake://planet")),
        () => p
      )(using java.time.Clock.systemUTC())
    assertEquals(
      restarted.tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(12)).map(_.map(b => String(b.value))),
      Right(Some("RIFF5/18/12")),
      "an area seen once, offline"
    )
    assertEquals(restarted.status.tiles, 2L)

  test("outside the configured zooms or switched off: nothing is asked"):
    val (od, p, _, _, _) = setup(cfg(maxZoom = BasemapOnDemandConfig.MaxZoom(4)))
    assertEquals(od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(12)), Right(None))
    assert(
      !od.serves(PmTiles.Zoom(5), BasemapFetch.Plan.MaxZoom(3)) && od.serves(
        PmTiles.Zoom(4),
        BasemapFetch.Plan.MaxZoom(3)
      ) && !od.serves(PmTiles.Zoom(3), BasemapFetch.Plan.MaxZoom(3))
    )
    val (off, p2, _, _, _) = setup(cfg(enabled = BasemapOnDemandConfig.Enabled(false)))
    assertEquals(off.tile(PmTiles.Zoom(4), PmTiles.TileColumn(9), PmTiles.TileRow(6)), Right(None))
    assertEquals(p.local.requests.get + p2.local.requests.get, 0)

  test("offline: one failed request, then none for retry-after-seconds; the page gets no tile meanwhile"):
    val (od, p, _, clock, _) = setup()
    p.fail = true
    assert(od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(12)).isLeft)
    assert(od.status.offlineUntil.isDefined)
    p.fail = false
    val before = p.local.requests.get
    assert(od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(12)).isLeft, "still within the pause")
    assertEquals(p.local.requests.get, before)
    clock.addAndGet(31000)
    assert(
      od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(12)).toOption.flatten.isDefined,
      "asked again after the pause"
    )

  test("the provider replaced its file (another ETag): its directory is read again, the tile still served"):
    val (od, p, _, _, _) = setup()
    assert(od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(12)).toOption.flatten.isDefined)
    p.local.etag = RemoteFile.Etag("\"v2\"")
    assertEquals(
      od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(13)).map(_.map(b => String(b.value))),
      Right(Some("RIFF5/18/13"))
    )

  test("the request rate: requests begin at most requests-per-second apart"):
    val (od, _, _, _, slept) = setup(cfg(rps = BasemapOnDemandConfig.RequestsPerSecond(4)))
    assert(od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(12)).toOption.flatten.isDefined)
    assert(od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(18), PmTiles.TileRow(13)).toOption.flatten.isDefined)
    assert(slept.get >= 2 * 250, s"slept ${slept.get} ms for 3 requests at 4 a second (the clock stands still)")

  test("a tile asked for by several requests at once is read once"):
    val (od, p, _, _, _) = setup()
    assert(od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(0), PmTiles.TileRow(0)).isRight) // the directory read
    val before = p.local.requests.get
    val ts = (1 to 8).map(_ =>
      Thread.ofVirtual().start { () =>
        val _ = od.tile(PmTiles.Zoom(5), PmTiles.TileColumn(20), PmTiles.TileRow(12))
      }
    )
    ts.foreach(_.join())
    assertEquals(p.local.requests.get, before + 1)

  test(
    "focus regions: a named one, a new one with its box, one named after a deep regional relief fetch; refused without a box"
  ):
    val t = BasemapTerrainConfig(
      BasemapTerrainConfig.Url("u"),
      BasemapTerrainConfig.MaxZoom(8),
      BasemapTerrainConfig.Attribution(""),
      cfg(maxZoom = BasemapOnDemandConfig.MaxZoom(12)),
      List(
        BasemapTerrainConfig.FocusRegions("aegean 21.5,35,28.5,41.5"),
        BasemapTerrainConfig.FocusRegions("argolid 22.5,37.4,23.2,37.9")
      )
    )
    val box = BBox(Longitude(22.5), Latitude(37.4), Longitude(23.2), Latitude(37.9))
    assertEquals(
      BasemapService.focusOf(Some(Basemap.Focus.Name("Aegean")), None, false, None, t),
      Right(Some(("aegean", BBox(Longitude(21.5), Latitude(35), Longitude(28.5), Latitude(41.5)))))
    )
    assertEquals(
      BasemapService.focusOf(Some(Basemap.Focus.Name("mine")), Some(box), false, None, t),
      Right(Some(("mine", box)))
    )
    assert(
      BasemapService
        .focusOf(Some(Basemap.Focus.Name("nowhere")), None, false, None, t)
        .left
        .exists(_.sentence.contains("give its --bbox"))
    )
    assert(BasemapService.focusOf(Some(Basemap.Focus.Name("bad name!")), Some(box), false, None, t).isLeft)
    assertEquals(
      BasemapService.focusOf(None, Some(box), true, Some(BasemapFetch.Plan.MaxZoom(12)), t),
      Right(Some(("bbox-22p5-37p4-23p2-37p9", box)))
    )
    assertEquals(
      BasemapService.focusOf(None, Some(box), true, Some(BasemapFetch.Plan.MaxZoom(8)), t),
      Right(None),
      "not deeper than the extract: the extract itself"
    )
    assertEquals(
      BasemapService.focusOf(None, Some(box), false, Some(BasemapFetch.Plan.MaxZoom(12)), t),
      Right(None),
      "with other layers: the extract"
    )
    assertEquals(
      Basemap.focusRegion(Basemap.Focus.Spec("nile-delta 29,30,32.5,31.7")),
      Right(("nile-delta", BBox(Longitude(29), Latitude(30), Longitude(32.5), Latitude(31.7))))
    )
    assert(
      Basemap.focusRegion(Basemap.Focus.Spec("x")).isLeft && Basemap.focusRegion(Basemap.Focus.Spec("x 1,2,3")).isLeft
    )

  test("#3760: a tile URL source — one GET a tile, a 404 kept as none, each transformed before it is kept"):
    val dir = Files.createTempDirectory("ondemand-get")
    val gets = AtomicInteger(0)
    val http = new RangeHttp:
      def text(url: RangeHttp.Url) = Left(Problem.Unclassified(Legacy.Text("no")))
      def bytes(url: RangeHttp.Url, o: ByteOffset, l: ByteCount) =
        Left(Problem.Unclassified(Legacy.Text("no range requests here")))
      def download(url: RangeHttp.Url, o: ByteOffset, l: ByteCount, to: Reservation) =
        Left(Problem.Unclassified(Legacy.Text("no")))
      override def file(url: RangeHttp.Url) =
        gets.incrementAndGet()
        Right(Option.when(!url.value.endsWith("/0/0.png"))(RangeHttp.ResponseBytes(url.value.getBytes)))
    val od = OnDemandTiles(
      BasemapFixtures.store(dir),
      () => cfg(),
      OnDemandTiles.Source.Template(() => OnDemandTiles.UrlTemplate("fake://sea/{z}/{x}/{y}.png")),
      () => http,
      ext = DiskLru.Ext("png"),
      transform = b => Right(Some(PmTiles.TileBytes(("T:" + String(b.value)).getBytes)))
    )(using java.time.Clock.systemUTC())
    assertEquals(
      od.tile(PmTiles.Zoom(2), PmTiles.TileColumn(1), PmTiles.TileRow(3)).map(_.map(b => String(b.value))),
      Right(Some("T:fake://sea/2/1/3.png"))
    )
    assertEquals(
      od.tile(PmTiles.Zoom(2), PmTiles.TileColumn(1), PmTiles.TileRow(3)).map(_.map(b => String(b.value))),
      Right(Some("T:fake://sea/2/1/3.png"))
    )
    assertEquals(od.tile(PmTiles.Zoom(1), PmTiles.TileColumn(0), PmTiles.TileRow(0)), Right(None))
    assertEquals(od.tile(PmTiles.Zoom(1), PmTiles.TileColumn(0), PmTiles.TileRow(0)), Right(None))
    assertEquals(gets.get, 2, "each tile asked once")
    assert(Files.isRegularFile(dir.resolve("tiles/2/1/3.png")) && Files.isRegularFile(dir.resolve("tiles/1/0/0.none")))
    assert(od.servesAt(PmTiles.Zoom(3)) && !od.servesAt(PmTiles.Zoom(cfg().maxZoom.value + 1)))
