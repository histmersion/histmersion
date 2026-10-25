package com.histmersion.pluginhost

import com.histmersion.api.BBox
import com.histmersion.plugin.*
import com.histmersion.values

/** The conversions between plugin-api's newtypes and the model's: free where both sides hold a newtype,
  * a parse where the host still holds a plain value, and every value a plugin hands over checked again —
  * a newtype is erased at run time, so `assume` (or a plugin built against another constraint) stands
  * in here for a plugin that hands over a value its type refuses. */
class SpiValuesSuite extends munit.FunSuite:

  test("towards a plugin: the host's values as the SPI's, a plain value that does not parse left out"):
    assertEquals(SpiValues.year(values.Year(-1600)), Year(-1600))
    assertEquals(SpiValues.year(-1600L), Some(Year(-1600)))
    assertEquals(SpiValues.year(5_000_000_000L), None)
    assertEquals(List(SpiValues.yearBound(-5_000_000_000L), SpiValues.yearBound(2026L)), List(Year(Int.MinValue), Year(2026)))
    assertEquals(SpiValues.period(-1600L, -1700L), Period.from(Year(-1700), Year(-1600)).toOption)
    assertEquals(SpiValues.period(values.Period.point(values.Year(7))), Some(Period.point(Year(7))))
    assertEquals((SpiValues.latitude(91.0), SpiValues.longitude(-181.0)), (None, None))
    assertEquals(SpiValues.doi(" 10.1234/abc "), Some(Doi("10.1234/abc")))
    assertEquals(SpiValues.doi("abc"), None)
    assertEquals((SpiValues.wikidata("Q5788"), SpiValues.wikidata("Troy")), (Some(WikidataId("Q5788")), None))
    assertEquals(SpiValues.area(-10, 30, 40, 50), Some((Longitude(-10), Latitude(30), Longitude(40), Latitude(50))))
    assertEquals(SpiValues.area(-10, 30, 400, 50), None)

  test("from a plugin: a valid value converts unchanged"):
    assertEquals(SpiValues.yearOf(Year(-1600)), Right(values.Year(-1600)))
    assertEquals(SpiValues.latitudeOf(Latitude(36.4)).map(l => l.value: Double), Right(36.4))
    assertEquals(SpiValues.longitudeOf(Longitude(25.4)).map(l => l.value: Double), Right(25.4))
    assertEquals(SpiValues.doiOf(Doi("10.1/x")).map(d => d.value: String), Right("10.1/x"))
    assertEquals(SpiValues.wikidataOf(WikidataId("Q1")).map(q => q.value: String), Right("Q1"))
    assertEquals(SpiValues.periodOf(Period.point(Year(3))), Right(values.Period.point(values.Year(3))))
    assertEquals(SpiValues.licenceOf(Licence("CC0-1.0")), "CC0-1.0")

  test("from a plugin: a value its type refuses is a Left naming it, never an exception"):
    assertEquals(SpiValues.latitudeOf(Latitude.assume(95.0)), Left("latitude 95.0 is not between -90 and 90"))
    assertEquals(SpiValues.longitudeOf(Longitude.assume(200.0)), Left("longitude 200.0 is not between -180 and 180"))
    assertEquals(SpiValues.doiOf(Doi.assume("nature14317")), Left("DOI 'nature14317' is not a DOI (10.<registrant>/<suffix>)"))
    assertEquals(SpiValues.wikidataOf(WikidataId.assume("q12")), Left("Wikidata id 'q12' is not a Wikidata item (Q and its number)"))
    assertEquals(SpiValues.optional(Some(Latitude.assume(-91.0)))(SpiValues.latitudeOf).isLeft, true)
    assertEquals(SpiValues.optional(Option.empty[Latitude])(SpiValues.latitudeOf), Right(None))

  private val event = CatalogueEvent("q-1", "https://q.example/1", "Quake", "earthquake", Some(Year(-1200)), Some(Year(-1190)),
    lat = Some(Latitude(36.4)), lon = Some(Longitude(25.4)), wikidata = Some(WikidataId("Q42")), central = Some(Year(-1195)))

  test("a catalogue record converts its years, position and Wikidata item into the host's record"):
    val r = PluginEvents.record("zq", event).toOption.get
    assertEquals((r.timeSpan.notBefore, r.timeSpan.notAfter, r.lat, r.lon, r.wikidata, r.central),
      (Some(-1200L), Some(-1190L), Some(36.4), Some(25.4), Some("Q42"), Some(-1195L)))

  test("a catalogue record with a value its type refuses is refused, naming the catalogue and the value"):
    assertEquals(PluginEvents.record("zq", event.copy(lat = Some(Latitude.assume(95.0)))),
      Left("q-1: catalogue 'zq': latitude 95.0 is not between -90 and 90"))
    assertEquals(PluginEvents.record("zq", event.copy(wikidata = Some(WikidataId.assume("Troy")))),
      Left("q-1: catalogue 'zq': Wikidata id 'Troy' is not a Wikidata item (Q and its number)"))

  test("a record the host cannot read at all (built against another SPI) is refused, not thrown"):
    val legacy = event.copy(notBefore = Some(java.lang.Long.valueOf(-1200L)).asInstanceOf[Option[Year]])
    val got = PluginEvents.record("zq", legacy)
    assert(got.left.exists(w => w.startsWith("q-1: catalogue 'zq' handed over a record the host cannot read") &&
      w.contains("plugin SPI 2")), got.toString)

  test("a media item's period converts, ends in either order; one the host cannot read makes the record unreadable"):
    val item = MediaItem("k1", "t", "https://x.example/1", Some("https://x.example/1.jpg"),
      Right(MediaRights(Licence("CC0-1.0"), credit = "c")), period = Period.from(Year(-500), Year(-400)).toOption)
    val rec = PluginMedia.record(item)
    assertEquals(rec.period, Some(values.Period.from(values.Year(-500), values.Year(-400)).toOption.get))
    assert(rec.rights.isRight)
    val broken = PluginMedia.record(item.copy(period = Some("1999").asInstanceOf[Option[Period]]))
    assertEquals(broken.period, None)
    assert(broken.rights.left.exists(_.startsWith("k1: a period the host cannot read")), broken.rights.toString)

  test("a query asks for the import's years and area as the SPI's values"):
    assertEquals(PluginEvents.query(Some(-3000L), 2026L, Some(BBox(-10, 30, 40, 50))),
      CatalogueQuery(Some(Year(-3000)), Year(2026), Some((Longitude(-10), Latitude(30), Longitude(40), Latitude(50)))))
    assertEquals(PluginEvents.query(None, 2026L, Some(BBox(-10, 30, 400, 50))).area, None)
