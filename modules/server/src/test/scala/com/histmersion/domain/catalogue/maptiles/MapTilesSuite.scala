package com.histmersion.domain.catalogue.maptiles

import com.histmersion.api.catalogue.{Period, TileLayer, TilesApi}
import com.histmersion.api.library.Filter

/** The tile service's pure half: what a request's key reads (and what it leaves out), its ETag and the browser's
  * revalidation, and the cache's bound.
  */
class MapTilesSuite extends munit.FunSuite:

  private val c = TileCoord.of(4, 9, 5, 11).toOption.get
  private def q(f: Filter = Filter(period = Some(Period(-1500, -1000))), layer: String = "sites") =
    TilesApi.TileQuery(layer, 4, 9, 5, f)
  private val v = Map("places" -> 3L, "corpus" -> 1L, "events" -> 7L, "media" -> 2L)

  test("a key holds the layer, the tile, what the layer reads and the versions of its groups"):
    val k = MapTiles.key(TileLayer.Sites, c, q(), v)
    assert(k.startsWith("sites|4/9/5|-1500..-1000|"), k)
    assert(k.contains("places=3") && k.contains("corpus=1") && k.contains("events=7") && !k.contains("media="), k)
    // a write to a group the layer reads is a new key; to another one, not
    assertNotEquals(MapTiles.key(TileLayer.Sites, c, q(), v.updated("places", 4L)), k)
    assertEquals(MapTiles.key(TileLayer.Sites, c, q(), v.updated("media", 9L)), k)
    // another layer's options and a filter's paging do not split a tile
    assertEquals(MapTiles.key(TileLayer.Sites, c, q().copy(provider = Some("met"), tier = Some("attested")), v), k)
    val ev = q(layer = "events")
    assertEquals(
      MapTiles.key(TileLayer.Events, c, ev.copy(filter = ev.filter.copy(limit = 50, offset = 10)), v),
      MapTiles.key(TileLayer.Events, c, ev, v)
    )
    assertNotEquals(
      MapTiles.key(TileLayer.Events, c, ev.copy(filter = ev.filter.copy(tags = List("x"))), v),
      MapTiles.key(TileLayer.Events, c, ev, v)
    )
    assertNotEquals(MapTiles.key(TileLayer.Sites, c, q().copy(siteType = Some("burial")), v), k)

  test("an ETag is the key's, quoted; If-None-Match names it, weak or in a list"):
    val t = MapTiles.etag("a")
    assert(t.startsWith("\"") && t.endsWith("\"") && t.length == 26, t)
    assertEquals(MapTiles.etag("a"), t)
    assertNotEquals(MapTiles.etag("b"), t)
    assert(MapTiles.matches(Some(t), t))
    assert(MapTiles.matches(Some(s"W/$t"), t))
    assert(MapTiles.matches(Some(s"\"x\", $t"), t))
    assert(MapTiles.matches(Some("*"), t))
    assert(!MapTiles.matches(Some("\"x\""), t))
    assert(!MapTiles.matches(None, t))

  test("the cache keeps at most its bytes, the least recently asked for going first"):
    val cache = TileCache(() => 10L)
    cache.put("a", Array.fill(4)(0))
    cache.put("b", Array.fill(4)(0))
    assert(cache.get("a").isDefined) // a is now the most recent
    cache.put("c", Array.fill(4)(0))
    assertEquals((cache.get("a").isDefined, cache.get("b").isDefined, cache.get("c").isDefined), (true, false, true))
    assertEquals(cache.stats, (2, 8L))
    cache.clear()
    assertEquals(cache.stats, (0, 0L))
