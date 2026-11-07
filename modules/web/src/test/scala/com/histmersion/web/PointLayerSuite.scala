package com.histmersion.web

class PointLayerSuite extends munit.FunSuite:

  final private case class Item(id: String, year: Long)

  private def dot(a: Item, span: (Dot.SpanPart1, Dot.SpanPart2)): Dot =
    new Dot(
      Dot.Key("k"),
      new PointLayer(PointKinds.Findings, () => null, () => None),
      a,
      span,
      Member(DotKind.Finding, Member.N(1), Nil, Certainty.NoSay)
    )

  test("same: the item and the span it was drawn for, again — nothing to redraw") {
    assert(PointLayer.same(dot(Item("a", 1), (Dot.SpanPart1(-100L), Dot.SpanPart2(100L))), Item("a", 1), (com.histmersion.api.Years.Astronomical(-100L), com.histmersion.api.Years.Astronomical(100L))))
  }

  test("same: a changed item or a moved span is redrawn") {
    assert(!PointLayer.same(dot(Item("a", 1), (Dot.SpanPart1(-100L), Dot.SpanPart2(100L))), Item("a", 2), (com.histmersion.api.Years.Astronomical(-100L), com.histmersion.api.Years.Astronomical(100L))))
    assert(!PointLayer.same(dot(Item("a", 1), (Dot.SpanPart1(-100L), Dot.SpanPart2(100L))), Item("a", 1), (com.histmersion.api.Years.Astronomical(-100L), com.histmersion.api.Years.Astronomical(101L))))
  }
