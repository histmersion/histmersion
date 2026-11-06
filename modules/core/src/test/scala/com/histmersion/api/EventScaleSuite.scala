package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.EventsMarkdown

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import io.circe.parser.decode
import io.circe.syntax.*
import com.histmersion.util.data.exchange.BundleFormat
import com.histmersion.api.catalogue.{
  EventGeo,
  EventGeometry,
  EventList,
  EventPatch,
  EventPut,
  EventScale,
  EventSummary
}
import com.histmersion.api.views.EventDetail
import com.histmersion.api.exchange.Bundle

/** An event's scale (#1440): parsing, the defaults from the kind, the sub-event rule, the place's kind and the
  * geometry; the point an event can be given; the years it covers on the map; the Markdown.
  */
class EventScaleSuite extends munit.FunSuite:

  test("parse: local and regional are stored, auto clears, anything else is refused"):
    assertEquals(EventScale.parse(com.histmersion.api.catalogue.EventSummary.Scale("local")), Some(Some("local")))
    assertEquals(EventScale.parse(com.histmersion.api.catalogue.EventSummary.Scale(" Regional ")), Some(Some("regional")))
    assertEquals(EventScale.parse(com.histmersion.api.catalogue.EventSummary.Scale("auto")), Some(None))
    assertEquals(EventScale.parse(com.histmersion.api.catalogue.EventSummary.Scale("global")), None)
    assertEquals(EventScale.parse(com.histmersion.api.catalogue.EventSummary.Scale("")), None)

  test("the kind decides first: battle, siege, destruction local; migration, change, era, war regional"):
    List("battle", "siege", "destruction", "Destruction", "construction", "founding").foreach(k =>
      assertEquals(EventScale.ofKind(com.histmersion.api.catalogue.EventSummary.Kind(k)), Some(EventScale.Local), k)
    )
    List(
      "migration",
      "technological_change",
      "Technological change",
      "cultural-shift",
      "era",
      "diplomatic_era",
      "war",
      "invasion",
      "collapse",
      "polity_formation",
      "period",
      "reign"
    ).foreach(k => assertEquals(EventScale.ofKind(com.histmersion.api.catalogue.EventSummary.Kind(k)), Some(EventScale.Regional), k))
    List("event", "natural_disaster", "treaty", "").foreach(k => assertEquals(EventScale.ofKind(com.histmersion.api.catalogue.EventSummary.Kind(k)), None, k))

  test("the place's kind: a site or feature is local, a region, polity or people regional"):
    assertEquals(EventScale.ofPlaceKind(com.histmersion.api.catalogue.Place.Kind.Wire("site")), Some("local"))
    assertEquals(EventScale.ofPlaceKind(com.histmersion.api.catalogue.Place.Kind.Wire("feature")), Some("local"))
    assertEquals(EventScale.ofPlaceKind(com.histmersion.api.catalogue.Place.Kind.Wire("region")), Some("regional"))
    assertEquals(EventScale.ofPlaceKind(com.histmersion.api.catalogue.Place.Kind.Wire("polity")), Some("regional"))
    assertEquals(EventScale.ofPlaceKind(com.histmersion.api.catalogue.Place.Kind.Wire("people")), Some("regional"))
    assertEquals(EventScale.ofPlaceKind(com.histmersion.api.catalogue.Place.Kind.Wire("something")), None)
    assertEquals(EventScale.ofGeometry(com.histmersion.api.catalogue.EventScale.GeometryType("POINT")), Some("local"))
    assertEquals(EventScale.ofGeometry(com.histmersion.api.catalogue.EventScale.GeometryType("ST_MultiPolygon")), Some("regional"))
    assertEquals(EventScale.ofGeometry(com.histmersion.api.catalogue.EventScale.GeometryType("LINESTRING")), None)

  test("effective: stated, then kind, then own geometry, then place kind, then sub-event, then local"):
    def eff(
        stated: Option[com.histmersion.api.catalogue.EventSummary.Scale] = None,
        kind: com.histmersion.api.catalogue.EventScale.EffectiveKind = com.histmersion.api.catalogue.EventScale.EffectiveKind("event"),
        parent: Boolean = false,
        place: Option[com.histmersion.api.catalogue.Place.Kind.Wire] = None,
        geom: Option[com.histmersion.api.catalogue.EventScale.GeometryType] = None
    ) =
      EventScale.effective(stated, kind, parent, place, geom)
    assertEquals(eff(stated = Some(com.histmersion.api.catalogue.EventSummary.Scale("regional")), kind = com.histmersion.api.catalogue.EventScale.EffectiveKind("battle")), "regional")
    assertEquals(eff(stated = Some(com.histmersion.api.catalogue.EventSummary.Scale("nonsense")), kind = com.histmersion.api.catalogue.EventScale.EffectiveKind("battle")), "local", "an unparsable stated value is ignored")
    assertEquals(eff(kind = com.histmersion.api.catalogue.EventScale.EffectiveKind("battle"), place = Some(com.histmersion.api.catalogue.Place.Kind.Wire("region"))), "local", "a battle at a region is still local")
    assertEquals(eff(kind = com.histmersion.api.catalogue.EventScale.EffectiveKind("war"), place = Some(com.histmersion.api.catalogue.Place.Kind.Wire("site"))), "regional", "a war at its capital is still regional")
    assertEquals(eff(kind = com.histmersion.api.catalogue.EventScale.EffectiveKind("event"), parent = true), "local", "a part of a larger event, nothing else said")
    assertEquals(
      eff(kind = com.histmersion.api.catalogue.EventScale.EffectiveKind("natural_disaster"), parent = true, place = Some(com.histmersion.api.catalogue.Place.Kind.Wire("region"))),
      "regional",
      "a drought within a collapse, at a region, is regional"
    )
    assertEquals(
      eff(kind = com.histmersion.api.catalogue.EventScale.EffectiveKind("event"), place = Some(com.histmersion.api.catalogue.Place.Kind.Wire("region")), geom = Some(com.histmersion.api.catalogue.EventScale.GeometryType("POINT"))),
      "local",
      "its own point is what the map draws it at"
    )
    assertEquals(eff(kind = com.histmersion.api.catalogue.EventScale.EffectiveKind("natural_disaster"), place = Some(com.histmersion.api.catalogue.Place.Kind.Wire("site"))), "local")
    assertEquals(eff(kind = com.histmersion.api.catalogue.EventScale.EffectiveKind("natural_disaster"), place = Some(com.histmersion.api.catalogue.Place.Kind.Wire("region"))), "regional")
    assertEquals(eff(kind = com.histmersion.api.catalogue.EventScale.EffectiveKind("event"), geom = Some(com.histmersion.api.catalogue.EventScale.GeometryType("POLYGON"))), "regional")
    assertEquals(eff(kind = com.histmersion.api.catalogue.EventScale.EffectiveKind("event")), "local")

  test("EventGeometry: a point is GeoJSON, longitude first; lat/lon and geometry exclude each other"):
    assertEquals(EventGeometry.point(com.histmersion.api.catalogue.Latitude(36.5), com.histmersion.api.catalogue.Longitude(-1.25)), Right("""{"type":"Point","coordinates":[-1.25,36.5]}"""))
    assertEquals(EventGeometry.point(com.histmersion.api.catalogue.Latitude(1e-4), com.histmersion.api.catalogue.Longitude(0.0)), Right("""{"type":"Point","coordinates":[0,0.0001]}"""))
    assert(EventGeometry.point(com.histmersion.api.catalogue.Latitude(91), com.histmersion.api.catalogue.Longitude(0)).isLeft)
    assert(EventGeometry.point(com.histmersion.api.catalogue.Latitude(0), com.histmersion.api.catalogue.Longitude(181)).isLeft)
    assertEquals(EventGeometry.of(None, None, None), Right(None))
    assertEquals(EventGeometry.of(Some(com.histmersion.api.catalogue.EventGeometry.Geojson(" {} ")), None, None), Right(Some("{}")))
    assert(EventGeometry.of(Some(com.histmersion.api.catalogue.EventGeometry.Geojson("{}")), Some(com.histmersion.api.catalogue.Latitude(1)), Some(com.histmersion.api.catalogue.Longitude(2))).isLeft)
    assert(EventGeometry.of(None, Some(com.histmersion.api.catalogue.Latitude(1)), None).isLeft)
    assertEquals(EventGeometry.of(None, Some(com.histmersion.api.catalogue.Latitude(1)), Some(com.histmersion.api.catalogue.Longitude(2))), Right(Some("""{"type":"Point","coordinates":[2,1]}""")))

  test("EventGeometry.check: GeoJSON geometries pass; WKT, Features and non-JSON are refused (#2554)"):
    val pt = """{"type":"Point","coordinates":[36.5,34.55]}"""
    assertEquals(EventGeometry.check(com.histmersion.api.catalogue.EventGeometry.Geojson(s"  $pt\n")), Right(pt))
    assert(EventGeometry.check(com.histmersion.api.catalogue.EventGeometry.Geojson("""{"type":"MultiPolygon","coordinates":[]}""")).isRight)
    val wkt = EventGeometry.check(com.histmersion.api.catalogue.EventGeometry.Geojson("POINT(36.5 34.55)"))
    assert(wkt.left.exists(_.value.contains("not WKT")), wkt.toString)
    assert(EventGeometry.check(com.histmersion.api.catalogue.EventGeometry.Geojson("srid=4326;polygon((0 0,1 0,1 1,0 0))")).left.exists(_.value.contains("not WKT")))
    assert(EventGeometry.check(com.histmersion.api.catalogue.EventGeometry.Geojson("""{"type":"Feature","geometry":null}""")).left.exists(_.value.contains("not a Feature")))
    assert(EventGeometry.check(com.histmersion.api.catalogue.EventGeometry.Geojson("""{"type":"Circle"}""")).left.exists(_.value.contains("'Circle'")))
    assert(EventGeometry.check(com.histmersion.api.catalogue.EventGeometry.Geojson("""{"coordinates":[1,2]}""")).left.exists(_.value.contains("no \"type\"")))
    assert(EventGeometry.check(com.histmersion.api.catalogue.EventGeometry.Geojson("[1,2]")).isLeft)
    assert(EventGeometry.check(com.histmersion.api.catalogue.EventGeometry.Geojson("near Kadesh")).left.exists(_.value.contains("not GeoJSON")))

  test("EventGeo.outer: the outer span, one known end alone, nothing when undated"):
    assertEquals((TimeSpan(Some(com.histmersion.api.catalogue.Year(-1300)), Some(com.histmersion.api.catalogue.Year(-1290)), Some(com.histmersion.api.catalogue.Year(-1260)), Some(com.histmersion.api.catalogue.Year(-1250)))).outer, Some((-1300L, -1250L)))
    assertEquals((TimeSpan(beginBy = Some(com.histmersion.api.catalogue.Year(-1290)), endFrom = Some(com.histmersion.api.catalogue.Year(-1260)))).outer, Some((-1290L, -1260L)))
    assertEquals((TimeSpan(notBefore = Some(com.histmersion.api.catalogue.Year(-1274)))).outer, Some((-1274L, -1274L)))
    assertEquals((TimeSpan()).outer, None)

  test("EventGeo.outer: an open-ended event stands at the end it states, not forever after (#1768)"):
    // a start alone: that year; a start window alone: the window; an end alone: that year
    assertEquals((TimeSpan(beginBy = Some(com.histmersion.api.catalogue.Year(-1200)))).outer, Some((-1200L, -1200L)))
    assertEquals((TimeSpan(notBefore = Some(com.histmersion.api.catalogue.Year(-1200)), beginBy = Some(com.histmersion.api.catalogue.Year(-1195)))).outer, Some((-1200L, -1195L)))
    assertEquals((TimeSpan(notAfter = Some(com.histmersion.api.catalogue.Year(-1100)))).outer, Some((-1100L, -1100L)))
    assertEquals((TimeSpan(endFrom = Some(com.histmersion.api.catalogue.Year(-1110)), notAfter = Some(com.histmersion.api.catalogue.Year(-1100)))).outer, Some((-1110L, -1100L)))
    // points recorded out of order are ordered, not inverted
    assertEquals((TimeSpan(notBefore = Some(com.histmersion.api.catalogue.Year(-1000)), notAfter = Some(com.histmersion.api.catalogue.Year(-1100)))).outer, Some((-1100L, -1000L)))
    assertEquals(EventGeo.missingEnd(TimeSpan(notBefore = Some(com.histmersion.api.catalogue.Year(-1200)))), Some("end not recorded"))
    assertEquals(EventGeo.missingEnd(TimeSpan(endFrom = Some(com.histmersion.api.catalogue.Year(-1200)))), Some("start not recorded"))
    assertEquals(EventGeo.missingEnd(TimeSpan(Some(com.histmersion.api.catalogue.Year(-1300)), None, None, Some(com.histmersion.api.catalogue.Year(-1250)))), None)
    assertEquals(EventGeo.missingEnd(TimeSpan()), None)

  test("the wire: a patch and a put take scale; a summary carries it with whether it is stated"):
    assertEquals(decode[EventPatch]("""{"scale":"regional"}"""), Right(EventPatch(scale = Some(com.histmersion.api.catalogue.EventSummary.Scale("regional")))))
    assertEquals(decode[EventPut]("""{"label":"x","scale":"local"}""").map(_.scale), Right(Some("local")))
    val s = EventSummary(com.histmersion.api.catalogue.Event.Id("e"), com.histmersion.api.catalogue.EventSummary.Label("E"), com.histmersion.api.catalogue.EventSummary.Kind("battle"), TimeSpan(), com.histmersion.api.catalogue.EventSummary.Historicity("attested"), scale = com.histmersion.api.catalogue.EventSummary.Scale("local"), scaleStated = com.histmersion.api.catalogue.EventSummary.ScaleStated(true))
    assertEquals(decode[EventSummary](s.asJson.noSpaces), Right(s))
    // an older client's summary without the fields still reads
    assertEquals(
      decode[EventSummary]("""{"id":"e","label":"E","kind":"battle","timeSpan":{},"historicity":"attested"}""")
        .map(_.scale),
      Right("local")
    )

  test("Markdown: the list line and the detail show the scale and whether it is stated"):
    val e = EventSummary(
      com.histmersion.api.catalogue.Event.Id("kadesh"),
      com.histmersion.api.catalogue.EventSummary.Label("Battle of Kadesh"),
      com.histmersion.api.catalogue.EventSummary.Kind("battle"),
      TimeSpan(Some(com.histmersion.api.catalogue.Year(-1274)), None, None, Some(com.histmersion.api.catalogue.Year(-1274))),
      com.histmersion.api.catalogue.EventSummary.Historicity("attested"),
      scale = com.histmersion.api.catalogue.EventSummary.Scale("local"),
      scaleStated = com.histmersion.api.catalogue.EventSummary.ScaleStated(false)
    )
    assert(EventsMarkdown.eventList(EventList(com.histmersion.api.RowCount(1), List(e))).contains("_(battle · local · attested ·"))
    assert(EventsMarkdown.event(EventDetail(e)).contains("**Scale:** local (derived)"))
    assert(
      EventsMarkdown
        .event(EventDetail(e.modify(_.scale).setTo("regional").modify(_.scaleStated).setTo(true)))
        .contains("**Scale:** regional (stated)")
    )

  test("a bundle event that states no scale has the rev it had before V078"):
    val ev = Bundle.Event(
      java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
      java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"),
      com.histmersion.api.exchange.Bundle.Event.Id("e"),
      com.histmersion.api.exchange.Bundle.Event.Label("E"),
      com.histmersion.api.exchange.Bundle.Event.Kind("battle"),
      None,
      None,
      None,
      None,
      com.histmersion.api.exchange.Bundle.Event.Historicity("attested"),
      None,
      None,
      None,
      None,
      None,
      None,
      Nil,
      Nil,
      Nil,
      com.histmersion.api.exchange.Bundle.Event.UpdatedAt("2026-01-01T00:00:00Z")
    )
    val line = BundleFormat.stamp(ev).line
    assert(!line.contains("scale"), line)
    assert(BundleFormat.stamp(ev.modify(_.scale).setTo(Some("regional"))).line.contains("\"scale\":\"regional\""))
    assertNotEquals(BundleFormat.stamp(ev.modify(_.scale).setTo(Some("regional"))).rev, BundleFormat.stamp(ev).rev)
