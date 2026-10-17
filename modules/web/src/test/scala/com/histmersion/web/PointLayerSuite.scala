package com.histmersion.web

import scala.scalajs.js

class PointLayerSuite extends munit.FunSuite:

  private final case class Item(id: String, year: Long)

  private def marker(a: Item, span: (Long, Long)): js.Dynamic =
    js.Dynamic.literal(hdItem = a.asInstanceOf[js.Any], hdSpan = span.asInstanceOf[js.Any])

  test("same: the item and the span it was drawn for, again — nothing to redraw") {
    assert(PointLayer.same(marker(Item("a", 1), (-100L, 100L)), Item("a", 1), (-100L, 100L)))
  }

  test("same: a changed item or a moved span is redrawn") {
    assert(!PointLayer.same(marker(Item("a", 1), (-100L, 100L)), Item("a", 2), (-100L, 100L)))
    assert(!PointLayer.same(marker(Item("a", 1), (-100L, 100L)), Item("a", 1), (-100L, 101L)))
  }

  test("same: a marker never drawn by a layer is not the same") {
    assert(!PointLayer.same(js.Dynamic.literal(), Item("a", 1), (0L, 1L)))
  }
