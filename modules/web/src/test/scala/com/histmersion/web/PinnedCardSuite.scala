package com.histmersion.web

/** #1441 — a place's card follows the pointer to be read, and is PINNED by a click (or Enter) so its
  * links can be clicked; Escape, its ✕ or the empty map close it. #1442 — the names' colours follow the
  * base map's tone. The pure parts. */
class PinnedCardSuite extends munit.FunSuite:

  test("#1441: Escape closes a pinned card; Enter and Space pin one; other keys do neither"):
    assert(PinRules.dismisses("Escape"))
    assert(PinRules.dismisses("Esc"))
    assert(!PinRules.dismisses("Enter"))
    assert(PinRules.activates("Enter"))
    assert(PinRules.activates(" "))
    assert(!PinRules.activates("Tab"))
    assert(!PinRules.activates("Escape"))

  test("#1441: the click that chooses a place pins its card; the click that unchooses it closes it"):
    assert(PinRules.pinAfterClick(chosenNow = true))
    assert(!PinRules.pinAfterClick(chosenNow = false))

  test("#1441: a pinned card follows its map point and waits, hidden, while it is panned out of view"):
    // a 800×600 map at (100, 50) in the viewport
    assertEquals(PinRules.inView(10, 20, 100, 50, 800, 600), Some((110.0, 70.0)))
    assertEquals(PinRules.inView(800, 600, 100, 50, 800, 600), Some((900.0, 650.0)))
    assertEquals(PinRules.inView(-1, 20, 100, 50, 800, 600), None)
    assertEquals(PinRules.inView(10, 601, 100, 50, 800, 600), None)

  test("#1441: a chip's pinned card sits centred under it, above it at the bottom, inside the gutters"):
    // a chip 60 px wide at x 100–160, y 40–60; a 300×120 card in a 1200×800 viewport
    assertEquals(Preview.underPlacement(100, 40, 60, 60, 300, 120, 1200, 800), (10.0, 66.0))
    assertEquals(Preview.underPlacement(500, 40, 60, 60, 300, 120, 1200, 800), (380.0, 66.0))
    // near the bottom: above the chip
    assertEquals(Preview.underPlacement(500, 700, 720, 60, 300, 120, 1200, 800), (380.0, 574.0))
    // near the right edge: kept 10 px inside
    assertEquals(Preview.underPlacement(1150, 40, 60, 40, 300, 120, 1200, 800)._1, 890.0)

  test("#1441: a place chip previews its place at the map's year; a source or an article still wins"):
    assertEquals(Preview.targetOf(None, None, Some("hatti"), Some(-1300L)),
      Some(Preview.Target.Place("hatti", Some(-1300L))))
    assertEquals(Preview.targetOf(Some("a2020_b"), None, Some("hatti"), None), Some(Preview.Target.Source("a2020_b")))
    assertEquals(Preview.targetOf(None, None, Some("  "), None), None)
    assertEquals(Preview.targetOf(None, None, None, None), None)

  test("#1442: the names follow the base map's tone; Dark darkens light tiles, never already-dark ones"):
    assertEquals(BaseMap.tone(BaseMap.Standard, MapTone.Light), MapTone.Light)
    assertEquals(BaseMap.tone(BaseMap.Standard, MapTone.Dark), MapTone.Dark)
    assertEquals(BaseMap.tone(BaseMap.Muted, MapTone.Dark), MapTone.Light)
    assertEquals(BaseMap.tone(BaseMap.Dark, MapTone.Light), MapTone.Dark)
    assert(BaseMap.darkened(BaseMap.Dark, MapTone.Light))
    assert(!BaseMap.darkened(BaseMap.Dark, MapTone.Dark))
    assert(!BaseMap.darkened(BaseMap.Standard, MapTone.Light))
    assertEquals(MapTone.parse(" DARK "), MapTone.Dark)
    assertEquals(MapTone.parse("sepia"), MapTone.Light)
    assertEquals(BaseMap.fromWire("dark"), Some(BaseMap.Dark))

  test("#1441: hovering the pinned place (its outline or its shape, at either year) shows no second card"):
    import Preview.Target.*
    assert(Preview.sameCard(Place("hatti", Some(-1300L)), Place("hatti", Some(-1301L))))
    assert(!Preview.sameCard(Place("hatti", None), Place("mitanni", None)))
    assert(Preview.sameCard(Source("a2020_b"), Source("a2020_b")))
    assert(!Preview.sameCard(Source("hatti"), Place("hatti", None)))
