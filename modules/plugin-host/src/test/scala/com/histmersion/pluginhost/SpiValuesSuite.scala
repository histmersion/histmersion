package com.histmersion.pluginhost

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.BBox
import com.histmersion.plugin.*

/** The conversions between plugin-api's newtypes and the model's: plugin-api's judge nothing, so every value a plugin
  * hands over is checked by the model's type here, and a plain value the host still holds is parsed by it.
  */
class SpiValuesSuite extends munit.FunSuite:

  test("towards a plugin: the host's values as the SPI's, a plain value that does not parse left out"):
    assertEquals(SpiValues.year(com.histmersion.Year(-1600)), Year(-1600))
    assertEquals(SpiValues.year(com.histmersion.domain.DeepTime.SignedYear(-1600L)), Some(Year(-1600)))
    assertEquals(SpiValues.year(com.histmersion.domain.DeepTime.SignedYear(5_000_000_000L)), None)
    assertEquals(
      List(SpiValues.yearBound(com.histmersion.domain.DeepTime.SignedYear(-5_000_000_000L)),SpiValues.yearBound(com.histmersion.domain.DeepTime.SignedYear(2026L))),
      List(Year(Int.MinValue), Year(2026))
    )
    assertEquals(SpiValues.period(com.histmersion.domain.DeepTime.SignedYear(-1600L), com.histmersion.domain.DeepTime.SignedYear(-1700L)), Period.from(Year(-1700), Year(-1600)).toOption)
    assertEquals(SpiValues.period(com.histmersion.Period.point(com.histmersion.Year(7))), Some(Period.point(Year(7))))
    assertEquals((SpiValues.latitude(com.histmersion.plugin.Latitude(91.0)), SpiValues.longitude(com.histmersion.plugin.Longitude(-181.0))), (None, None))
    assertEquals(com.histmersion.domain.library.Doi.normalize(" 10.1234/abc ").flatMap(SpiValues.doi), Some(Doi("10.1234/abc")))
    assertEquals(com.histmersion.domain.library.Doi.normalize("abc").flatMap(SpiValues.doi), None)
    assertEquals((SpiValues.wikidata(com.histmersion.domain.catalogue.places.Wikidata.Id("Q5788")), com.histmersion.domain.catalogue.places.Wikidata.Id.option("Troy").flatMap(SpiValues.wikidata)), (Some(WikidataId("Q5788")), None))
    assertEquals(SpiValues.area(com.histmersion.plugin.Longitude(-10), com.histmersion.plugin.Latitude(30), com.histmersion.plugin.Longitude(40), com.histmersion.plugin.Latitude(50)), Some((Longitude(-10), Latitude(30), Longitude(40), Latitude(50))))
    assertEquals(SpiValues.area(com.histmersion.plugin.Longitude(-10), com.histmersion.plugin.Latitude(30), com.histmersion.plugin.Longitude(400), com.histmersion.plugin.Latitude(50)), None)

  test("from a plugin: a valid value converts unchanged"):
    assertEquals(SpiValues.yearOf(Year(-1600)), Right(com.histmersion.Year(-1600)))
    assertEquals(SpiValues.latitudeOf(Latitude(36.4)).map(l => l.value: Double), Right(36.4))
    assertEquals(SpiValues.longitudeOf(Longitude(25.4)).map(l => l.value: Double), Right(25.4))
    assertEquals(SpiValues.doiOf(Doi("10.1/x")).map(d => d.value: String), Right("10.1/x"))
    assertEquals(SpiValues.wikidataOf(WikidataId("Q1")).map(q => q.value: String), Right("Q1"))
    assertEquals(
      SpiValues.periodOf(Period.point(Year(3))),
      Right(com.histmersion.Period.point(com.histmersion.Year(3)))
    )
    assertEquals(SpiValues.licenceOf(Licence("CC0-1.0")), "CC0-1.0")

  test("from a plugin: a value its type refuses is a Left naming it, never an exception"):
    assertEquals(SpiValues.latitudeOf(Latitude(95.0)), Left("latitude 95.0 is not between -90 and 90"))
    assertEquals(SpiValues.longitudeOf(Longitude(200.0)), Left("longitude 200.0 is not between -180 and 180"))
    assertEquals(
      SpiValues.doiOf(Doi("nature14317")),
      Left("DOI 'nature14317' is not a DOI (10.<registrant>/<suffix>)")
    )
    assertEquals(
      SpiValues.wikidataOf(WikidataId("q12")),
      Left("Wikidata id 'q12' is not a Wikidata item (Q and its number)")
    )
    assertEquals(SpiValues.optional(Some(Latitude(-91.0)))(SpiValues.latitudeOf).isLeft, true)
    assertEquals(SpiValues.optional(Option.empty[Latitude])(SpiValues.latitudeOf), Right(None))

  private val event = CatalogueEvent(
    com.histmersion.plugin.CatalogueEvent.Id("q-1"),
    com.histmersion.plugin.CatalogueEvent.Url("https://q.example/1"),
    com.histmersion.plugin.CatalogueEvent.Label("Quake"),
    com.histmersion.plugin.CatalogueEvent.Kind("earthquake"),
    Some(Year(-1200)),
    Some(Year(-1190)),
    lat = Some(Latitude(36.4)),
    lon = Some(Longitude(25.4)),
    wikidata = Some(WikidataId("Q42")),
    central = Some(Year(-1195))
  )

  test("a catalogue record converts its years, position and Wikidata item into the host's record"):
    val Kept(r, warnings) = PluginEvents.record(com.histmersion.api.catalogue.CatalogRecord.Provider("zq"), event).toOption.get
    assertEquals(warnings, Nil)
    assertEquals(
      (r.timeSpan.notBefore, r.timeSpan.notAfter, r.lat, r.lon, r.wikidata, r.central),
      (Some(-1200L), Some(-1190L), Some(36.4), Some(25.4), Some("Q42"), Some(-1195L))
    )

  test("a catalogue record's bad position or Wikidata item is left out with a warning, the rest of the record kept"):
    val offGlobe = PluginEvents.record(com.histmersion.api.catalogue.CatalogRecord.Provider("zq"), event.modify(_.lat).setTo(Some(Latitude(95.0)))).toOption.get
    assertEquals((offGlobe.record.lat, offGlobe.record.lon, offGlobe.record.wikidata), (None, None, Some("Q42")))
    assertEquals((offGlobe.record.label, offGlobe.record.timeSpan.notBefore), ("Quake", Some(-1200L)))
    assertEquals(
      offGlobe.warnings,
      List(
        "q-1: catalogue 'zq': latitude 95.0 is not between -90 and 90, left out",
        "q-1: catalogue 'zq': a position needs both latitude and longitude, left out"
      )
    )
    val noItem = PluginEvents.record(com.histmersion.api.catalogue.CatalogRecord.Provider("zq"), event.modify(_.wikidata).setTo(Some(WikidataId("Troy")))).toOption.get
    assertEquals((noItem.record.lat, noItem.record.wikidata), (Some(36.4), None))
    assertEquals(
      noItem.warnings,
      List("q-1: catalogue 'zq': Wikidata id 'Troy' is not a Wikidata item (Q and its number), left out")
    )
    val half = PluginEvents.record(com.histmersion.api.catalogue.CatalogRecord.Provider("zq"), event.modify(_.lon).setTo(None)).toOption.get
    assertEquals((half.record.lat, half.record.lon), (None, None))
    assertEquals(half.warnings, List("q-1: catalogue 'zq': a position needs both latitude and longitude, left out"))

  test("a catalogue record whose dating is no dating is still refused, naming the catalogue and the value"):
    assertEquals(
      PluginEvents.record(com.histmersion.api.catalogue.CatalogRecord.Provider("zq"), event.modify(_.notBefore).setTo(Some(Year(-1180)))),
      Left("q-1: its dating runs backwards")
    )

  test("a value left out is a warning, a value kept none"):
    val (doi, why) = SpiValues.kept(Some(Doi("10.1/x")))(SpiValues.doiOf)
    assertEquals((doi.map(d => d.value: String), why), (Some("10.1/x"), None))
    assertEquals(
      SpiValues.kept(Some(Doi("x")))(SpiValues.doiOf),
      (None, Some("DOI 'x' is not a DOI (10.<registrant>/<suffix>)"))
    )
    assertEquals(SpiValues.kept(Option.empty[Doi])(SpiValues.doiOf), (None, None))

  test("a record the host cannot read at all (built against another SPI) is refused, not thrown"):
    val legacy = event.modify(_.notBefore).setTo(Some(java.lang.Long.valueOf(-1200L)).asInstanceOf[Option[Year]])
    val got = PluginEvents.record(com.histmersion.api.catalogue.CatalogRecord.Provider("zq"), legacy)
    assert(
      got.left.exists(w =>
        w.value.startsWith("q-1: catalogue 'zq' handed over a record the host cannot read") &&
          w.value.contains("plugin SPI 2")
      ),
      got.toString
    )

  test("a media item's period converts, ends in either order; one the host cannot read makes the record unreadable"):
    val item = MediaItem(
      com.histmersion.plugin.MediaItem.Key("k1"),
      com.histmersion.plugin.MediaItem.Title("t"),
      com.histmersion.plugin.MediaItem.PageUrl("https://x.example/1"),
      Some(com.histmersion.plugin.MediaItem.ImageUrl("https://x.example/1.jpg")),
      Right(MediaRights(Licence("CC0-1.0"), credit = com.histmersion.plugin.MediaRights.Credit("c"))),
      period = Period.from(Year(-500), Year(-400)).toOption
    )
    val rec = PluginMedia.record(item)
    assertEquals(
      rec.period,
      Some(com.histmersion.Period.from(com.histmersion.Year(-500), com.histmersion.Year(-400)).toOption.get)
    )
    assert(rec.rights.isRight)
    val broken = PluginMedia.record(item.modify(_.period).setTo(Some("1999").asInstanceOf[Option[Period]]))
    assertEquals(broken.period, None)
    assert(broken.rights.left.exists(_.value.startsWith("k1: a period the host cannot read")), broken.rights.toString)

  test("a query asks for the import's years and area as the SPI's values"):
    assertEquals(
      PluginEvents.query(Some(com.histmersion.domain.DeepTime.SignedYear(-3000L)), com.histmersion.domain.DeepTime.SignedYear(2026L), Some(BBox(com.histmersion.api.catalogue.Longitude(-10), com.histmersion.api.catalogue.Latitude(30), com.histmersion.api.catalogue.Longitude(40), com.histmersion.api.catalogue.Latitude(50)))),
      CatalogueQuery(Some(Year(-3000)), Year(2026), Some((Longitude(-10), Latitude(30), Longitude(40), Latitude(50))))
    )
    assertEquals(PluginEvents.query(None, com.histmersion.domain.DeepTime.SignedYear(2026L), Some(BBox(com.histmersion.api.catalogue.Longitude(-10), com.histmersion.api.catalogue.Latitude(30), com.histmersion.api.catalogue.Longitude(400), com.histmersion.api.catalogue.Latitude(50)))).area, None)
