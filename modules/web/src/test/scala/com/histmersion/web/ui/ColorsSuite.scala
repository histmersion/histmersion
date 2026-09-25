package com.histmersion.web.ui

class ColorsSuite extends munit.FunSuite:

  private val hexRe = "^#[0-9a-fA-F]{6}$".r

  test("EdgeColors: all values are valid hex"):
    EdgeColors.palette.foreach { (kind, color) =>
      assert(hexRe.findFirstIn(color.value).isDefined, s"bad color for edge kind '$kind': ${color.value}")
    }

  test("EdgeColors: default is valid hex"):
    assert(hexRe.findFirstIn(EdgeColors.default.value).isDefined)

  test("EdgeColors: apply returns default for unknown kind"):
    assertEquals(EdgeColors("nonexistent"), EdgeColors.default.value)

  test("EdgeColors: apply returns palette value for known kind"):
    assertEquals(EdgeColors("references"), "#6ea8fe")

  test("EdgeColors: has all expected edge kinds"):
    val expected = Set("references", "challenges", "refutes", "updates", "superseded_by",
      "supersedes", "confirms", "cites", "foundational_for", "compilation_of", "review_of", "verifies")
    assertEquals(EdgeColors.palette.keySet, expected)

  test("StatusColors: all values are valid hex"):
    StatusColors.palette.foreach { (status, color) =>
      assert(hexRe.findFirstIn(color.value).isDefined, s"bad color for status '$status': ${color.value}")
    }

  test("StatusColors: has all expected statuses"):
    val expected = Set("foundational", "current", "contested", "superseded", "debunked", "retracted", "unassessed")
    assertEquals(StatusColors.palette.keySet, expected)

  test("StatusColors: apply returns default for unknown status"):
    assertEquals(StatusColors("nonexistent"), StatusColors.default.value)

  test("MediaKindColors: all values are valid hex"):
    MediaKindColors.palette.foreach { (kind, color) =>
      assert(hexRe.findFirstIn(color.value).isDefined, s"bad color for media kind '$kind': ${color.value}")
    }

  test("MediaKindColors: has expected kinds"):
    val expected = Set("figure", "photo", "map", "plan", "scan")
    assertEquals(MediaKindColors.palette.keySet, expected)

  test("OverlayColors: boundary is valid hex"):
    assert(hexRe.findFirstIn(OverlayColors.boundary.value).isDefined)

  test("OverlayColors: opacities in 0..1"):
    assert(OverlayColors.boundaryFillOpacity >= 0 && OverlayColors.boundaryFillOpacity <= 1)
    assert(OverlayColors.boundaryStrokeOpacity >= 0 && OverlayColors.boundaryStrokeOpacity <= 1)

  test("ViridisStops: has 5 stops"):
    assertEquals(ViridisStops.stops.length, 5)

  test("ViridisStops: all components in 0..255"):
    ViridisStops.stops.foreach { (r, g, b) =>
      assert(r >= 0 && r <= 255, s"r=$r out of range")
      assert(g >= 0 && g <= 255, s"g=$g out of range")
      assert(b >= 0 && b <= 255, s"b=$b out of range")
    }

  test("ConfidenceSaturation: levels sum to expected keys"):
    assertEquals(ConfidenceSaturation.levels.keySet, Set("high", "medium", "low", "contested"))

  test("ConfidenceSaturation: high is 1.0 (no desaturation)"):
    assertEquals(ConfidenceSaturation.levels("high"), 1.0)

  test("ConfidenceSaturation: order ranks contested lowest"):
    assertEquals(ConfidenceSaturation.order("contested"), 0)
    assert(ConfidenceSaturation.order("high") > ConfidenceSaturation.order("contested"))

  test("GraphLayout: radii are positive and ordered"):
    assert(GraphLayout.leafRadius > 0)
    assert(GraphLayout.depth1Radius > GraphLayout.leafRadius)
    assert(GraphLayout.rootRadius > GraphLayout.depth1Radius)

  test("GraphLayout: label truncation is shorter than max"):
    assert(GraphLayout.labelTruncLen < GraphLayout.labelMaxLen)

  test("Layout: window constraints are consistent"):
    assert(Layout.windowMinWidth.value > 0)
    assert(Layout.windowMinHeight.value > 0)
    assert(Layout.windowMaxWidth > Layout.windowMinWidth.value)
    assert(Layout.windowMaxHeight > Layout.windowMinHeight.value)

  test("Layout: playback constants are positive"):
    assert(Layout.playbackWindowHalf > 0)
    assert(Layout.playbackDuration > 0)
    assert(Layout.exitAnimMs > 0)
