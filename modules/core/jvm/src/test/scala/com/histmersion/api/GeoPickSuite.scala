package com.histmersion.api

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

/** How `GET /api/places/geo` reads its time parameters ([[GeoPick.resolve]]) — the map's "States
  * shown" menu over a range. */
class GeoPickSuite extends munit.FunSuite:
  import GeoTime.*

  test("every pick round-trips its wire name; an unknown one is none"):
    GeoPick.values.foreach(p => assertEquals(GeoPick.parse(p.wire), Some(p)))
    assertEquals(GeoPick.parse(" Latest "), Some(GeoPick.Latest))
    assertEquals(GeoPick.parse("middle"), None)
    assertEquals(GeoPick.values.map(_.wire).toList, List("mid", "start", "end", "earliest", "latest"))

  test("a snapshot pick reads ONE year of the range: the middle as the map always did, the start, the end"):
    assertEquals(GeoPick.resolve(None, Some(-2000), Some(-1000), GeoPick.Mid), At(-1500))
    assertEquals(GeoPick.resolve(None, Some(-2000), Some(-1000), GeoPick.Start), At(-2000))
    assertEquals(GeoPick.resolve(None, Some(-2000), Some(-1000), GeoPick.End), At(-1000))
    // the middle truncates like the web map's `(a + b) / 2`, so a snapshot shares its cached year
    assertEquals(GeoPick.resolve(None, Some(-3), Some(0), GeoPick.Mid), At((-3L + 0L) / 2))

  test("the range picks keep the whole range; reversed bounds are put in order; one year is a snapshot"):
    assertEquals(GeoPick.resolve(None, Some(1000), Some(1500), GeoPick.Earliest), Within(1000, 1500, latest = false))
    assertEquals(GeoPick.resolve(None, Some(1500), Some(1000), GeoPick.Latest), Within(1000, 1500, latest = true))
    assertEquals(GeoPick.resolve(None, Some(1200), Some(1200), GeoPick.Latest), At(1200))
    assertEquals(GeoPick.resolve(None, Some(1200), None, GeoPick.Earliest), At(1200), "one bound alone is that year")
    assertEquals(GeoPick.resolve(None, None, Some(-700), GeoPick.Mid), At(-700))

  test("without a range `asOf` is the year (the old query), and without it nothing is time-sliced"):
    assertEquals(GeoPick.resolve(Some(-1200), None, None, GeoPick.Earliest), At(-1200))
    assertEquals(GeoPick.resolve(None, None, None, GeoPick.Mid), Always)
    assertEquals(GeoPick.resolve(Some(-1200), Some(0), Some(100), GeoPick.Start), At(0), "a range wins over asOf")

  test("the window a time rule reads"):
    assertEquals(Always.window, None)
    assertEquals(At(5).window, Some((5L, 5L)))
    assertEquals(Within(-10, 10, latest = true).window, Some((-10L, 10L)))
