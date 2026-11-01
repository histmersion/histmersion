package com.histmersion.domain.catalogue.maptiles

class UndatedCellsSuite extends munit.FunSuite:

  private def cell(gx: Long, n: Int, one: String, dup: Int = 0, x: Double = 100, mid: Option[Double] = None) =
    GridCell(gx, 7, n, dup, x * n, 50.0 * n, x * dup, 50.0 * dup, mid, mid, 1, 2, 3, 4, one)

  test("a square of dated and undated sites counts both, and how many have no known date"):
    val out = UndatedCells.merge(3, List(cell(5, 2, "a", mid = Some(-500))), List(cell(5, 3, "u")))
    assertEquals(out.map(c => (c.key, c.n, c.undated, c.midLo)), List(("c3/5/7", 5, 3, Some(-500.0))))

  test("an undated site the span shows by its findings counts once, as dated"):
    val out = UndatedCells.merge(3, List(cell(5, 1, "f", dup = 1)), List(cell(5, 1, "f")))
    assertEquals(out.map(c => (c.key, c.n, c.undated, c.x)), List(("f", 1, 0, 100)))
    assertEquals(UndatedCells.total(List(cell(5, 1, "f", dup = 1)), List(cell(5, 1, "f"))), 1L)

  test("squares of one kind only: a single undated site is itself, marked; several are a cell"):
    val out = UndatedCells.merge(2, List(cell(1, 3, "d")), List(cell(2, 1, "u1"), cell(3, 4, "u2")))
    assertEquals(out.map(c => (c.key, c.n, c.undated)), List(("c2/1/7", 3, 0), ("u1", 1, 1), ("c2/3/7", 4, 4)))
    assertEquals(UndatedCells.total(List(cell(1, 3, "d")), List(cell(2, 1, "u1"), cell(3, 4, "u2"))), 8L)

  test("kept counts answer only for the writes and the grid they were built from"):
    val b = UndatedBuild(7, 16, 6)
    assert(b.answers(7, 16, 6))
    assert(b.answers(7, 16, 4))
    assert(!b.answers(8, 16, 6))
    assert(!b.answers(7, 8, 6))
    assert(!b.answers(7, 16, 8))
