package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

/** #1441 — a place's card follows the pointer to be read, and is PINNED by a click (or Enter) so its links can be
  * clicked; Escape, its ✕ or the empty map close it. #1442 — the names' colours follow the base map's tone. The pure
  * parts.
  */
class PinnedCardSuite extends munit.FunSuite:

  test("#1441: Escape closes a pinned card; Enter and Space pin one; other keys do neither"):
    assert(PinRules.dismisses(com.histmersion.web.PinRules.KeyName("Escape")))
    assert(PinRules.dismisses(com.histmersion.web.PinRules.KeyName("Esc")))
    assert(!PinRules.dismisses(com.histmersion.web.PinRules.KeyName("Enter")))
    assert(PinRules.activates(com.histmersion.web.PinRules.KeyName("Enter")))
    assert(PinRules.activates(com.histmersion.web.PinRules.KeyName(" ")))
    assert(!PinRules.activates(com.histmersion.web.PinRules.KeyName("Tab")))
    assert(!PinRules.activates(com.histmersion.web.PinRules.KeyName("Escape")))

  test("#1441: the click that chooses a place pins its card; the click that unchooses it closes it"):
    assert(PinRules.pinAfterClick(chosenNow = com.histmersion.web.PinRules.ChosenNow(true)))
    assert(!PinRules.pinAfterClick(chosenNow = com.histmersion.web.PinRules.ChosenNow(false)))

  test("#1441: a pinned card follows its map point and waits, hidden, while it is panned out of view"):
    // a 800×600 map at (100, 50) in the viewport
    assertEquals(PinRules.inView(com.histmersion.web.PinRules.ScreenPixels(10), com.histmersion.web.PinRules.ScreenPixels(20), com.histmersion.web.PinRules.ScreenPixels(100), com.histmersion.web.PinRules.ScreenPixels(50), com.histmersion.web.PinRules.ElementSize(800), com.histmersion.web.PinRules.ElementSize(600)), Some((110.0, 70.0)))
    assertEquals(PinRules.inView(com.histmersion.web.PinRules.ScreenPixels(800), com.histmersion.web.PinRules.ScreenPixels(600), com.histmersion.web.PinRules.ScreenPixels(100), com.histmersion.web.PinRules.ScreenPixels(50), com.histmersion.web.PinRules.ElementSize(800), com.histmersion.web.PinRules.ElementSize(600)), Some((900.0, 650.0)))
    assertEquals(PinRules.inView(com.histmersion.web.PinRules.ScreenPixels(-1), com.histmersion.web.PinRules.ScreenPixels(20), com.histmersion.web.PinRules.ScreenPixels(100), com.histmersion.web.PinRules.ScreenPixels(50), com.histmersion.web.PinRules.ElementSize(800), com.histmersion.web.PinRules.ElementSize(600)), None)
    assertEquals(PinRules.inView(com.histmersion.web.PinRules.ScreenPixels(10), com.histmersion.web.PinRules.ScreenPixels(601), com.histmersion.web.PinRules.ScreenPixels(100), com.histmersion.web.PinRules.ScreenPixels(50), com.histmersion.web.PinRules.ElementSize(800), com.histmersion.web.PinRules.ElementSize(600)), None)

  test("#1441: a chip's pinned card sits centred under it, above it at the bottom, inside the gutters"):
    // a chip 60 px wide at x 100–160, y 40–60; a 300×120 card in a 1200×800 viewport
    assertEquals(Preview.underPlacement(com.histmersion.web.Preview.UnderPlacementLeft(100), com.histmersion.web.Preview.UnderPlacementTop(40), com.histmersion.web.Preview.UnderPlacementBottom(60), com.histmersion.web.Preview.UnderPlacementWidth(60), com.histmersion.web.Preview.UnderPlacementW(300), com.histmersion.web.Preview.UnderPlacementH(120), com.histmersion.web.Preview.UnderPlacementVw(1200), com.histmersion.web.Preview.UnderPlacementVh(800)), (10.0, 66.0))
    assertEquals(Preview.underPlacement(com.histmersion.web.Preview.UnderPlacementLeft(500), com.histmersion.web.Preview.UnderPlacementTop(40), com.histmersion.web.Preview.UnderPlacementBottom(60), com.histmersion.web.Preview.UnderPlacementWidth(60), com.histmersion.web.Preview.UnderPlacementW(300), com.histmersion.web.Preview.UnderPlacementH(120), com.histmersion.web.Preview.UnderPlacementVw(1200), com.histmersion.web.Preview.UnderPlacementVh(800)), (380.0, 66.0))
    // near the bottom: above the chip
    assertEquals(Preview.underPlacement(com.histmersion.web.Preview.UnderPlacementLeft(500), com.histmersion.web.Preview.UnderPlacementTop(700), com.histmersion.web.Preview.UnderPlacementBottom(720), com.histmersion.web.Preview.UnderPlacementWidth(60), com.histmersion.web.Preview.UnderPlacementW(300), com.histmersion.web.Preview.UnderPlacementH(120), com.histmersion.web.Preview.UnderPlacementVw(1200), com.histmersion.web.Preview.UnderPlacementVh(800)), (380.0, 574.0))
    // near the right edge: kept 10 px inside
    assertEquals(Preview.underPlacement(com.histmersion.web.Preview.UnderPlacementLeft(1150), com.histmersion.web.Preview.UnderPlacementTop(40), com.histmersion.web.Preview.UnderPlacementBottom(60), com.histmersion.web.Preview.UnderPlacementWidth(40), com.histmersion.web.Preview.UnderPlacementW(300), com.histmersion.web.Preview.UnderPlacementH(120), com.histmersion.web.Preview.UnderPlacementVw(1200), com.histmersion.web.Preview.UnderPlacementVh(800))._1, 890.0)

  test("#1441: a place chip previews its place at the map's year; a source or an article still wins"):
    assertEquals(
      Preview.targetOf(None, None, Some(com.histmersion.web.Pages.PlaceId("hatti")), Some(com.histmersion.api.Years.Astronomical(-1300L))),
      Some(Preview.Target.Place(com.histmersion.web.Preview.Target.Place.Id("hatti"), Some(com.histmersion.api.Years.Astronomical(-1300L))))
    )
    assertEquals(Preview.targetOf(Some(com.histmersion.api.library.Citekey("a2020_b")), None, Some(com.histmersion.web.Pages.PlaceId("hatti")), None), Some(Preview.Target.Source(com.histmersion.web.Preview.Target.Source.Citekey("a2020_b"))))
    assertEquals(Preview.targetOf(None, None, Some(com.histmersion.web.Pages.PlaceId("  ")), None), None)
    assertEquals(Preview.targetOf(None, None, None, None), None)

  test("#1442: the names follow the base map's tone; Dark darkens light tiles, never already-dark ones"):
    assertEquals(BaseMap.tone(BaseMap.Standard, MapTone.Light), MapTone.Light)
    assertEquals(BaseMap.tone(BaseMap.Standard, MapTone.Dark), MapTone.Dark)
    assertEquals(BaseMap.tone(BaseMap.Muted, MapTone.Dark), MapTone.Light)
    assertEquals(BaseMap.tone(BaseMap.Dark, MapTone.Light), MapTone.Dark)
    assert(BaseMap.darkened(BaseMap.Dark, MapTone.Light))
    assert(!BaseMap.darkened(BaseMap.Dark, MapTone.Dark))
    assert(!BaseMap.darkened(BaseMap.Standard, MapTone.Light))
    assertEquals(MapTone.parse(com.histmersion.web.MapTone.Wire(" DARK ")), MapTone.Dark)
    assertEquals(MapTone.parse(com.histmersion.web.MapTone.Wire("sepia")), MapTone.Light)
    assertEquals(BaseMap.fromWire(com.histmersion.web.BaseMap.Wire("dark")), Some(BaseMap.Dark))

  test("#1441: hovering the pinned place (its outline or its shape, at either year) shows no second card"):
    import Preview.Target.*
    assert(Preview.sameCard(Place(com.histmersion.web.Preview.Target.Place.Id("hatti"), Some(com.histmersion.api.Years.Astronomical(-1300L))), Place(com.histmersion.web.Preview.Target.Place.Id("hatti"), Some(com.histmersion.api.Years.Astronomical(-1301L)))))
    assert(!Preview.sameCard(Place(com.histmersion.web.Preview.Target.Place.Id("hatti"), None), Place(com.histmersion.web.Preview.Target.Place.Id("mitanni"), None)))
    assert(Preview.sameCard(Source(com.histmersion.web.Preview.Target.Source.Citekey("a2020_b")), Source(com.histmersion.web.Preview.Target.Source.Citekey("a2020_b"))))
    assert(!Preview.sameCard(Source(com.histmersion.web.Preview.Target.Source.Citekey("hatti")), Place(com.histmersion.web.Preview.Target.Place.Id("hatti"), None)))
