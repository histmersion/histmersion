package com.histmersion.web.ui

class ThemeSuite extends munit.FunSuite:

  private val hexPattern = "^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$".r

  private val allColors: List[(String, CssColor)] = List(
    "bg"      -> Theme.bg,
    "panel"   -> Theme.panel,
    "ink"     -> Theme.ink,
    "muted"   -> Theme.muted,
    "accent"  -> Theme.accent,
    "line"    -> Theme.line,
    "inputBg" -> Theme.inputBg
  )

  for (name, color) <- allColors do
    test(s"Theme.$name is valid hex color"):
      assert(hexPattern.findFirstIn(color.value).isDefined, s"${color.value} is not a valid hex color")

  test("Layout.sidebarMinWidth is positive"):
    assert(Layout.sidebarMinWidth.value > 0)

  test("Layout.sidebarMaxWidth > sidebarMinWidth"):
    assert(Layout.sidebarMaxWidth.value > Layout.sidebarMinWidth.value)

  test("Layout.sidebarDefault is within min/max"):
    assert(Layout.sidebarDefault.value >= Layout.sidebarMinWidth.value)
    assert(Layout.sidebarDefault.value <= Layout.sidebarMaxWidth.value)

  test("Layout.overviewBase < overviewHeight"):
    assert(Layout.overviewBase.value < Layout.overviewHeight.value)

  test("Layout.detailBase < detailHeight"):
    assert(Layout.detailBase.value < Layout.detailHeight.value)

  test("Layout.maxBuckets is positive"):
    assert(Layout.maxBuckets > 0)

  test("Layout.minSpan is positive"):
    assert(Layout.minSpan > 0)

  test("Layout.handleWidth is positive"):
    assert(Layout.handleWidth > 0)

  test("Layout.markerRadiusDefault is positive"):
    assert(Layout.markerRadiusDefault > 0)
