package com.histmersion
package domain.catalogue.maptiles

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.lint.{LintRule, tolerated}

class UndatedCellsSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def cell(
      gx: GridCell.Gx,
      n: GridCell.N,
      one: GridCell.One,
      dup: GridCell.Dup = GridCell.Dup(0),
      x: Double = 100,
      mid: Option[Double] = None
  ) =
    GridCell(
      gx,
      GridCell.Gy(7),
      n,
      dup,
      GridCell.SumX(x * n.value),
      GridCell.SumY(50.0 * n.value),
      GridCell.DupX(x * dup.value),
      GridCell.DupY(50.0 * dup.value),
      mid.map(GridCell.MidLo(_)),
      mid.map(GridCell.MidHi(_)),
      GridCell.West(1),
      GridCell.South(2),
      GridCell.East(3),
      GridCell.North(4),
      one
    )

  test("a square of dated and undated sites counts both, and how many have no known date"):
    val out = UndatedCells.merge(
      UndatedCells.MergeZ(3),
      List(cell(GridCell.Gx(5), GridCell.N(2), GridCell.One("a"), mid = Some(-500))),
      List(cell(GridCell.Gx(5), GridCell.N(3), GridCell.One("u")))
    )
    assertEquals(out.map(c => (c.key, c.n, c.undated, c.midLo)), List(("c3/5/7", 5, 3, Some(-500.0))))

  test("an undated site the span shows by its findings counts once, as dated"):
    val out = UndatedCells.merge(
      UndatedCells.MergeZ(3),
      List(cell(GridCell.Gx(5), GridCell.N(1), GridCell.One("f"), dup = GridCell.Dup(1))),
      List(cell(GridCell.Gx(5), GridCell.N(1), GridCell.One("f")))
    )
    assertEquals(out.map(c => (c.key, c.n, c.undated, c.x)), List(("f", 1, 0, 100)))
    assertEquals(
      UndatedCells.total(
        List(cell(GridCell.Gx(5), GridCell.N(1), GridCell.One("f"), dup = GridCell.Dup(1))),
        List(cell(GridCell.Gx(5), GridCell.N(1), GridCell.One("f")))
      ),
      1L
    )

  test("squares of one kind only: a single undated site is itself, marked; several are a cell"):
    val out = UndatedCells.merge(
      UndatedCells.MergeZ(2),
      List(cell(GridCell.Gx(1), GridCell.N(3), GridCell.One("d"))),
      List(
        cell(GridCell.Gx(2), GridCell.N(1), GridCell.One("u1")),
        cell(GridCell.Gx(3), GridCell.N(4), GridCell.One("u2"))
      )
    )
    assertEquals(out.map(c => (c.key, c.n, c.undated)), List(("c2/1/7", 3, 0), ("u1", 1, 1), ("c2/3/7", 4, 4)))
    assertEquals(
      UndatedCells.total(
        List(cell(GridCell.Gx(1), GridCell.N(3), GridCell.One("d"))),
        List(
          cell(GridCell.Gx(2), GridCell.N(1), GridCell.One("u1")),
          cell(GridCell.Gx(3), GridCell.N(4), GridCell.One("u2"))
        )
      ),
      8L
    )

  test("kept counts answer only for the writes and the grid they were built from"):
    val b = UndatedBuild(UndatedBuild.Version(7), UndatedBuild.PerSide(16), UndatedBuild.MaxZoom(6))
    assert(b.answers(UndatedBuild.Version(7), UndatedBuild.PerSide(16), UndatedBuild.MaxZoom(6)))
    assert(b.answers(UndatedBuild.Version(7), UndatedBuild.PerSide(16), UndatedBuild.MaxZoom(4)))
    assert(!b.answers(UndatedBuild.Version(8), UndatedBuild.PerSide(16), UndatedBuild.MaxZoom(6)))
    assert(!b.answers(UndatedBuild.Version(7), UndatedBuild.PerSide(8), UndatedBuild.MaxZoom(6)))
    assert(!b.answers(UndatedBuild.Version(7), UndatedBuild.PerSide(16), UndatedBuild.MaxZoom(8)))
