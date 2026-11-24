package com.histmersion
package domain.catalogue.maptiles

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.{Period, TileLayer, TilesApi}
import com.histmersion.api.library.Filter
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{LintRule, tolerated}

/** The tile service's pure half: what a request's key reads (and what it leaves out), its ETag and the browser's
  * revalidation, and the cache's bound.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class MapTilesSuite extends munit.FunSuite:

  private val c = TileCoord.of(TileCoord.Z(4), TileCoord.X(9), TileCoord.Y(5), TileCoord.Z(11)).toOption.get
  private def q(
      f: Filter = Filter(period = Some(Period(Year(-1500), Year(-1000)))),
      layer: TilesApi.TileQuery.Layer = TilesApi.TileQuery.Layer("sites")
  ) =
    TilesApi.TileQuery(layer, TilesApi.TileQuery.Z(4), TilesApi.TileQuery.X(9), TilesApi.TileQuery.Y(5), f)
  private val v = Map(
    TileRepo.GroupName("places") -> UndatedBuild.Version(3L),
    TileRepo.GroupName("corpus") -> UndatedBuild.Version(1L),
    TileRepo.GroupName("events") -> UndatedBuild.Version(7L),
    TileRepo.GroupName("media") -> UndatedBuild.Version(2L)
  )

  test("a key holds the layer, the tile, what the layer reads and the versions of its groups"):
    val k = MapTiles.key(TileLayer.Sites, c, q(), v)
    assert(k.value.startsWith("sites|4/9/5|-1500..-1000|"), k)
    assert(
      k.value.contains("places=3") && k.value.contains("corpus=1") && k.value.contains("events=7") && !k.value.contains(
        "media="
      ),
      k
    )
    // a write to a group the layer reads is a new key; to another one, not
    assertNotEquals(
      MapTiles.key(TileLayer.Sites, c, q(), v.updated(TileRepo.GroupName("places"), UndatedBuild.Version(4L))),
      k
    )
    assertEquals(
      MapTiles.key(TileLayer.Sites, c, q(), v.updated(TileRepo.GroupName("media"), UndatedBuild.Version(9L))),
      k
    )
    // another layer's options and a filter's paging do not split a tile
    assertEquals(
      MapTiles.key(
        TileLayer.Sites,
        c,
        q()
          .modify(_.provider)
          .setTo(Some(TilesApi.TileQuery.Provider("met")))
          .modify(_.tier)
          .setTo(Some(TilesApi.TileQuery.Tier("attested"))),
        v
      ),
      k
    )
    val ev = q(layer = TilesApi.TileQuery.Layer("events"))
    assertEquals(
      MapTiles.key(
        TileLayer.Events,
        c,
        ev.modify(_.filter)
          .setTo(ev.filter.modify(_.limit).setTo(Filter.Limit(50)).modify(_.offset).setTo(Filter.Offset(10))),
        v
      ),
      MapTiles.key(TileLayer.Events, c, ev, v)
    )
    assertNotEquals(
      MapTiles
        .key(TileLayer.Events, c, ev.modify(_.filter).setTo(ev.filter.modify(_.tags).setTo(List(Filter.Tags("x")))), v),
      MapTiles.key(TileLayer.Events, c, ev, v)
    )
    assertNotEquals(
      MapTiles.key(TileLayer.Sites, c, q().modify(_.siteType).setTo(Some(TilesApi.TileQuery.SiteType("burial"))), v),
      k
    )

  test("an ETag is the key's, quoted; If-None-Match names it, weak or in a list"):
    val t = MapTiles.etag(MapTiles.TileKey("a"))
    assert(t.value.startsWith("\"") && t.value.endsWith("\"") && t.value.length == 26, t)
    assertEquals(MapTiles.etag(MapTiles.TileKey("a")), t)
    assertNotEquals(MapTiles.etag(MapTiles.TileKey("b")), t)
    assert(MapTiles.matches(Some(t), t))
    assert(MapTiles.matches(Some(MapTiles.Etag(s"W/$t")), t))
    assert(MapTiles.matches(Some(MapTiles.Etag(s"\"x\", $t")), t))
    assert(MapTiles.matches(Some(MapTiles.Etag("*")), t))
    assert(!MapTiles.matches(Some(MapTiles.Etag("\"x\"")), t))
    assert(!MapTiles.matches(None, t))

  test("the cache keeps at most its bytes, the least recently asked for going first"):
    val cache = TileCache(() => ByteCount(10L))
    cache.put(MapTiles.TileKey("a"), VectorTileWriter.VectorTile(Array.fill(4)(0)))
    cache.put(MapTiles.TileKey("b"), VectorTileWriter.VectorTile(Array.fill(4)(0)))
    assert(cache.get(MapTiles.TileKey("a")).isDefined) // a is now the most recent
    cache.put(MapTiles.TileKey("c"), VectorTileWriter.VectorTile(Array.fill(4)(0)))
    assertEquals(
      (
        cache.get(MapTiles.TileKey("a")).isDefined,
        cache.get(MapTiles.TileKey("b")).isDefined,
        cache.get(MapTiles.TileKey("c")).isDefined
      ),
      (true, false, true)
    )
    assertEquals(cache.stats, (2, 8L))
    cache.clear()
    assertEquals(cache.stats, (0, 0L))
