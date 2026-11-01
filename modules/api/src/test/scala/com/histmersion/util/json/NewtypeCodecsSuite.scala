package com.histmersion.util.json

import com.histmersion.util.json.NewtypeCodecs.given
import com.histmersion.values.*
import io.circe.{Decoder, Encoder, Json}
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.tapir.Schema

import java.util.UUID

class NewtypeCodecsSuite extends munit.FunSuite:

  /** The newtype's JSON is its base value's, it decodes back to itself, and its schema is the base type's. */
  private def roundTrip[T: {Encoder, Decoder, Schema}, B: {Encoder, Schema}](value: T, base: B)(using
      munit.Location
  ): Unit =
    assertEquals(value.asJson, base.asJson)
    assertEquals(decode[T](value.asJson.noSpaces), Right(value))
    assertEquals(summon[Schema[T]].schemaType.toString, summon[Schema[B]].schemaType.toString)
    assertEquals(summon[Schema[T]].name, None)
    assertEquals[Any, Any](summon[Schema[T]].validator, summon[Schema[B]].validator)

  private val uuid = UUID.fromString("0b3f6a4e-2c1d-4e5f-8a9b-0c1d2e3f4a5b")
  private inline val jobText = "0b3f6a4e-2c1d-4e5f-8a9b-0c1d2e3f4a5b"

  test("the identifiers over String are their text on the wire"):
    roundTrip(Slug("sea_peoples"), "sea_peoples")
    roundTrip(GoalSlug("bronze-age.collapse"), "bronze-age.collapse")
    roundTrip(EventId("uluburun_shipwreck"), "uluburun_shipwreck")
    roundTrip(PlaceId("geonames_2911611"), "geonames_2911611")
    roundTrip(JobId(jobText), jobText)
    roundTrip(RunId(jobText), jobText)
    roundTrip(ArxivId("2101.00001"), "2101.00001")
    roundTrip(Pmid("12345678"), "12345678")
    roundTrip(Pmcid("PMC1234567"), "PMC1234567")
    roundTrip(Isbn("978-1-948488-83-9"), "978-1-948488-83-9")
    roundTrip(ArchiveId("hittiteempire00gurn"), "hittiteempire00gurn")
    roundTrip(PluginId("oa-unpaywall"), "oa-unpaywall")
    roundTrip(UserId.Local, "local")

  test("the uids are UUIDs on the wire"):
    roundTrip(SourceUid(uuid), uuid)
    roundTrip(ArticleUid(uuid), uuid)
    roundTrip(EventUid(uuid), uuid)
    roundTrip(GoalUid(uuid), uuid)
    roundTrip(AssetUid(uuid), uuid)

  test("the existing value newtypes come out of the generic instances as their hand-written ones do"):
    roundTrip(Year(-3300), -3300)
    roundTrip(Doi("10.3390/rel10040258"), "10.3390/rel10040258")
    roundTrip(WikidataId("Q12554"), "Q12554")
    roundTrip(Latitude(41.9), 41.9)
    roundTrip(Longitude(-12.5), -12.5)

  test("titles, authors and paging are their base values on the wire"):
    roundTrip(Title("The Sea Peoples"), "The Sea Peoples")
    roundTrip(AuthorName("Kristiansen, K."), "Kristiansen, K.")
    roundTrip(LicenceId("CC-BY-SA-4.0"), "CC-BY-SA-4.0")
    roundTrip(PageSize(200), 200)
    roundTrip(Offset.Start, 0)
    assert(decode[PageSize]("0").isLeft)
    assert(decode[Offset]("-1").isLeft)

  test("a value its constraint refuses does not decode"):
    assert(decode[Slug]("\"Sea Peoples\"").isLeft)
    assert(decode[Slug](Json.fromString("a" * 122).noSpaces).isLeft)
    assert(decode[GoalSlug]("\"-x\"").isLeft)
    assert(decode[EventId]("\"\"").isLeft)
    assert(decode[PlaceId]("\"2911611\"").isLeft && decode[PlaceId]("\"geonames_\"").isLeft)
    assert(decode[JobId]("\"not-a-uuid\"").isLeft && decode[RunId]("\"0B3F6A4E-2C1D-4E5F-8A9B-0C1D2E3F4A5B\"").isLeft)
    assert(decode[PluginId]("\"Oa_Unpaywall\"").isLeft)
    assert(decode[SourceUid]("\"x\"").isLeft)
    assert(decode[Doi]("\"doi:10.1/x\"").isLeft && decode[Latitude]("91").isLeft)
