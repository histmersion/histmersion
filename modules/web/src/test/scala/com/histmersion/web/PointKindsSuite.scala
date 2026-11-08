package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.TimeSpan
import com.histmersion.api.library.Confidence
import com.histmersion.api.catalogue.EventGeo
import com.histmersion.api.library.GeoHit
import com.histmersion.api.media.MediaGeoHit
import com.histmersion.api.catalogue.Place
import com.histmersion.api.library.Source

/** The four kinds of point are four descriptors of ONE layer (docs/map-layers.md): the same assertions run over every
  * one of them, so they cannot drift apart — the marker's shape, glyph, fill and accessible name, what a bubble counts,
  * its row in a bubble's list. Pure.
  */
class PointKindsSuite extends munit.FunSuite:

  override def beforeAll(): Unit = TestTokens.install()

  private val span = (com.histmersion.api.Years.Astronomical(-1700L), com.histmersion.api.Years.Astronomical(-1200L))

  /** One sample of each kind, of a KNOWN type, and one of an unknown type where a kind has types. */
  final private case class Sample[A](kind: PointKind[A], known: A, unknown: Option[A])

  private val finding = GeoHit(
    com.histmersion.api.library.Citekey("zz2020_x"),
    com.histmersion.api.library.GeoHit.Title("Zz paper"),
    com.histmersion.api.library.GeoHit.Claim("a claim"),
    None,
    Some(com.histmersion.api.catalogue.Longitude(31.0)),
    Some(com.histmersion.api.catalogue.Latitude(37.0)),
    Some(com.histmersion.api.catalogue.Year(-1300L)),
    Some(com.histmersion.api.catalogue.Year(-1250L)),
    Some(Confidence.High),
    sourceKind = Some(GeoHit.SourceKind(Source.Kinds.Paper))
  )
  private val site = SitePoint(
    Some(com.histmersion.web.SitePoint.Id("zz_fort")),
    com.histmersion.web.SitePoint.Label("Zz fort"),
    SitePoint.Kind(Place.Kind.Site),
    Some(com.histmersion.web.SitePoint.SiteType("fortification")),
    None,
    com.histmersion.web.map.LatLon.Lat(37.5),
    com.histmersion.web.map.LatLon.Lon(32.0),
    Some((PlaybackAreas.FractionalYear(-1400.0), PlaybackAreas.FractionalYear(-1300.0)))
  )
  private val event = EventGeo(
    com.histmersion.api.catalogue.Event.Id("zz_b"),
    com.histmersion.api.catalogue.EventGeo.Label("Zz battle"),
    com.histmersion.api.catalogue.EventGeo.Kind("battle"),
    com.histmersion.api.catalogue.EventGeo.Scale("local"),
    com.histmersion.api.catalogue.EventGeo.Historicity("attested"),
    TimeSpan(Some(com.histmersion.api.catalogue.Year(-1274)), None, None, Some(com.histmersion.api.catalogue.Year(-1274))),
    com.histmersion.api.catalogue.Latitude(37.5),
    com.histmersion.api.catalogue.Longitude(32),
    com.histmersion.api.catalogue.EventGeo.Anchor("event")
  )
  private val picture = MediaGeoHit(None, com.histmersion.api.exchange.Bundle.Asset.FileName("zz.jpg"), Some(com.histmersion.api.media.MediaItem.Kind("photo")), com.histmersion.api.catalogue.Latitude(37.0), com.histmersion.api.catalogue.Longitude(22.0), Some(com.histmersion.api.catalogue.Year(-1500L)), Some(com.histmersion.api.catalogue.Year(-1400L)), None, com.histmersion.api.media.MediaItem.Uid("u1"))

  private val samples: List[Sample[?]] = List(
    Sample(
      PointKinds.Findings,
      Spot(com.histmersion.api.catalogue.Latitude(37.0), com.histmersion.api.catalogue.Longitude(31.0), List(finding)),
      Some(Spot(com.histmersion.api.catalogue.Latitude(37.0), com.histmersion.api.catalogue.Longitude(31.0), List(finding.modify(_.sourceKind).setTo(None))))
    ),
    Sample(PointKinds.Sites, site, Some(site.modify(_.siteType).setTo(None))),
    Sample(PointKinds.Events, event, None),
    Sample(PointKinds.Media, picture, None)
  )

  test("every kind is listed once, each with its own shape and cluster noun"):
    assertEquals(PointKinds.all.map(_.dot), DotKind.values.toList)
    assertEquals(PointKinds.all.map(_.shape).distinct.size, PointKinds.all.size)
    assertEquals(samples.map(_.kind), PointKinds.all)

  test("the marker: the kind's shape, a white-on-halo glyph, the fill as --c — the same markup for every kind"):
    samples.foreach { case Sample(k, a, _) =>
      val h = PointMarker.html(k, a, "rgb(1,2,3)")
      assert(h.startsWith(s"""<span class="hd-point hd-badge hd-badge--${k.shape.cls} """), s"${k.dot}: $h")
      assert(h.contains("--c:rgb(1,2,3)") && h.contains("glyph--badge") && h.contains("glyph-halo"), s"${k.dot}: $h")
    }

  test("an unknown type is its kind's shape with the exclamation mark, never a bare dot"):
    samples.foreach { case Sample(k, _, u) =>
      u.foreach(a => assert(k.inner(a) == Glyphs.badge(Glyphs.BadgeName(CardIcons.Unknown)).value, s"${k.dot}: ${k.inner(a)}"))
    }
    samples.foreach { case Sample(k, a, _) => assert(k.inner(a) != Glyphs.badge(Glyphs.BadgeName(CardIcons.Unknown)).value, s"${k.dot} known") }

  test("the colour lives in --c alone: a span change re-colours in place, it never re-draws"):
    samples.foreach { case Sample(k, a, _) =>
      assertEquals(PointMarker.signature(k, a), PointMarker.signature(k, a))
      assert(!PointMarker.signature(k, a).contains(k.fill(a, span)), s"${k.dot}")
      assert(k.fill(a, span).nonEmpty)
    }

  test("the accessible name says what it is and what activating it does; no internal ids"):
    samples.foreach { case Sample(k, a, _) =>
      val l = PointMarker.ariaLabel(k, a)
      assert(l.value.endsWith(s" — ${k.action(a).value}") && k.label(a).value.nonEmpty, s"${k.dot}: ${l.value}")
    }
    assert(!PointMarker.ariaLabel(PointKinds.Findings, samples.head.known.asInstanceOf[Spot]).value.contains("zz2020_x"))

  test("what a bubble counts: one per point, a findings' point by its findings; every row but a finding's has an icon"):
    samples.foreach { case Sample(k, a, _) =>
      val m = k.member(a)
      assertEquals(m.kind, k.dot)
      assertEquals(m.n, 1)
      assert(m.mids.nonEmpty, s"${k.dot} is dated")
      if k.dot == DotKind.Finding then assertEquals(m.findings.size, 1)
      else assert(m.entry.exists(e => e.kind == k.dot && Glyphs.svg(Glyphs.Name(e.icon.value)).value.nonEmpty),s"${k.dot}: ${m.entry}")
    }
    val two = Spot(com.histmersion.api.catalogue.Latitude(1), com.histmersion.api.catalogue.Longitude(2), List(finding, finding.modify(_.citekey).setTo(com.histmersion.api.library.Citekey("zz2021_y"))))
    assertEquals(PointKinds.Findings.member(two).n, 2)
    assertEquals(PointKinds.Findings.inner(two), """<span class="hd-find-count">2</span>""")
    assertEquals(PointKinds.Findings.title(two), Some("2 findings here"))
    assertEquals(PointKinds.Findings.target(two, None), None)

  test("the card a hover shows: the source, the place at the map's year, the event, a figure's source"):
    assertEquals(PointKinds.Findings.target(Spot(com.histmersion.api.catalogue.Latitude(1), com.histmersion.api.catalogue.Longitude(2), List(finding)), None), Some(Preview.Target.Source(com.histmersion.web.Preview.Target.Source.Citekey("zz2020_x"))))
    assertEquals(PointKinds.Sites.target(site, Some(com.histmersion.api.Years.Astronomical(-1300L))), Some(Preview.Target.Place(com.histmersion.web.Preview.Target.Place.Id("zz_fort"), Some(com.histmersion.api.Years.Astronomical(-1300L)))))
    assertEquals(PointKinds.Events.target(event, None), Some(Preview.Target.Event(com.histmersion.web.Preview.Target.Event.Id("zz_b"))))
    assertEquals(PointKinds.Media.target(picture, None), None)
    assertEquals(
      PointKinds.Media.target(picture.modify(_.citekey).setTo(Some(com.histmersion.api.library.Citekey("zz2019"))), None),
      Some(Preview.Target.Source(com.histmersion.web.Preview.Target.Source.Citekey("zz2019")))
    )

  test("the client-side filter: the site-type menu keeps its type; every other kind is filtered by the server"):
    assert(PointKinds.Sites.keeps(site, None))
    assert(PointKinds.Sites.keeps(site, Some(com.histmersion.api.catalogue.SiteTile.SiteType("fortification"))))
    assert(!PointKinds.Sites.keeps(site, Some(com.histmersion.api.catalogue.SiteTile.SiteType("burial"))))

  test("equal optical size: the diamond's box is the largest, the photo landscape"):
    val (fw, _) = PointKinds.Findings.box
    assert(fw.value > PointKinds.Sites.box._1.value && fw.value > PointKinds.Events.box._1.value)
    val (pw, ph) = PointKinds.Media.box
    assert(pw.value > ph.value)

  test("findings at one point are one point; the spots keep their order"):
    val a = finding.modify(_.lat).setTo(Some(com.histmersion.api.catalogue.Latitude(1))).modify(_.lon).setTo(Some(com.histmersion.api.catalogue.Longitude(2)))
    val b = finding.modify(_.citekey).setTo(com.histmersion.api.library.Citekey("b")).modify(_.lat).setTo(Some(com.histmersion.api.catalogue.Latitude(3))).modify(_.lon).setTo(Some(com.histmersion.api.catalogue.Longitude(4)))
    val spots = Spot.of(List(a, b, a.modify(_.citekey).setTo(com.histmersion.api.library.Citekey("c")), finding.modify(_.lat).setTo(None)))
    assertEquals(spots.map(s => (s.lat, s.lon, s.items.size)), List((1.0, 2.0, 2), (3.0, 4.0, 1)))
