package com.histmersion.web

class PointLayerSuite extends munit.FunSuite:

  final private case class Item(id: String, year: Long)

  private def dot(a: Item, span: (Long, Long)): Dot =
    Dot(
      "k",
      PointLayer(PointKinds.Findings, () => null, () => None),
      a,
      span,
      Member(DotKind.Finding, 1, Nil, Certainty.NoSay)
    )

  test("same: the item and the span it was drawn for, again — nothing to redraw") {
    assert(PointLayer.same(dot(Item("a", 1), (-100L, 100L)), Item("a", 1), (-100L, 100L)))
  }

  test("same: a changed item or a moved span is redrawn") {
    assert(!PointLayer.same(dot(Item("a", 1), (-100L, 100L)), Item("a", 2), (-100L, 100L)))
    assert(!PointLayer.same(dot(Item("a", 1), (-100L, 100L)), Item("a", 1), (-100L, 101L)))
  }
