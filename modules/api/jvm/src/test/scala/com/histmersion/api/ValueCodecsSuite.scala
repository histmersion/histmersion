package com.histmersion.api


import com.histmersion.api.ValueCodecs.given
import com.histmersion.values.{Citekey, Doi, Latitude, Longitude, WikidataId, Year}
import io.circe.parser.decode
import io.circe.syntax.*

class ValueCodecsSuite extends munit.FunSuite:

  test("each newtype is its base type on the wire; a value its constraint refuses does not decode"):
    assertEquals(Citekey("anthony2007_horse").asJson.noSpaces, "\"anthony2007_horse\"")
    assertEquals(decode[Citekey]("\"anthony2007_horse\"").map(c => c.value: String), Right("anthony2007_horse"))
    assert(decode[Citekey]("\"../x\"").left.exists(_.getMessage.contains("'..'")))
    assertEquals(Year(-3300).asJson.noSpaces, "-3300")
    assertEquals(decode[Year]("-3300").map(y => y.value: Int), Right(-3300))
    assert(decode[Doi]("\"10.3390/rel10040258\"").isRight && decode[Doi]("\"doi:10.1/x\"").isLeft)
    assert(decode[WikidataId]("\"Q12554\"").isRight && decode[WikidataId]("\"P31\"").isLeft)
    assert(decode[Latitude]("41.9").isRight && decode[Latitude]("91").isLeft && decode[Longitude]("-181").isLeft)

  test("the schemas are the base types'"):
    assertEquals(summon[sttp.tapir.Schema[Citekey]].schemaType.toString, sttp.tapir.Schema.string.schemaType.toString)
    assertEquals(summon[sttp.tapir.Schema[Year]].schemaType.toString, sttp.tapir.Schema.schemaForInt.schemaType.toString)
