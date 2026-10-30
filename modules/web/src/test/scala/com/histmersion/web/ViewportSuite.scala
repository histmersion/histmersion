package com.histmersion.web

/** The narrow (phone) layout's decisions, purely: which mode a width is, and how the bottom sheet steps and snaps. What
  * the modes look like is the stylesheet's.
  */
class ViewportSuite extends munit.FunSuite:

  test("a thin screen is narrow; so is a short TOUCH screen (a phone held sideways); a desktop is not"):
    def m(w: Double, h: Double, touch: Boolean) = Viewport.mode(w, h, touch, 760, 500, 1366)
    assertEquals(m(390, 844, touch = true), Viewport.Mode.Narrow)
    assertEquals(m(760, 900, touch = false), Viewport.Mode.Narrow)
    assertEquals(m(844, 390, touch = true), Viewport.Mode.Narrow, "sideways phone")
    assertEquals(m(1440, 900, touch = true), Viewport.Mode.Wide, "a big touch screen is wide")
    assertEquals(m(1920, 1080, touch = false), Viewport.Mode.Wide)
    assertEquals(Viewport.query(760, 500), "(max-width: 760px), (max-height: 500px) and (pointer: coarse)")

  test("between the phone and the desktop, up to its own width, a screen is a tablet, held either way"):
    def m(w: Double, h: Double, touch: Boolean, tabletMax: Int = 1366) = Viewport.mode(w, h, touch, 760, 500, tabletMax)
    assertEquals(m(761, 900, touch = false), Viewport.Mode.Tablet)
    for (w, h) <- List((768, 1024), (820, 1180), (1024, 768), (1024, 1366), (1180, 820), (1280, 800), (1366, 1024)) do
      assertEquals(m(w, h, touch = true), Viewport.Mode.Tablet, s"${w}x$h")
    assertEquals(m(1367, 1024, touch = true), Viewport.Mode.Wide)
    assertEquals(m(1280, 480, touch = false), Viewport.Mode.Tablet, "a short mouse window is not a sideways phone")
    assertEquals(m(1024, 768, touch = false, tabletMax = 0), Viewport.Mode.Wide, "0: no tablet layout")
    assertEquals(m(700, 900, touch = false, tabletMax = 0), Viewport.Mode.Narrow)
    assertEquals(Viewport.Mode.Tablet.wire, "narrow", "the phone's rules apply, the tablet's own on top")
    assertEquals(Viewport.tabletQuery(1366), "(max-width: 1366px)")

  test("the timeline starts unfolded on a tablet, folded on a phone"):
    assert(MobileChrome.timelineOpenIn(Viewport.Mode.Tablet))
    assert(!MobileChrome.timelineOpenIn(Viewport.Mode.Narrow))
    assert(!MobileChrome.timelineOpenIn(Viewport.Mode.Wide))

  test("a tap on the handle moves the sheet one step up, and from the top back to the strip"):
    assertEquals(Sheet.next(Sheet.Peek), Sheet.Half)
    assertEquals(Sheet.next(Sheet.Half), Sheet.Full)
    assertEquals(Sheet.next(Sheet.Full), Sheet.Peek)

  test("a drag lets go at the nearest of the three heights"):
    // on an 844px phone: the strip is 58px (~7%), the full sheet leaves 52px (~94%)
    val (peek, full) = (58.0 / 844, 1 - 52.0 / 844)
    assertEquals(Sheet.nearest(0.05, peek, full), Sheet.Peek)
    assertEquals(Sheet.nearest(0.25, peek, full), Sheet.Peek)
    assertEquals(Sheet.nearest(0.35, peek, full), Sheet.Half)
    assertEquals(Sheet.nearest(0.70, peek, full), Sheet.Half)
    assertEquals(Sheet.nearest(0.80, peek, full), Sheet.Full)
    assertEquals(Sheet.nearest(1.20, peek, full), Sheet.Full)

  test("the sheet is a per-device preference: remembered locally, never in a shared link"):
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.set(UiState.sheet := Sheet.Full)()
    assertEquals(UiState.store.get("histmersion.ui.sheet"), Some("""["full"]"""))
    assert(!UiState.hashString.contains("sheet"), UiState.hashString)
    UiState.sheet.clear()
    UiState.restoreLocal()
    assertEquals(UiState.sheet.now(), Sheet.Full)
    UiState.store.put("histmersion.ui.sheet", """["sideways"]""")
    UiState.restoreLocal()
    assertEquals(UiState.sheet.now(), Sheet.Peek, "garbage falls back to the strip")
    UiState.set(UiState.sheet := Sheet.Peek)()

  // ── the drawers ───────────────────────────────────────────────────────────────────────────────

  import MobileChrome.{swipesIn, swipesOut, Edge}

  test("a swipe in from an edge opens that edge's drawer; a short or vertical gesture does not"):
    assert(swipesIn(Edge.Left, 80, 10))
    assert(!swipesIn(Edge.Left, -80, 10), "wrong way")
    assert(!swipesIn(Edge.Left, 20, 0), "too short")
    assert(!swipesIn(Edge.Left, 60, 70), "more down than across: a scroll")
    assert(swipesIn(Edge.Right, -80, 10))
    assert(!swipesIn(Edge.Right, 80, 10))

  test("a swipe back towards its edge closes a drawer"):
    assert(swipesOut(Edge.Left, -60, 5))
    assert(!swipesOut(Edge.Left, 60, 5))
    assert(swipesOut(Edge.Right, 60, 5))
    assert(!swipesOut(Edge.Right, -60, 5))

  test("the drawers are one at a time: opening one closes the other; toggling twice closes"):
    MobileChrome.open(MobileChrome.Drawer.Filters)
    MobileChrome.open(MobileChrome.Drawer.MapTools)
    assertEquals(MobileChrome.drawer.now(), Some(MobileChrome.Drawer.MapTools))
    MobileChrome.toggle(MobileChrome.Drawer.MapTools)
    assertEquals(MobileChrome.drawer.now(), None)
    MobileChrome.toggle(MobileChrome.Drawer.Filters)
    assertEquals(MobileChrome.drawer.now(), Some(MobileChrome.Drawer.Filters))
    MobileChrome.close()

  test("the ☰ badge counts the filters that are set but hidden in the drawer, not the search"):
    val chips = List(
      UiState.ActiveChip("q", "search", "troy"),
      UiState.ActiveChip("tags", "tag", "argolid"),
      UiState.ActiveChip("status", "status", "current")
    )
    assertEquals(MobileChrome.hiddenFilterCount(chips), 2)
    assertEquals(MobileChrome.hiddenFilterCount(Nil), 0)
