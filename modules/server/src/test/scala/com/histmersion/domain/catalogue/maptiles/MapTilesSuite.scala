package com.histmersion.domain.catalogue.maptiles

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.{Period, TileLayer, TilesApi}
import com.histmersion.api.library.Filter

/** The tile service's pure half: what a request's key reads (and what it leaves out), its ETag and the browser's
  * revalidation, and the cache's bound.
  */
class MapTilesSuite extends munit.FunSuite:

  private val c = TileCoord.of(com.histmersion.domain.catalogue.maptiles.TileCoord.Z(4), com.histmersion.domain.catalogue.maptiles.TileCoord.X(9), com.histmersion.domain.catalogue.maptiles.TileCoord.Y(5), com.histmersion.domain.catalogue.maptiles.TileCoord.Z(11)).toOption.get
  private def q(f: Filter = Filter(period = Some(Period(com.histmersion.api.catalogue.Year(-1500), com.histmersion.api.catalogue.Year(-1000)))), layer: String = "sites") =
    TilesApi.TileQuery(layer, com.histmersion.api.catalogue.TilesApi.TileQuery.Z(4), com.histmersion.api.catalogue.TilesApi.TileQuery.X(9), com.histmersion.api.catalogue.TilesApi.TileQuery.Y(5), f)
  private val v = Map("places" -> 3L, "corpus" -> 1L, "events" -> 7L, "media" -> 2L)

  test("a key holds the layer, the tile, what the layer reads and the versions of its groups"):
    val k = MapTiles.key(TileLayer.Sites, c, q(), v)
    assert(k.startsWith("sites|4/9/5|-1500..-1000|"), k)
    assert(k.contains("places=3") && k.contains("corpus=1") && k.contains("events=7") && !k.contains("media="), k)
    // a write to a group the layer reads is a new key; to another one, not
    assertNotEquals(MapTiles.key(TileLayer.Sites, c, q(), v.updated("places", 4L)), k)
    assertEquals(MapTiles.key(TileLayer.Sites, c, q(), v.updated("media", 9L)), k)
    // another layer's options and a filter's paging do not split a tile
    assertEquals(MapTiles.key(TileLayer.Sites, c, q().modify(_.provider).setTo(Some("met")).modify(_.tier).setTo(Some("attested")), v), k)
    val ev = q(layer = "events")
    assertEquals(
      MapTiles.key(TileLayer.Events, c, ev.modify(_.filter).setTo(ev.filter.modify(_.limit).setTo(50).modify(_.offset).setTo(10)), v),
      MapTiles.key(TileLayer.Events, c, ev, v)
    )
    assertNotEquals(
      MapTiles.key(TileLayer.Events, c, ev.modify(_.filter).setTo(ev.filter.modify(_.tags).setTo(List("x"))), v),
      MapTiles.key(TileLayer.Events, c, ev, v)
    )
    assertNotEquals(MapTiles.key(TileLayer.Sites, c, q().modify(_.siteType).setTo(Some("burial")), v), k)

  test("an ETag is the key's, quoted; If-None-Match names it, weak or in a list"):
    val t = MapTiles.etag(com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey("a"))
    assert(t.startsWith("\"") && t.endsWith("\"") && t.length == 26, t)
    assertEquals(MapTiles.etag(com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey("a")), t)
    assertNotEquals(MapTiles.etag(com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey("b")), t)
    assert(MapTiles.matches(Some(t), t))
    assert(MapTiles.matches(Some(s"W/$t"), t))
    assert(MapTiles.matches(Some(s"\"x\", $t"), t))
    assert(MapTiles.matches(Some(com.histmersion.domain.catalogue.maptiles.MapTiles.Etag("*")), t))
    assert(!MapTiles.matches(Some(com.histmersion.domain.catalogue.maptiles.MapTiles.Etag("\"x\"")), t))
    assert(!MapTiles.matches(None, t))

  test("the cache keeps at most its bytes, the least recently asked for going first"):
    val cache = TileCache(() => com.histmersion.ByteCount(10L))
    cache.put(com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey("a"), Array.fill(4)(0))
    cache.put(com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey("b"), Array.fill(4)(0))
    assert(cache.get(com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey("a")).isDefined) // a is now the most recent
    cache.put(com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey("c"), Array.fill(4)(0))
    assertEquals((cache.get(com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey("a")).isDefined, cache.get(com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey("b")).isDefined, cache.get(com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey("c")).isDefined), (true, false, true))
    assertEquals(cache.stats, (2, 8L))
    cache.clear()
    assertEquals(cache.stats, (0, 0L))
