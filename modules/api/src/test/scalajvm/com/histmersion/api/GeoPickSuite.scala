package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

/** How `GET /api/places/geo` reads its time parameters ([[GeoPick.resolve]]) — the map's "States shown" menu over a
  * range.
  */

import com.histmersion.api.catalogue.GeoTime
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class GeoPickSuite extends munit.FunSuite:
  import com.histmersion.api.catalogue.GeoTime.*

  test("every pick round-trips its wire name; an unknown one is none"):
    GeoPick.values.foreach(p => assertEquals(GeoPick.parse(p.wire), Some(p)))
    assertEquals(GeoPick.parse(GeoPick.Wire(" Latest ")), Some(GeoPick.Latest))
    assertEquals(GeoPick.parse(GeoPick.Wire("middle")), None)
    assertEquals(GeoPick.values.map(_.wire).toList, List("mid", "start", "end", "earliest", "latest"))

  test("a snapshot pick reads ONE year of the range: the middle as the map always did, the start, the end"):
    assertEquals(GeoPick.resolve(None, Some(Year(-2000)), Some(Year(-1000)), GeoPick.Mid), At(Year(-1500)))
    assertEquals(GeoPick.resolve(None, Some(Year(-2000)), Some(Year(-1000)), GeoPick.Start), At(Year(-2000)))
    assertEquals(GeoPick.resolve(None, Some(Year(-2000)), Some(Year(-1000)), GeoPick.End), At(Year(-1000)))
    // the middle truncates like the web map's `(a + b) / 2`, so a snapshot shares its cached year
    assertEquals(GeoPick.resolve(None, Some(Year(-3)), Some(Year(0)), GeoPick.Mid), At(Year((-3L + 0L) / 2)))

  test("the range picks keep the whole range; reversed bounds are put in order; one year is a snapshot"):
    assertEquals(
      GeoPick.resolve(None, Some(Year(1000)), Some(Year(1500)), GeoPick.Earliest),
      Within(Year(1000), Year(1500), latest = Within.Latest(false))
    )
    assertEquals(
      GeoPick.resolve(None, Some(Year(1500)), Some(Year(1000)), GeoPick.Latest),
      Within(Year(1000), Year(1500), latest = Within.Latest(true))
    )
    assertEquals(GeoPick.resolve(None, Some(Year(1200)), Some(Year(1200)), GeoPick.Latest), At(Year(1200)))
    assertEquals(
      GeoPick.resolve(None, Some(Year(1200)), None, GeoPick.Earliest),
      At(Year(1200)),
      "one bound alone is that year"
    )
    assertEquals(GeoPick.resolve(None, None, Some(Year(-700)), GeoPick.Mid), At(Year(-700)))

  test("without a range `asOf` is the year (the old query), and without it nothing is time-sliced"):
    assertEquals(GeoPick.resolve(Some(Year(-1200)), None, None, GeoPick.Earliest), At(Year(-1200)))
    assertEquals(GeoPick.resolve(None, None, None, GeoPick.Mid), Always)
    assertEquals(
      GeoPick.resolve(Some(Year(-1200)), Some(Year(0)), Some(Year(100)), GeoPick.Start),
      At(Year(0)),
      "a range wins over asOf"
    )

  test("the window a time rule reads"):
    assertEquals(Always.window, None)
    assertEquals(At(Year(5)).window, Some((5L, 5L)))
    assertEquals(Within(Year(-10), Year(10), latest = Within.Latest(true)).window, Some((-10L, 10L)))
