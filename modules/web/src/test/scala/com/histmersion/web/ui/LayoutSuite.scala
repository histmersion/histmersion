package com.histmersion.web.ui

class LayoutSuite extends munit.FunSuite:

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
