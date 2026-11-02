package com.histmersion.web

import com.histmersion.api.TimeSpan
import com.histmersion.domain.library.Confidence
import com.histmersion.api.catalogue.EventGeo
import com.histmersion.api.library.GeoHit
import com.histmersion.api.media.MediaGeoHit
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.library.Source

/** The four kinds of point are four descriptors of ONE layer (docs/map-layers.md): the same assertions run over every
  * one of them, so they cannot drift apart — the marker's shape, glyph, fill and accessible name, what a bubble counts,
  * its row in a bubble's list. Pure.
  */
class PointKindsSuite extends munit.FunSuite:

  override def beforeAll(): Unit = TestTokens.install()

  private val span = (-1700L, -1200L)

  /** One sample of each kind, of a KNOWN type, and one of an unknown type where a kind has types. */
  final private case class Sample[A](kind: PointKind[A], known: A, unknown: Option[A])

  private val finding = GeoHit(
    "zz2020_x",
    "Zz paper",
    "a claim",
    None,
    Some(31.0),
    Some(37.0),
    Some(-1300L),
    Some(-1250L),
    Some(Confidence.High),
    sourceKind = Some(Source.Kinds.Paper)
  )
  private val site = SitePoint(
    Some("zz_fort"),
    "Zz fort",
    Place.Kind.Site,
    Some("fortification"),
    None,
    37.5,
    32.0,
    Some((-1400.0, -1300.0))
  )
  private val event = EventGeo(
    "zz_b",
    "Zz battle",
    "battle",
    "local",
    "attested",
    TimeSpan(Some(-1274), None, None, Some(-1274)),
    37.5,
    32,
    "event"
  )
  private val picture = MediaGeoHit(None, "zz.jpg", Some("photo"), 37.0, 22.0, Some(-1500L), Some(-1400L), None, "u1")

  private val samples: List[Sample[?]] = List(
    Sample(
      PointKinds.Findings,
      Spot(37.0, 31.0, List(finding)),
      Some(Spot(37.0, 31.0, List(finding.copy(sourceKind = None))))
    ),
    Sample(PointKinds.Sites, site, Some(site.copy(siteType = None))),
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
      u.foreach(a => assert(k.inner(a) == Glyphs.badge(CardIcons.Unknown), s"${k.dot}: ${k.inner(a)}"))
    }
    samples.foreach { case Sample(k, a, _) => assert(k.inner(a) != Glyphs.badge(CardIcons.Unknown), s"${k.dot} known") }

  test("the colour lives in --c alone: a span change re-colours in place, it never re-draws"):
    samples.foreach { case Sample(k, a, _) =>
      assertEquals(PointMarker.signature(k, a), PointMarker.signature(k, a))
      assert(!PointMarker.signature(k, a).contains(k.fill(a, span)), s"${k.dot}")
      assert(k.fill(a, span).nonEmpty)
    }

  test("the accessible name says what it is and what activating it does; no internal ids"):
    samples.foreach { case Sample(k, a, _) =>
      val l = PointMarker.ariaLabel(k, a)
      assert(l.endsWith(s" — ${k.action(a)}") && k.label(a).nonEmpty, s"${k.dot}: $l")
    }
    assert(!PointMarker.ariaLabel(PointKinds.Findings, samples.head.known.asInstanceOf[Spot]).contains("zz2020_x"))

  test("what a bubble counts: one per point, a findings' point by its findings; every row but a finding's has an icon"):
    samples.foreach { case Sample(k, a, _) =>
      val m = k.member(a)
      assertEquals(m.kind, k.dot)
      assertEquals(m.n, 1)
      assert(m.mids.nonEmpty, s"${k.dot} is dated")
      if k.dot == DotKind.Finding then assertEquals(m.findings.size, 1)
      else assert(m.entry.exists(e => e.kind == k.dot && Glyphs.inner.contains(e.icon)), s"${k.dot}: ${m.entry}")
    }
    val two = Spot(1, 2, List(finding, finding.copy(citekey = "zz2021_y")))
    assertEquals(PointKinds.Findings.member(two).n, 2)
    assertEquals(PointKinds.Findings.inner(two), """<span class="hd-find-count">2</span>""")
    assertEquals(PointKinds.Findings.title(two), Some("2 findings here"))
    assertEquals(PointKinds.Findings.target(two, None), None)

  test("the card a hover shows: the source, the place at the map's year, the event, a figure's source"):
    assertEquals(PointKinds.Findings.target(Spot(1, 2, List(finding)), None), Some(Preview.Target.Source("zz2020_x")))
    assertEquals(PointKinds.Sites.target(site, Some(-1300L)), Some(Preview.Target.Place("zz_fort", Some(-1300L))))
    assertEquals(PointKinds.Events.target(event, None), Some(Preview.Target.Event("zz_b")))
    assertEquals(PointKinds.Media.target(picture, None), None)
    assertEquals(
      PointKinds.Media.target(picture.copy(citekey = Some("zz2019")), None),
      Some(Preview.Target.Source("zz2019"))
    )

  test("the client-side filter: the site-type menu keeps its type; every other kind is filtered by the server"):
    assert(PointKinds.Sites.keeps(site, None))
    assert(PointKinds.Sites.keeps(site, Some("fortification")))
    assert(!PointKinds.Sites.keeps(site, Some("burial")))

  test("equal optical size: the diamond's box is the largest, the photo landscape"):
    val (fw, _) = PointKinds.Findings.box
    assert(fw > PointKinds.Sites.box._1 && fw > PointKinds.Events.box._1)
    val (pw, ph) = PointKinds.Media.box
    assert(pw > ph)

  test("findings at one point are one point; the spots keep their order"):
    val a = finding.copy(lat = Some(1), lon = Some(2))
    val b = finding.copy(citekey = "b", lat = Some(3), lon = Some(4))
    val spots = Spot.of(List(a, b, a.copy(citekey = "c"), finding.copy(lat = None)))
    assertEquals(spots.map(s => (s.lat, s.lon, s.items.size)), List((1.0, 2.0, 2), (3.0, 4.0, 1)))
