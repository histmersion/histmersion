package com.histmersion
package web

import com.histmersion.api.catalogue.SiteType

import com.histmersion.testsupport.ComparedByValue.given
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Confidence
import com.histmersion.api.media.MediaRole
import com.histmersion.api.library.GeoHit
import com.histmersion.api.media.MediaThumb
import com.histmersion.api.views.PlaceDetail
import com.histmersion.api.catalogue.Place
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.api.media.MediaItem
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Period
import com.histmersion.lint.{tolerated, LintRule}

/** Every card kind leads with an icon from the one registry (#1449): sources by what they are, places by kind and site
  * type, regions and polities a map, events their kind, articles and pictures theirs. And the map's clusters: what a
  * bubble holds and says. Pure.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class CardIconsSuite extends munit.FunSuite:

  override def beforeAll(): Unit = TestTokens.install()

  private def src(
      doi: Option[Doi] = None,
      venue: Option[Source.Venue] = None,
      provenance: Option[Source.Provenance] = None,
      tags: List[Source.Tags] = Nil
  ) =
    Source.Detail(
      Citekey("zz2020"),
      Title("Zz"),
      (Nil).map(AuthorName.apply),
      (Some(2020)).map(Year.apply),
      venue,
      Source.Status.Current,
      Nil,
      Nil,
      tags,
      None,
      None,
      None,
      doi,
      None,
      provenance = provenance
    )

  test("every icon a card or a marker can name is in the registry"):
    val names = Source.Kinds.all.flatMap(k => List(CardIcons.sourceKind(k), CardIcons.sourceKind(k, marker = true))) ++
      Place.Kind.all.map(k => CardIcons.place(Place.Kind.Wire(k.value), None)) ++ SiteType.all.map(t =>
        CardIcons.place(Place.Kind.Wire(Place.Kind.Site), Some(Place.Entry.SiteType(t.id.value)))
      ) ++
      List(
        Glyphs.Name(CardIcons.Article),
        Glyphs.Name(CardIcons.Media),
        Glyphs.Name(CardIcons.Unknown),
        CardIcons.siteMarker(None)
      )
    assertEquals(names.filterNot(n => Glyphs.inner.contains(n.value)), Nil)

  test(
    "a source's kind: thesis, dataset, book or chapter by ISBN DOI, paper by any other DOI, book by cover or provenance"
  ):
    assertEquals(CardIcons.kindOf(src(tags = List(Source.Tags("PhD-thesis")))).value, Source.Kinds.Thesis)
    assertEquals(
      CardIcons.kindOf(src(venue = Some(Source.Venue("Doctoral dissertation, Uppsala")))).value,
      Source.Kinds.Thesis
    )
    assertEquals(
      CardIcons.kindOf(src(doi = Some(Doi("10.5061/dryad.abc")))).value,
      Source.Kinds.Dataset
    )
    assertEquals(
      CardIcons.kindOf(src(doi = Some(Doi("10.1007/978-3-030-12345-6")))).value,
      Source.Kinds.Book
    )
    assertEquals(
      CardIcons.kindOf(src(doi = Some(Doi("10.1007/978-3-030-12345-6_7")))).value,
      Source.Kinds.Chapter
    )
    assertEquals(
      CardIcons.kindOf(src(doi = Some(Doi("10.1017/CBO9780511607066.004")))).value,
      Source.Kinds.Chapter
    )
    assertEquals(
      CardIcons.kindOf(src(doi = Some(Doi("10.4324/9781315123456")))).value,
      Source.Kinds.Book
    )
    assertEquals(
      CardIcons.kindOf(src(doi = Some(Doi("10.1038/nature14317")))).value,
      Source.Kinds.Paper
    )
    assertEquals(CardIcons.kindOf(src(provenance = Some(Source.Provenance("calibre")))).value, Source.Kinds.Book)
    assertEquals(CardIcons.kindOf(src(), hasCover = true).value, Source.Kinds.Book)
    assertEquals(CardIcons.kindOf(src()).value, Source.Kinds.Unknown)

  test("ISBN DOIs: the book, a part of it, and DOIs that are not books"):
    assertEquals(Source.Kinds.isbnDoi(Doi("10.1007/978-3-030-12345-6")), Some(false))
    assertEquals(Source.Kinds.isbnDoi(Doi("10.4324/9781315123456-5")), Some(true))
    assertEquals(Source.Kinds.isbnDoi(Doi("10.1038/nature14317")), None)
    assertEquals(Source.Kinds.isbnDoi(Doi("10.1000/978123")), None, "too short for an ISBN")

  test("source icons: a card's generic document for an unknown kind, the map's exclamation mark"):
    assertEquals(CardIcons.sourceKind(Source.Kinds.Wire(Source.Kinds.Book)).value, "book")
    assertEquals(CardIcons.sourceKind(Source.Kinds.Wire(Source.Kinds.Unknown)).value, CardIcons.GenericSource)
    assertEquals(
      CardIcons.sourceKind(Source.Kinds.Wire(Source.Kinds.Unknown), marker = true).value,
      CardIcons.Unknown
    )

  test("place icons: a site its type's (a pin untyped), a region or polity a map, a people a group"):
    assertEquals(
      CardIcons.place(Place.Kind.Wire(Place.Kind.Site), Some(Place.Entry.SiteType("fortification"))).value,
      "castle"
    )
    assertEquals(CardIcons.place(Place.Kind.Wire(Place.Kind.Site), None).value, CardIcons.GenericPlace)
    assertEquals(CardIcons.place(Place.Kind.Wire(Place.Kind.Region), None).value, CardIcons.Area)
    assertEquals(CardIcons.place(Place.Kind.Wire(Place.Kind.Polity), None).value, CardIcons.Area)
    assertEquals(CardIcons.place(Place.Kind.Wire(Place.Kind.People), None).value, CardIcons.People)
    assertEquals(
      CardIcons.place(Place.Kind.Wire(Place.Kind.Feature), Some(Place.Entry.SiteType("cave"))).value,
      "door-open"
    )
    assertEquals(CardIcons.siteMarker(None).value, CardIcons.Unknown)

  test("every card kind carries its icon"):
    val s = src(doi = Some(Doi("10.1038/nature14317")))
    assertEquals(Preview.card(s).icon.map(_.value), Some("file-text"))
    val cover = MediaThumb(
      MediaItem.Uid("u"),
      MediaThumb.Role(MediaRole.Cover),
      MediaThumb.Url("/m/u/thumb"),
      MediaItem.Credit("c")
    )
    assertEquals(
      Preview.card(Source.WithFindings(src(), Nil, illustration = Some(cover))).icon.map(_.value),
      Some("book")
    )
    val fort = PlaceDetail(
      Place.Summary(
        Place.Id("zz_f"),
        Place.Summary.Label("Zz fort"),
        Place.Summary.Kind(Place.Kind.Site),
        None,
        None,
        None,
        Place.Summary.Names("Zz fort")
      ),
      None,
      Nil,
      Nil,
      Place.Counts.Findings(0),
      None,
      siteType = Some(Place.Entry.SiteType("fortification"))
    )
    assertEquals(Preview.card(fort).icon.map(_.value), Some("castle"))
    assertEquals(
      Preview
        .card(
          fort.copy(place = fort.place.modify(_.kind).setTo(Place.Summary.Kind(Place.Kind.Polity)), siteType = None)
        )
        .icon
        .map(_.value),
      Some("map")
    )
    val art = Article.View(
      Article.Row(
        Article.Slug("zz"),
        Article.Title("Zz"),
        Article.Kind.Wire("concept"),
        Article.Body(""),
        None,
        Nil,
        Nil,
        None,
        None
      ),
      Nil,
      Nil
    )
    assertEquals(Preview.card(art).icon.map(_.value), Some(CardIcons.Article))

  test("the HTML card icon is the same element as the card view's"):
    assertEquals(
      CardIcons.html(Glyphs.Name("image")),
      s"""<span class="cite-tip-icon">${Glyphs.svg(Glyphs.Name("image"))}</span>"""
    )

  // ── the mixed clusters ─────────────────────────────────────────────────────────────────────────

  private def hit(s: Year, e: Year, c: Confidence = Confidence.High) =
    GeoHit(
      Citekey("zz"),
      GeoHit.Title("Zz"),
      GeoHit.Claim("c"),
      None,
      Some(Longitude(30.0)),
      Some(Latitude(37.0)),
      Some(s),
      Some(e),
      Some(c)
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def dot(k: DotKind, mid: Option[DotEntry.Mid] = None) =
    Member(
      k,
      Member.N(1),
      mid.toList.map(m => Member.Mids(m.value)),
      Certainty.NoSay,
      entry = Some(DotEntry(k, DotEntry.Id(k.toString), DotEntry.Label(k.toString), DotEntry.Icon("x"), mid))
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def finds(hs: GeoHit*) =
    Member(
      DotKind.Finding,
      Member.N(hs.size),
      hs.toList.flatMap(MapView.midOf).map(m => Member.Mids(m.value)),
      Certainty.ofAll(hs.map(_.confidence)),
      findings = hs.toList
    )

  test("a bubble counts every point, a findings' point by its findings, and says what it holds in a fixed order"):
    val c = DotCluster.Contents(
      List(
        finds(hit(Year(-1300), Year(-1200)), hit(Year(-1250), Year(-1250))),
        dot(DotKind.Event),
        dot(DotKind.Site),
        dot(DotKind.Site),
        dot(DotKind.Media)
      )
    )
    assertEquals(c.total.value, 6)
    assertEquals(DotCluster.summary(c).value, "2 findings · 2 sites · 1 event · 1 picture")
    assert(DotCluster.mixed(c))
    assertEquals(c.findings.size, 2)
    assertEquals(c.entries.map(_.kind), List(DotKind.Event, DotKind.Site, DotKind.Site, DotKind.Media))
    assertEquals(DotCluster.summary(DotCluster.Contents(List(dot(DotKind.Site)))).value, "1 site")
    assert(!DotCluster.mixed(DotCluster.Contents(List(finds(hit(Year(1), Year(2)))))))
    assertEquals(DotCluster.summary(Map.empty).value, "")

  test("a bubble's colour spans every dated point, whatever its kind; the undated have no say"):
    val c = DotCluster.Contents(
      List(finds(hit(Year(-1300), Year(-1200))), dot(DotKind.Site, Some(DotEntry.Mid(-1600.0))), dot(DotKind.Event))
    )
    assertEquals(c.mids.map(_.value).sorted, List(-1600.0, -1250.0))
    assertEquals(MapView.rangeOf(c.mids).map((lo, hi) => (lo.value, hi.value)), Some((-1600.0, -1250.0)))
    assert(
      MapView
        .gradientOf(c.mids, (Year.Astronomical(-1700L), Year.Astronomical(-1200L)), c.certainty)
        .value
        .startsWith("linear-gradient(")
    )
    assertEquals(DotCluster.Contents(List(dot(DotKind.Site))).certainty, Certainty.NoSay)
    assertEquals(
      DotCluster.Contents(List(finds(hit(Year(1), Year(2), Confidence.Low)), dot(DotKind.Site))).certainty,
      Certainty.Stated(Confidence.Low)
    )
    assertEquals(
      MapView.gradientOf(Nil, (Year.Astronomical(0L), Year.Astronomical(1L)), Certainty.NoSay),
      MapView.colorFor(None, (Year.Astronomical(0L), Year.Astronomical(1L)), Certainty.NoSay)
    )

  test("a bubble's list: by kind, then by name"):
    val open: org.scalajs.dom.Element => Unit = _ => ()
    val xs = List(
      DotEntry(DotKind.Event, DotEntry.Id("e"), DotEntry.Label("b"), DotEntry.Icon("x"), None),
      DotEntry(DotKind.Site, DotEntry.Id("s2"), DotEntry.Label("Zeta"), DotEntry.Icon("x"), None),
      DotEntry(DotKind.Site, DotEntry.Id("s1"), DotEntry.Label("alpha"), DotEntry.Icon("x"), None),
      DotEntry(DotKind.Media, DotEntry.Id("m"), DotEntry.Label("a"), DotEntry.Icon("x"), None)
    ).map(_ -> open)
    assertEquals(DotCluster.ordered(xs).map(_._1.id.value), List("s1", "s2", "e", "m"))

  test("a dated span's middle"):
    assertEquals(DotCluster.mid(Some(Period.Start(-1300L)), Some(Period.End(-1200L))).map(_.value), Some(-1250.0))
    assertEquals(DotCluster.mid(None, Some(Period.End(1L))), None)

  test("a bubble's markup: its count, its colours as data, and what it holds as its title and accessible name"):
    val h = DotCluster
      .bubbleHtml(
        DotCluster.Count(5),
        DotCluster.Size(24),
        (DotCluster.StartLabel("red"), DotCluster.EndLabel("blue")),
        DotCluster.Id("hdb1"),
        DotCluster.ExtraClass("hd-cluster--mixed"),
        Some(DotCluster.SpokenText("2 findings · 3 sites"))
      )
      .value
    assert(
      h.startsWith(
        """<span class="hd-dot hd-cluster hd-cluster--mixed" style="--d:24px;--fs:12px;--ring:5px;--g0:red;--g1:blue" """ +
          """role="img" title="2 findings · 3 sites" aria-label="2 findings · 3 sites">"""
      ),
      h
    )
    assert(h.endsWith("""<span class="hd-dot-count">5</span></span>"""), h)
    assert(h.contains("""<linearGradient id="hdb1"""") && h.contains("""fill="url(#hdb1)""""), h)
    // a single dot's bubble has no count; without words, no role
    val one = DotCluster
      .bubbleHtml(
        DotCluster.Count(1),
        DotCluster.Size(18),
        (DotCluster.StartLabel("red"), DotCluster.EndLabel("red")),
        DotCluster.Id("k"),
        DotCluster.ExtraClass(""),
        None
      )
      .value
    assert(one.contains("""<span class="hd-dot-count"></span>""") && !one.contains("role="), one)

  // the user's report: "one circle is not ideally in the centre of another" — the disc and its rings are one
  // SVG, every circle about the SAME centre, at every count width
  test("a bubble's disc and rings are concentric, at 1-, 2- and 3-digit counts"):
    val circle = """<circle class="([^"]+)" cx="([^"]+)" cy="([^"]+)" r="([^"]+)"""".r
    for n <- List(3, 42, 120) do
      val d = 2 * (5 + 4 + 3 * n.toString.length)
      val h = DotCluster
        .bubbleHtml(
          DotCluster.Count(n),
          DotCluster.Size(d),
          (DotCluster.StartLabel("red"), DotCluster.EndLabel("blue")),
          DotCluster.Id("g"),
          DotCluster.ExtraClass(""),
          None
        )
        .value
      val cs = circle
        .findAllMatchIn(h)
        .map(m => (m.group(1), m.group(2).toDouble, m.group(3).toDouble, m.group(4).toDouble))
        .toList
      assertEquals(cs.map(_._1), List("hd-bubble-fill", "hd-bubble-line", "hd-bubble-coin", "hd-bubble-line"), h)
      val s = d + 2 * DotCluster.Ring
      assert(h.contains(s"""viewBox="0 0 $s $s""""), h)
      cs.foreach { (_, cx, cy, _) => assertEquals(cx, s / 2.0); assertEquals(cy, s / 2.0) }
      // the disc, a 1-px line, the 3-px coin, a 1-px line: radii out from the disc's edge, the last line
      // ending exactly at the ring's reach
      assertEquals(cs.map(_._4), List(d / 2.0, d / 2.0 + 0.5, d / 2.0 + 2.5, d / 2.0 + 4.5))
      assertEquals(d / 2.0 + 4.5 + 0.5, s / 2.0)

  test("a bubble's outline (#1647): square for events only, tabbed with events among others, round otherwise"):
    assertEquals(DotCluster.form(DotCluster.Contents(List(dot(DotKind.Event), dot(DotKind.Event)))), BubbleForm.Events)
    assertEquals(
      DotCluster.form(DotCluster.Contents(List(dot(DotKind.Event), finds(hit(Year(1), Year(2)))))),
      BubbleForm.WithEvents
    )
    assertEquals(
      DotCluster.form(DotCluster.Contents(List(dot(DotKind.Event), dot(DotKind.Site)))),
      BubbleForm.WithEvents
    )
    assertEquals(
      DotCluster.form(
        DotCluster.Contents(List(finds(hit(Year(1), Year(2)), hit(Year(3), Year(4))), dot(DotKind.Site)))
      ),
      BubbleForm.Round
    )
    assertEquals(DotCluster.form(DotCluster.Contents(Nil)), BubbleForm.Round)

  test("an events bubble is a rounded square, its rings squares about the same centre; a mixed one carries a tab"):
    val rect = """<rect class="([^"]+)" x="([^"]+)" y="([^"]+)" width="([^"]+)" height="([^"]+)" rx="([^"]+)"""".r
    for n <- List(3, 42, 120) do
      val d = 2 * (5 + 4 + 3 * n.toString.length)
      val s = d + 2 * DotCluster.Ring
      val h = DotCluster
        .bubbleHtml(
          DotCluster.Count(n),
          DotCluster.Size(d),
          (DotCluster.StartLabel("red"), DotCluster.EndLabel("blue")),
          DotCluster.Id("g"),
          DotCluster.ExtraClass(""),
          Some(DotCluster.SpokenText(s"$n events")),
          BubbleForm.Events
        )
        .value
      assert(h.startsWith("""<span class="hd-dot hd-cluster hd-cluster--events" """), h)
      assert(!h.contains("<circle"), h)
      val rs = rect.findAllMatchIn(h).map(m => (m.group(1), (2 to 6).map(i => m.group(i).toDouble))).toList
      assertEquals(rs.map(_._1), List("hd-bubble-fill", "hd-bubble-line", "hd-bubble-coin", "hd-bubble-line"), h)
      // every square centred on the box's centre, square, its corner rounding by its distance out
      rs.foreach {
        case (_, Seq(x, y, w, ht, _)) =>
          assertEquals(x + w / 2, s / 2.0); assertEquals(y + ht / 2, s / 2.0); assertEquals(w, ht)
        case other => fail(other.toString)
      }
      assertEquals(rs.map(_._2(2)), List(d.toDouble, d + 1.0, d + 5.0, d + 9.0))
      assertEquals(rs.map(_._2(4)), List(4.0, 4.5, 6.5, 8.5))
      // the outer line ends exactly at the rings' reach, as the round bubble's does
      assertEquals(rs.last._2(0) - 0.5, 0.0)
    val mixed = DotCluster
      .bubbleHtml(
        DotCluster.Count(4),
        DotCluster.Size(24),
        (DotCluster.StartLabel("red"), DotCluster.EndLabel("blue")),
        DotCluster.Id("m"),
        DotCluster.ExtraClass("hd-cluster--mixed"),
        None,
        BubbleForm.WithEvents
      )
      .value
    assert(mixed.startsWith("""<span class="hd-dot hd-cluster hd-cluster--with-events hd-cluster--mixed" """), mixed)
    assertEquals("""<circle """.r.findAllMatchIn(mixed).size, 4, mixed)
    val s = 24 + 2 * DotCluster.Ring
    assert(
      mixed.contains(
        s"""<rect class="hd-bubble-tab" x="${s - DotCluster.Tab - 0.5}" y="0.5" width="${DotCluster.Tab}""""
      ),
      mixed
    )
    // the round bubble is what it was: no tab, no square
    val round = DotCluster
      .bubbleHtml(
        DotCluster.Count(4),
        DotCluster.Size(24),
        (DotCluster.StartLabel("red"), DotCluster.EndLabel("blue")),
        DotCluster.Id("r"),
        DotCluster.ExtraClass(""),
        None
      )
      .value
    assert(!round.contains("<rect") && round.startsWith("""<span class="hd-dot hd-cluster" """), round)

  test("a bubble's colours: the ramp's ends of its dates, the same twice when they agree or none is dated"):
    val span = (Year.Astronomical(-2000L), Year.Astronomical(0L))
    def at(x: Fmt.ColorT) = Fmt.desaturate(Fmt.color(x), Confidence.High)
    val (a, b) = MapView.rampEnds(List(MapView.Mid(-1900.0), MapView.Mid(-100.0)), span, Certainty.NoSay)
    assertEquals(a.value, at(Fmt.ColorT(0.05)).value)
    assertEquals(b.value, at(Fmt.ColorT(0.95)).value)
    val flat = MapView.rampEnds(List(MapView.Mid(-1000.0), MapView.Mid(-1000.0)), span, Certainty.NoSay)
    assertEquals((flat._1.value, flat._2.value), (at(Fmt.ColorT(0.5)).value, at(Fmt.ColorT(0.5)).value))
    val undated = MapView.rampEnds(Nil, span, Certainty.NoSay)
    assertEquals(undated._1, undated._2)
    // the findings' gradient is built from the same ends
    assertEquals(
      MapView.gradientOf(List(MapView.Mid(-1900.0), MapView.Mid(-100.0)), span, Certainty.NoSay).value,
      s"linear-gradient(135deg,$a,$b)"
    )
    assertEquals(MapView.gradientOf(List(MapView.Mid(-1000.0)), span, Certainty.NoSay).value, at(Fmt.ColorT(0.5)).value)

  test("points join a bubble closer than its widest diameter, or than the largest marker box if wider"):
    assertEquals(
      MapView.clusterRadius(
        18,
        List(
          (PointKind.BoxPixels(24), PointKind.BoxPixels(24)),
          (PointKind.BoxPixels(34), PointKind.BoxPixels(34)),
          (PointKind.BoxPixels(32), PointKind.BoxPixels(24))
        )
      ),
      36
    )
    assertEquals(
      MapView.clusterRadius(
        10,
        List((PointKind.BoxPixels(24), PointKind.BoxPixels(24)), (PointKind.BoxPixels(34), PointKind.BoxPixels(34)))
      ),
      34
    )
    assertEquals(MapView.clusterRadius(18, Nil), 36)
    // the map's own markers: none is wider than the bubbles' reach, so two drawn apart never overlap
    assert(
      PointKinds.all.forall(k =>
        k.box._1.value <= MapView.clusterRadius(18, Nil) && k.box._2.value <= MapView.clusterRadius(18, Nil)
      )
    )
