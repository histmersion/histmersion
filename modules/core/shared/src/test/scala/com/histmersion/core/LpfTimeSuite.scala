package com.histmersion.core

/** The LPF reader's years and timespans (#1403). Shared: it runs on the JVM AND under Scala.js, so a
  * regex the JS backend cannot compile (a look-behind) fails here rather than in the browser. */
class LpfTimeSuite extends munit.FunSuite:

  test("parseYear: the spellings LPF and hand-made GeoJSON actually contain"):
    assertEquals(Lpf.parseYear("-1400"), Some(-1400L))
    assertEquals(Lpf.parseYear("1400"), Some(1400L))
    assertEquals(Lpf.parseYear("1400 BCE"), Some(-1400L))
    assertEquals(Lpf.parseYear("1400 BC"), Some(-1400L))
    assertEquals(Lpf.parseYear("1400 b.c."), Some(-1400L))
    assertEquals(Lpf.parseYear("1400 B.C.E."), Some(-1400L))
    assertEquals(Lpf.parseYear("1400BC"), Some(-1400L), "an era word right after the digits")
    assertEquals(Lpf.parseYear("c. 1500 BC"), Some(-1500L))
    assertEquals(Lpf.parseYear("ca. 1500 BCE"), Some(-1500L))
    assertEquals(Lpf.parseYear("1200 CE"), Some(1200L))
    assertEquals(Lpf.parseYear("AD 800"), Some(800L))
    assertEquals(Lpf.parseYear("a.d. 800"), Some(800L))
    assertEquals(Lpf.parseYear("-0500-01-01"), Some(-501L), "ISO date: year -500 = 501 BCE = historical -501")
    assertEquals(Lpf.parseYear("0500-01-01"), Some(500L))
    assertEquals(Lpf.parseYear("-1400 BCE"), Some(-1400L), "an explicit minus wins over the era word")
    assertEquals(Lpf.parseYear(""), None)
    assertEquals(Lpf.parseYear("unknown"), None)

  test("parseYear: an era word inside another word is no era"):
    assertEquals(Lpf.parseYear("abc 300"), Some(300L), "'bc' preceded by a letter")
    assertEquals(Lpf.parseYear("bcd 300"), Some(300L), "'bc' followed by a letter")
    assertEquals(Lpf.parseYear("300 (bc)"), Some(-300L), "punctuation around it is fine")
    assertEquals(Lpf.parseYear("bc 300"), Some(-300L), "at the very start")
