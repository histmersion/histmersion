package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.Years
import com.histmersion.api.library.Citekey

/** #1441 — a place's card follows the pointer to be read, and is PINNED by a click (or Enter) so its links can be
  * clicked; Escape, its ✕ or the empty map close it. #1442 — the names' colours follow the base map's tone. The pure
  * parts.
  */
class PinnedCardSuite extends munit.FunSuite:

  test("#1441: Escape closes a pinned card; Enter and Space pin one; other keys do neither"):
    assert(PinRules.dismisses(PinRules.KeyName("Escape")))
    assert(PinRules.dismisses(PinRules.KeyName("Esc")))
    assert(!PinRules.dismisses(PinRules.KeyName("Enter")))
    assert(PinRules.activates(PinRules.KeyName("Enter")))
    assert(PinRules.activates(PinRules.KeyName(" ")))
    assert(!PinRules.activates(PinRules.KeyName("Tab")))
    assert(!PinRules.activates(PinRules.KeyName("Escape")))

  test("#1441: the click that chooses a place pins its card; the click that unchooses it closes it"):
    assert(PinRules.pinAfterClick(chosenNow = PinRules.ChosenNow(true)))
    assert(!PinRules.pinAfterClick(chosenNow = PinRules.ChosenNow(false)))

  test("#1441: a pinned card follows its map point and waits, hidden, while it is panned out of view"):
    // a 800×600 map at (100, 50) in the viewport
    assertEquals(PinRules.inView(PinRules.ScreenPixels(10), PinRules.ScreenPixels(20), PinRules.ScreenPixels(100), PinRules.ScreenPixels(50), PinRules.ElementSize(800), PinRules.ElementSize(600)), Some((110.0, 70.0)))
    assertEquals(PinRules.inView(PinRules.ScreenPixels(800), PinRules.ScreenPixels(600), PinRules.ScreenPixels(100), PinRules.ScreenPixels(50), PinRules.ElementSize(800), PinRules.ElementSize(600)), Some((900.0, 650.0)))
    assertEquals(PinRules.inView(PinRules.ScreenPixels(-1), PinRules.ScreenPixels(20), PinRules.ScreenPixels(100), PinRules.ScreenPixels(50), PinRules.ElementSize(800), PinRules.ElementSize(600)), None)
    assertEquals(PinRules.inView(PinRules.ScreenPixels(10), PinRules.ScreenPixels(601), PinRules.ScreenPixels(100), PinRules.ScreenPixels(50), PinRules.ElementSize(800), PinRules.ElementSize(600)), None)

  test("#1441: a chip's pinned card sits centred under it, above it at the bottom, inside the gutters"):
    // a chip 60 px wide at x 100–160, y 40–60; a 300×120 card in a 1200×800 viewport
    assertEquals(Preview.underPlacement(Preview.UnderPlacementLeft(100), Preview.UnderPlacementTop(40), Preview.UnderPlacementBottom(60), Preview.UnderPlacementWidth(60), Preview.UnderPlacementW(300), Preview.UnderPlacementH(120), Preview.UnderPlacementVw(1200), Preview.UnderPlacementVh(800)), (10.0, 66.0))
    assertEquals(Preview.underPlacement(Preview.UnderPlacementLeft(500), Preview.UnderPlacementTop(40), Preview.UnderPlacementBottom(60), Preview.UnderPlacementWidth(60), Preview.UnderPlacementW(300), Preview.UnderPlacementH(120), Preview.UnderPlacementVw(1200), Preview.UnderPlacementVh(800)), (380.0, 66.0))
    // near the bottom: above the chip
    assertEquals(Preview.underPlacement(Preview.UnderPlacementLeft(500), Preview.UnderPlacementTop(700), Preview.UnderPlacementBottom(720), Preview.UnderPlacementWidth(60), Preview.UnderPlacementW(300), Preview.UnderPlacementH(120), Preview.UnderPlacementVw(1200), Preview.UnderPlacementVh(800)), (380.0, 574.0))
    // near the right edge: kept 10 px inside
    assertEquals(Preview.underPlacement(Preview.UnderPlacementLeft(1150), Preview.UnderPlacementTop(40), Preview.UnderPlacementBottom(60), Preview.UnderPlacementWidth(40), Preview.UnderPlacementW(300), Preview.UnderPlacementH(120), Preview.UnderPlacementVw(1200), Preview.UnderPlacementVh(800))._1, 890.0)

  test("#1441: a place chip previews its place at the map's year; a source or an article still wins"):
    assertEquals(
      Preview.targetOf(None, None, Some(Pages.PlaceId("hatti")), Some(Years.Astronomical(-1300L))),
      Some(Preview.Target.Place(Preview.Target.Place.Id("hatti"), Some(Years.Astronomical(-1300L))))
    )
    assertEquals(Preview.targetOf(Some(Citekey("a2020_b")), None, Some(Pages.PlaceId("hatti")), None), Some(Preview.Target.Source(Preview.Target.Source.Citekey("a2020_b"))))
    assertEquals(Preview.targetOf(None, None, Some(Pages.PlaceId("  ")), None), None)
    assertEquals(Preview.targetOf(None, None, None, None), None)

  test("#1442: the names follow the base map's tone; Dark darkens light tiles, never already-dark ones"):
    assertEquals(BaseMap.tone(BaseMap.Standard, MapTone.Light), MapTone.Light)
    assertEquals(BaseMap.tone(BaseMap.Standard, MapTone.Dark), MapTone.Dark)
    assertEquals(BaseMap.tone(BaseMap.Muted, MapTone.Dark), MapTone.Light)
    assertEquals(BaseMap.tone(BaseMap.Dark, MapTone.Light), MapTone.Dark)
    assert(BaseMap.darkened(BaseMap.Dark, MapTone.Light))
    assert(!BaseMap.darkened(BaseMap.Dark, MapTone.Dark))
    assert(!BaseMap.darkened(BaseMap.Standard, MapTone.Light))
    assertEquals(MapTone.parse(MapTone.Wire(" DARK ")), MapTone.Dark)
    assertEquals(MapTone.parse(MapTone.Wire("sepia")), MapTone.Light)
    assertEquals(BaseMap.fromWire(BaseMap.Wire("dark")), Some(BaseMap.Dark))

  test("#1441: hovering the pinned place (its outline or its shape, at either year) shows no second card"):
    import Preview.Target.*
    assert(Preview.sameCard(Place(Place.Id("hatti"), Some(Years.Astronomical(-1300L))), Place(Place.Id("hatti"), Some(Years.Astronomical(-1301L)))))
    assert(!Preview.sameCard(Place(Place.Id("hatti"), None), Place(Place.Id("mitanni"), None)))
    assert(Preview.sameCard(Source(Source.Citekey("a2020_b")), Source(Source.Citekey("a2020_b"))))
    assert(!Preview.sameCard(Source(Source.Citekey("hatti")), Place(Place.Id("hatti"), None)))
