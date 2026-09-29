package com.histmersion.web

/** The narrow (phone) layout's decisions, purely: which mode a width is, and how the bottom sheet
  * steps and snaps. What the modes look like is the stylesheet's. */
class ViewportSuite extends munit.FunSuite:

  test("at or below the configured width it is the narrow layout; above it, the wide one"):
    assertEquals(Viewport.mode(390, 760), Viewport.Mode.Narrow)
    assertEquals(Viewport.mode(760, 760), Viewport.Mode.Narrow)
    assertEquals(Viewport.mode(761, 760), Viewport.Mode.Wide)
    assertEquals(Viewport.mode(1440, 760), Viewport.Mode.Wide)

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
