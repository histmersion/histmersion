package com.histmersion.web.ui

class CssSuite extends munit.FunSuite:

  test("Px.css produces valid pixel string"):
    assertEquals(Px(10).css, "10px")
    assertEquals(Px(0).css, "0px")
    assertEquals(Px(360).css, "360px")

  test("Px.value round-trips"):
    assertEquals(Px(42).value, 42)

  test("CssColor preserves value"):
    assertEquals(CssColor("#ff0000").value, "#ff0000")
    assertEquals(CssColor("rgb(1,2,3)").value, "rgb(1,2,3)")

  test("CssClass preserves value"):
    assertEquals(CssClass("foo-bar").value, "foo-bar")
    assertEquals(CssClass("hd-dot hd-cluster").value, "hd-dot hd-cluster")
