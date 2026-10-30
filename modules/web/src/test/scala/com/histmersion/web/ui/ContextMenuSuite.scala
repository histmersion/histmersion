package com.histmersion.web.ui

class ContextMenuSuite extends munit.FunSuite:

  test("the menu key: the keyboard's own, or Shift+F10"):
    assert(ContextMenu.isMenuKey("ContextMenu", shift = false))
    assert(ContextMenu.isMenuKey("F10", shift = true))
    assert(!ContextMenu.isMenuKey("F10", shift = false))

  test("a menu stays inside the window, flipped to the other side of the point where it would not fit"):
    assertEquals(ContextMenu.place(100, 100, 200, 300, 1440, 900), (100.0, 100.0))
    assertEquals(ContextMenu.place(1400, 100, 200, 300, 1440, 900), (1200.0, 100.0))
    assertEquals(ContextMenu.place(100, 850, 200, 300, 1440, 900), (100.0, 550.0))
    assertEquals(ContextMenu.place(5, 5, 200, 300, 150, 200), (8.0, 8.0))
