package com.histmersion.web

/** #887: Escape closed no window anywhere. It closes the focused window, else the topmost one showing — and only a bare
  * Escape that no field, dropdown, lightbox or drawer took first. The pure parts of [[WindowManager.installKeys]]; the
  * key itself was checked in headless Chrome.
  */
class WindowEscapeSuite extends munit.FunSuite:
  import WindowManager.{escapeIsOurs, escapeTarget, Stacked}

  private val ws = List(
    Stacked(1, z = 101, minimized = false),
    Stacked(2, z = 105, minimized = false),
    Stacked(3, z = 110, minimized = true)
  )

  test("Escape closes the topmost window showing — a minimized one is not showing"):
    assertEquals(escapeTarget(ws, focused = None), Some(2))

  test("…unless the focus is inside another window: that one goes"):
    assertEquals(escapeTarget(ws, focused = Some(1)), Some(1))
    // focus in a minimized (or already closed) window falls back to the topmost
    assertEquals(escapeTarget(ws, focused = Some(3)), Some(2))
    assertEquals(escapeTarget(ws, focused = Some(99)), Some(2))

  test("no window showing: Escape closes nothing"):
    assertEquals(escapeTarget(Nil, None), None)
    assertEquals(escapeTarget(List(Stacked(4, 120, minimized = true)), None), None)

  test("only a bare Escape that nothing took first, outside a field and with no list open"):
    assert(escapeIsOurs("Escape", modified = false, taken = false, typing = false, dropdownOpen = false))
    assert(!escapeIsOurs("Enter", modified = false, taken = false, typing = false, dropdownOpen = false))
    assert(!escapeIsOurs("Escape", modified = true, taken = false, typing = false, dropdownOpen = false))
    // a dropdown, the lightbox or a drawer closed itself with this press
    assert(!escapeIsOurs("Escape", modified = false, taken = true, typing = false, dropdownOpen = false))
    // typing in an input, a textarea or an editable element
    assert(!escapeIsOurs("Escape", modified = false, taken = false, typing = true, dropdownOpen = false))
    // an autocomplete's list is open (Escape closes that first)
    assert(!escapeIsOurs("Escape", modified = false, taken = false, typing = false, dropdownOpen = true))
