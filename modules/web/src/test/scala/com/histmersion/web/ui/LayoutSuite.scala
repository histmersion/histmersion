package com.histmersion
package web.ui

import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
    assert(Layout.maxBuckets.value > 0)

  test("Layout.minSpan is positive"):
    assert(Layout.minSpan.value > 0)

  test("Layout.handleWidth is positive"):
    assert(Layout.handleWidth.value > 0)

  test("Layout.markerRadiusDefault is positive"):
    assert(Layout.markerRadiusDefault.value > 0)
