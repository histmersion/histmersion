package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

class ThemeSuite extends munit.FunSuite:

  test("the button cycles system → light → dark → system"):
    val seen = Iterator.iterate(ThemeChoice.System)(ThemeChoice.next).take(4).toList
    assertEquals(seen, List(ThemeChoice.System, ThemeChoice.Light, ThemeChoice.Dark, ThemeChoice.System))

  test("a stored value is read back; nothing stored, or anything unknown, follows the system"):
    assertEquals(ThemeChoice.fromWire("dark"), ThemeChoice.Dark)
    assertEquals(ThemeChoice.fromWire(" Light "), ThemeChoice.Light)
    assertEquals(ThemeChoice.fromWire(null), ThemeChoice.System)
    assertEquals(ThemeChoice.fromWire("sepia"), ThemeChoice.System)
    ThemeChoice.values.foreach(c => assertEquals(ThemeChoice.fromWire(c.wire), c))

  test("the button says what it shows and what a press picks"):
    assertEquals(ThemeChoice.tooltip(ThemeChoice.System), "Theme: follows the system. Click for light.")
    assertEquals(ThemeChoice.tooltip(ThemeChoice.Light), "Theme: light. Click for dark.")
    assertEquals(ThemeChoice.tooltip(ThemeChoice.Dark), "Theme: dark. Click for the system's setting.")

  test("the stored key is the one the pages' <head> script reads"):
    assertEquals(Theme.StorageKey, "histmersion.theme")
