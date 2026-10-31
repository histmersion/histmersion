package com.histmersion.api

import com.histmersion.config.*
import com.histmersion.files.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import io.circe.parser.decode
import io.circe.syntax.*
import com.histmersion.util.data.exchange.BundleFormat

/** An event's scale (#1440): parsing, the defaults from the kind, the sub-event rule, the place's kind and the
  * geometry; the point an event can be given; the years it covers on the map; the Markdown.
  */
class EventScaleSuite extends munit.FunSuite:

  test("parse: local and regional are stored, auto clears, anything else is refused"):
    assertEquals(EventScale.parse("local"), Some(Some("local")))
    assertEquals(EventScale.parse(" Regional "), Some(Some("regional")))
    assertEquals(EventScale.parse("auto"), Some(None))
    assertEquals(EventScale.parse("global"), None)
    assertEquals(EventScale.parse(""), None)

  test("the kind decides first: battle, siege, destruction local; migration, change, era, war regional"):
    List("battle", "siege", "destruction", "Destruction", "construction", "founding").foreach(k =>
      assertEquals(EventScale.ofKind(k), Some(EventScale.Local), k)
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
    ).foreach(k => assertEquals(EventScale.ofKind(k), Some(EventScale.Regional), k))
    List("event", "natural_disaster", "treaty", "").foreach(k => assertEquals(EventScale.ofKind(k), None, k))

  test("the place's kind: a site or feature is local, a region, polity or people regional"):
    assertEquals(EventScale.ofPlaceKind("site"), Some("local"))
    assertEquals(EventScale.ofPlaceKind("feature"), Some("local"))
    assertEquals(EventScale.ofPlaceKind("region"), Some("regional"))
    assertEquals(EventScale.ofPlaceKind("polity"), Some("regional"))
    assertEquals(EventScale.ofPlaceKind("people"), Some("regional"))
    assertEquals(EventScale.ofPlaceKind("something"), None)
    assertEquals(EventScale.ofGeometry("POINT"), Some("local"))
    assertEquals(EventScale.ofGeometry("ST_MultiPolygon"), Some("regional"))
    assertEquals(EventScale.ofGeometry("LINESTRING"), None)

  test("effective: stated, then kind, then own geometry, then place kind, then sub-event, then local"):
    def eff(
        stated: Option[String] = None,
        kind: String = "event",
        parent: Boolean = false,
        place: Option[String] = None,
        geom: Option[String] = None
    ) =
      EventScale.effective(stated, kind, parent, place, geom)
    assertEquals(eff(stated = Some("regional"), kind = "battle"), "regional")
    assertEquals(eff(stated = Some("nonsense"), kind = "battle"), "local", "an unparsable stated value is ignored")
    assertEquals(eff(kind = "battle", place = Some("region")), "local", "a battle at a region is still local")
    assertEquals(eff(kind = "war", place = Some("site")), "regional", "a war at its capital is still regional")
    assertEquals(eff(kind = "event", parent = true), "local", "a part of a larger event, nothing else said")
    assertEquals(
      eff(kind = "natural_disaster", parent = true, place = Some("region")),
      "regional",
      "a drought within a collapse, at a region, is regional"
    )
    assertEquals(
      eff(kind = "event", place = Some("region"), geom = Some("POINT")),
      "local",
      "its own point is what the map draws it at"
    )
    assertEquals(eff(kind = "natural_disaster", place = Some("site")), "local")
    assertEquals(eff(kind = "natural_disaster", place = Some("region")), "regional")
    assertEquals(eff(kind = "event", geom = Some("POLYGON")), "regional")
    assertEquals(eff(kind = "event"), "local")

  test("EventGeometry: a point is GeoJSON, longitude first; lat/lon and geometry exclude each other"):
    assertEquals(EventGeometry.point(36.5, -1.25), Right("""{"type":"Point","coordinates":[-1.25,36.5]}"""))
    assertEquals(EventGeometry.point(1e-4, 0.0), Right("""{"type":"Point","coordinates":[0,0.0001]}"""))
    assert(EventGeometry.point(91, 0).isLeft)
    assert(EventGeometry.point(0, 181).isLeft)
    assertEquals(EventGeometry.of(None, None, None), Right(None))
    assertEquals(EventGeometry.of(Some(" {} "), None, None), Right(Some("{}")))
    assert(EventGeometry.of(Some("{}"), Some(1), Some(2)).isLeft)
    assert(EventGeometry.of(None, Some(1), None).isLeft)
    assertEquals(EventGeometry.of(None, Some(1), Some(2)), Right(Some("""{"type":"Point","coordinates":[2,1]}""")))

  test("EventGeometry.check: GeoJSON geometries pass; WKT, Features and non-JSON are refused (#2554)"):
    val pt = """{"type":"Point","coordinates":[36.5,34.55]}"""
    assertEquals(EventGeometry.check(s"  $pt\n"), Right(pt))
    assert(EventGeometry.check("""{"type":"MultiPolygon","coordinates":[]}""").isRight)
    val wkt = EventGeometry.check("POINT(36.5 34.55)")
    assert(wkt.left.exists(_.contains("not WKT")), wkt.toString)
    assert(EventGeometry.check("srid=4326;polygon((0 0,1 0,1 1,0 0))").left.exists(_.contains("not WKT")))
    assert(EventGeometry.check("""{"type":"Feature","geometry":null}""").left.exists(_.contains("not a Feature")))
    assert(EventGeometry.check("""{"type":"Circle"}""").left.exists(_.contains("'Circle'")))
    assert(EventGeometry.check("""{"coordinates":[1,2]}""").left.exists(_.contains("no \"type\"")))
    assert(EventGeometry.check("[1,2]").isLeft)
    assert(EventGeometry.check("near Kadesh").left.exists(_.contains("not GeoJSON")))

  test("EventGeo.outer: the outer span, one known end alone, nothing when undated"):
    assertEquals(EventGeo.outer(TimeSpan(Some(-1300), Some(-1290), Some(-1260), Some(-1250))), Some((-1300L, -1250L)))
    assertEquals(EventGeo.outer(TimeSpan(beginBy = Some(-1290), endFrom = Some(-1260))), Some((-1290L, -1260L)))
    assertEquals(EventGeo.outer(TimeSpan(notBefore = Some(-1274))), Some((-1274L, -1274L)))
    assertEquals(EventGeo.outer(TimeSpan()), None)

  test("EventGeo.outer: an open-ended event stands at the end it states, not forever after (#1768)"):
    // a start alone: that year; a start window alone: the window; an end alone: that year
    assertEquals(EventGeo.outer(TimeSpan(beginBy = Some(-1200))), Some((-1200L, -1200L)))
    assertEquals(EventGeo.outer(TimeSpan(notBefore = Some(-1200), beginBy = Some(-1195))), Some((-1200L, -1195L)))
    assertEquals(EventGeo.outer(TimeSpan(notAfter = Some(-1100))), Some((-1100L, -1100L)))
    assertEquals(EventGeo.outer(TimeSpan(endFrom = Some(-1110), notAfter = Some(-1100))), Some((-1110L, -1100L)))
    // points recorded out of order are ordered, not inverted
    assertEquals(EventGeo.outer(TimeSpan(notBefore = Some(-1000), notAfter = Some(-1100))), Some((-1100L, -1000L)))
    assertEquals(EventGeo.missingEnd(TimeSpan(notBefore = Some(-1200))), Some("end not recorded"))
    assertEquals(EventGeo.missingEnd(TimeSpan(endFrom = Some(-1200))), Some("start not recorded"))
    assertEquals(EventGeo.missingEnd(TimeSpan(Some(-1300), None, None, Some(-1250))), None)
    assertEquals(EventGeo.missingEnd(TimeSpan()), None)

  test("the wire: a patch and a put take scale; a summary carries it with whether it is stated"):
    assertEquals(decode[EventPatch]("""{"scale":"regional"}"""), Right(EventPatch(scale = Some("regional"))))
    assertEquals(decode[EventPut]("""{"label":"x","scale":"local"}""").map(_.scale), Right(Some("local")))
    val s = EventSummary("e", "E", "battle", TimeSpan(), "attested", scale = "local", scaleStated = true)
    assertEquals(decode[EventSummary](s.asJson.noSpaces), Right(s))
    // an older client's summary without the fields still reads
    assertEquals(
      decode[EventSummary]("""{"id":"e","label":"E","kind":"battle","timeSpan":{},"historicity":"attested"}""")
        .map(_.scale),
      Right("local")
    )

  test("Markdown: the list line and the detail show the scale and whether it is stated"):
    val e = EventSummary(
      "kadesh",
      "Battle of Kadesh",
      "battle",
      TimeSpan(Some(-1274), None, None, Some(-1274)),
      "attested",
      scale = "local",
      scaleStated = false
    )
    assert(Markdown.eventList(EventList(1, List(e))).contains("_(battle · local · attested ·"))
    assert(Markdown.event(EventDetail(e)).contains("**Scale:** local (derived)"))
    assert(
      Markdown
        .event(EventDetail(e.copy(scale = "regional", scaleStated = true)))
        .contains("**Scale:** regional (stated)")
    )

  test("a bundle event that states no scale has the rev it had before V078"):
    val ev = BundleEvent(
      java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
      java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"),
      "e",
      "E",
      "battle",
      None,
      None,
      None,
      None,
      "attested",
      None,
      None,
      None,
      None,
      None,
      None,
      Nil,
      Nil,
      Nil,
      "2026-01-01T00:00:00Z"
    )
    val line = BundleFormat.stamp(ev).line
    assert(!line.contains("scale"), line)
    assert(BundleFormat.stamp(ev.copy(scale = Some("regional"))).line.contains("\"scale\":\"regional\""))
    assertNotEquals(BundleFormat.stamp(ev.copy(scale = Some("regional"))).rev, BundleFormat.stamp(ev).rev)
