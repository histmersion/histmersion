package com.histmersion
package plugin

import com.histmersion.util.problem.Text.sentence

/** The SPI's own newtypes: what each accepts, at compile time for a literal and at run time for a value read from a
  * provider; and the SPI generation compiled into the plugin.
  */
class ValuesSuite extends munit.FunSuite:

  test("a year is any integer, BCE negative"):
    assertEquals(List(Year(-1600), Year(0), Year(2026)).map(_.value), List(-1600, 0, 2026))
    assertEquals(Year.either(Int.MinValue).map(y => y.value: Int), Right(Int.MinValue))

  test("a period runs from its start to its end, both included; one ending before it starts is none"):
    val p = Period.from(Year(-1700), Year(-1600)).toOption.get
    assertEquals(
      ({
        val refactorTuple = (p.start.value, p.end.value, p.isPoint);
        (refactorTuple._1, refactorTuple._2, refactorTuple._3)
      }: (Int, Int, Boolean)),
      (-1700, -1600, false)
    )
    assert(Period.point(Year(-1600)).isPoint)
    assertEquals(
      ((Period
        .from(Year(-1600), Year(-1700)))
        .left
        .map(refactorError => (refactorError).sentence): Either[String, Period]),
      Left("end: year -1700 is below -1600")
    )

  // The SPI's values are unconstrained: the host validates them where they enter (SpiValuesSuite in plugin-host,
  // and the domain types' own suites), so a rule change on the host needs no plugin release.
  test("coordinates, DOIs and Wikidata ids are carried unchanged: the host judges them"):
    assertEquals((Latitude(36.4).value, Longitude(-25.4).value), (36.4, -25.4))
    assertEquals(List(90.5, -91.0).map(Latitude(_).value), List(90.5, -91.0))
    assertEquals(Longitude(180.5).value, 180.5)
    assertEquals(
      List("10.1038/nature14317", "https://doi.org/10.1234/abc", "nature14317").map(Doi(_).value),
      List("10.1038/nature14317", "https://doi.org/10.1234/abc", "nature14317")
    )
    assertEquals(List("Q5788", "q12", "P31").map(WikidataId(_).value), List("Q5788", "q12", "P31"))

  test("a licence is the provider's statement, whatever it says: the host judges it"):
    assertEquals(
      List("CC-BY-4.0", "Public domain", "", "all rights reserved").map(Licence(_).value),
      List("CC-BY-4.0", "Public domain", "", "all rights reserved")
    )

  test("the SPI generation is compiled into the plugin: the host keeps no member a plugin could read it from"):
    assertEquals(Spi.Version, 2)
    assertEquals(Spi.getClass.getMethods.filter(_.getName == "Version").toList, Nil)
