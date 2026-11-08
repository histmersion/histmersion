package com.histmersion
package plugin

/** The SPI's own newtypes: what each accepts, at compile time for a literal and at run time for a value read from a
  * provider; and the SPI generation compiled into the plugin.
  */
class ValuesSuite extends munit.FunSuite:

  test("a year is any integer, BCE negative"):
    assertEquals(List(Year(-1600), Year(0), Year(2026)).map(_.value), List(-1600, 0, 2026))
    assertEquals(Year.either(Int.MinValue).map(y => y.value: Int), Right(Int.MinValue))

  test("a period runs from its start to its end, both included; one ending before it starts is none"):
    val p = Period.from(Year(-1700), Year(-1600)).toOption.get
    assertEquals(({ val refactorTuple = (p.start.value, p.end.value, p.isPoint); (refactorTuple._1, refactorTuple._2, (refactorTuple._3).value) }: (Int, Int, Boolean)), (-1700, -1600, false))
    assert((Period.point(Year(-1600)).isPoint).value)
    assertEquals(((Period.from(Year(-1600), Year(-1700))).left.map(refactorError => (refactorError).value): Either[String, Period]), Left("the period ends (-1700) before it starts (-1600)"))

  test("coordinates are on the globe"):
    assertEquals((Latitude(36.4).value, Longitude(-25.4).value), (36.4, -25.4))
    assertEquals(List(-90.0, 90.0, 90.5).map(Latitude.option(_).isDefined), List(true, true, false))
    assertEquals(List(-180.0, 180.0, 180.5).map(Longitude.option(_).isDefined), List(true, true, false))

  test("a DOI is 10.<registrant>/<suffix>, without a resolver"):
    assertEquals(Doi("10.1038/nature14317").value, "10.1038/nature14317")
    assertEquals(
      List("10.1234/abc", "https://doi.org/10.1234/abc", "10.1234/", "doi:10.1/x").map(Doi.option(_).isDefined),
      List(true, false, false, false)
    )
    assert(Doi.either("nature14317").isLeft)

  test("a Wikidata item is Q and its number"):
    assertEquals(WikidataId("Q5788").value, "Q5788")
    assertEquals(
      List("Q1", "Q0", "q5788", "P31", "Q12x").map(WikidataId.option(_).isDefined),
      List(true, false, false, false, false)
    )

  test("a licence is the provider's statement, whatever it says: the host judges it"):
    assertEquals(
      List("CC-BY-4.0", "Public domain", "", "all rights reserved").map(Licence(_).value),
      List("CC-BY-4.0", "Public domain", "", "all rights reserved")
    )

  test("the SPI generation is compiled into the plugin: the host keeps no member a plugin could read it from"):
    assertEquals(Spi.Version, 2)
    assertEquals(Spi.getClass.getMethods.filter(_.getName == "Version").toList, Nil)
