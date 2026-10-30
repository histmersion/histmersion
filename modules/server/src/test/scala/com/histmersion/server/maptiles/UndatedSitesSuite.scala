package com.histmersion.server.maptiles

import com.histmersion.domain.*
import com.histmersion.domain.maptiles.UndatedCells

import java.util.concurrent.atomic.AtomicReference

/** The undated sites of a tile, over sites held in memory: counted by squares, the counts adding up to every site, and
  * listed one by one but those the span already shows.
  */
class UndatedSitesSuite extends munit.FunSuite:
  private val grid = TileGrid.of(maxZoom = 11, maxPoints = 600, cellPx = 32)
  private val sites = (0 until 30)
    .flatMap(i =>
      (0 until 30)
        .map(j => UndatedSite(f"u$i%02d$j%02d", 37.0 + i * 0.01, 22.0 + j * 0.01, Option.when(i % 2 == 0)("tower")))
    )
    .toList
  private val service = UndatedSites.InMemory(AtomicReference(sites))

  private def tileAt(z: Int, lat: Double, lon: Double): TileCoord =
    val (mx, my) = TileCoord.mercator(lat, lon)
    TileCoord.of(z, math.floor(mx * (1 << z)).toLong, math.floor(my * (1 << z)).toLong, 22).toOption.get

  test("counted by squares, the counts add up to every site of the tile, of a type when asked"):
    val c = tileAt(6, 37.1, 22.1)
    val counted = service.counted(c, grid, None)
    assertEquals(counted.map(_.n).sum, 900)
    assertEquals(service.counted(c, grid, Some("tower")).map(_.n).sum, 450)
    assert(
      counted.forall(g => g.gx / UndatedSites.perSideOf(grid) == c.x && g.gy / UndatedSites.perSideOf(grid) == c.y)
    )

  test("merged with no dated site, a crowded tile's cells all say how many have no known date"):
    val c = tileAt(6, 37.1, 22.1)
    val cells = UndatedCells.merge(c.z, Nil, service.counted(c, grid, None))
    assertEquals(cells.map(_.n).sum, 900)
    assert(cells.forall(cell => cell.undated == cell.n))

  test("listed one by one, marked, but the sites the span shows by their findings"):
    val c = tileAt(11, 37.0, 22.0)
    val listed = service.items(c, None, except = List("u0000"))
    assert(listed.nonEmpty)
    assert(!listed.exists(_.key == "u0000"))
    assert(listed.forall(i => i.undated == 1 && i.n == 1))
