package com.histmersion
package domain.catalogue.maptiles

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.*

import java.util.concurrent.atomic.AtomicReference

/** The undated sites of a tile, over sites held in memory: counted by squares, the counts adding up to every site, and
  * listed one by one but those the span already shows.
  */
class UndatedSitesSuite extends munit.FunSuite:
  private val grid =
    TileGrid.of(maxZoom = TileGrid.MaxZoom(11), maxPoints = TileGrid.MaxPoints(600), cellPx = TileGrid.CellPx(32))
  private val sites = (0 until 30)
    .flatMap(i =>
      (0 until 30)
        .map(j =>
          UndatedSite(
            UndatedSite.Id(f"u$i%02d$j%02d"),
            Latitude.applyUnsafe(37.0 + i * 0.01),
            Longitude.applyUnsafe(22.0 + j * 0.01),
            Option.when(i % 2 == 0)(UndatedSite.SiteType("tower"))
          )
        )
    )
    .toList
  private val service = UndatedSites.InMemory(AtomicReference(sites))

  private def tileAt(z: TileCoord.Z, lat: Latitude, lon: Longitude): TileCoord =
    val (mx, my) = TileCoord.mercator(lat, lon)
    TileCoord
      .of(
        z,
        TileCoord.X(math.floor(mx * (1 << z.value)).toLong),
        TileCoord.Y(math.floor(my * (1 << z.value)).toLong),
        TileCoord.Z(22)
      )
      .toOption
      .get

  test("counted by squares, the counts add up to every site of the tile, of a type when asked"):
    val c = tileAt(TileCoord.Z(6), Latitude(37.1), Longitude(22.1))
    val counted = service.counted(c, grid, None)
    assertEquals(counted.map(_.n.value).sum, 900)
    assertEquals(service.counted(c, grid, Some(TileRepo.SiteType("tower"))).map(_.n.value).sum, 450)
    assert(
      counted.forall(g =>
        g.gx.value / UndatedSites.perSideOf(grid).value == c.x.value && g.gy.value / UndatedSites
          .perSideOf(grid)
          .value == c.y.value
      )
    )

  test("merged with no dated site, a crowded tile's cells all say how many have no known date"):
    val c = tileAt(TileCoord.Z(6), Latitude(37.1), Longitude(22.1))
    val cells = UndatedCells.merge(c.z, Nil, service.counted(c, grid, None))
    assertEquals(cells.map(_.n.value).sum, 900)
    assert(cells.forall(cell => cell.undated.value == cell.n.value))

  test("listed one by one, marked, but the sites the span shows by their findings"):
    val c = tileAt(TileCoord.Z(11), Latitude(37.0), Longitude(22.0))
    val listed = service.items(c, None, except = List(TileRepo.SiteId("u0000")))
    assert(listed.nonEmpty)
    assert(!listed.exists(_.key == TileCell.Key("u0000")))
    assert(listed.forall(i => i.undated == TileCell.Undated(1) && i.n == TileCell.N(1)))
