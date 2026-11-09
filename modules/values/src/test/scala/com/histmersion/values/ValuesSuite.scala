package com.histmersion
package values

import com.histmersion.values.testsupport.ComparedByValue.given

import com.histmersion.{Latitude, LatLon, Longitude, Period, Year}
import com.histmersion.domain.library.Doi
import com.histmersion.domain.catalogue.places.Wikidata
class ValuesSuite extends munit.FunSuite:

  test("a period may not end before it starts; a point is one year"):
    assertEquals(
      Period.from(Year(-3300), Year(-3100)).map(p => (p.start.value: Int, p.end.value: Int)),
      Right((-3300, -3100))
    )
    assertEquals(Period.from(Year(-3100), Year(-3300)), Left(Period.Error.EndsBeforeStart(Year(-3100), Year(-3300))))
    assertEquals(
      (Period.Error.EndsBeforeStart(Year(-3100), Year(-3300)).message).legacyText,
      "the period ends (-3300) before it starts (-3100)"
    )
    assert(Period.point(Year(-1200)).isPoint && !Period.from(Year(1), Year(2)).toOption.get.isPoint)
    assert(compileErrors("Period(Year(1), Year(2))").nonEmpty, "only through from or point")

  test("a DOI is 10.<registrant>/<suffix>"):
    assert(Doi.either("10.3390/rel10040258").isRight)
    assert(
      Doi.either("https://doi.org/10.3390/x").isLeft && Doi.either("10.x/y").isLeft && Doi.either("10.1/a b").isLeft
    )

  test("a Wikidata id is Q and a number"):
    assert(Wikidata.Id.either("Q12554").isRight)
    assert(Wikidata.Id.either("Q0").isLeft && Wikidata.Id.either("P31").isLeft && Wikidata.Id.either("q1").isLeft)

  test("coordinates stay on the globe"):
    assert(Latitude.either(90.0).isRight && Latitude.either(-90.0).isRight && Latitude.either(90.5).isLeft)
    assert(Longitude.either(-180.0).isRight && Longitude.either(180.1).isLeft)
    assertEquals(LatLon(Latitude(41.9), Longitude(12.5)).lat.value: Double, 41.9)
