package com.histmersion.web

import com.histmersion.core.*

/** Every card kind leads with an icon from the one registry (#1449): sources by what they are, places
  * by kind and site type, regions and polities a map, events their kind, articles and pictures theirs.
  * And the map's clusters: what a bubble holds and says. Pure. */
class CardIconsSuite extends munit.FunSuite:

  override def beforeAll(): Unit = TestTokens.install()

  private def src(doi: Option[String] = None, venue: Option[String] = None, provenance: Option[String] = None,
      tags: List[String] = Nil) =
    SourceDetail("zz2020", "Zz", Nil, Some(2020), venue, SourceStatus.Current, Nil, Nil, tags, None, None, None, doi, None,
      provenance = provenance)

  test("every icon a card or a marker can name is in the registry"):
    val names = SourceKinds.all.flatMap(k => List(CardIcons.sourceKind(k), CardIcons.sourceKind(k, marker = true))) ++
      PlaceKind.all.map(CardIcons.place(_, None)) ++ SiteTypes.all.map(t => CardIcons.place(PlaceKind.Site, Some(t.id))) ++
      List(CardIcons.Article, CardIcons.Media, CardIcons.Unknown, CardIcons.siteMarker(None))
    assertEquals(names.filterNot(Glyphs.inner.contains), Nil)

  test("a source's kind: thesis, dataset, book or chapter by ISBN DOI, paper by any other DOI, book by cover or provenance"):
    assertEquals(CardIcons.kindOf(src(tags = List("PhD-thesis"))), SourceKinds.Thesis)
    assertEquals(CardIcons.kindOf(src(venue = Some("Doctoral dissertation, Uppsala"))), SourceKinds.Thesis)
    assertEquals(CardIcons.kindOf(src(doi = Some("10.5061/dryad.abc"))), SourceKinds.Dataset)
    assertEquals(CardIcons.kindOf(src(doi = Some("10.1007/978-3-030-12345-6"))), SourceKinds.Book)
    assertEquals(CardIcons.kindOf(src(doi = Some("10.1007/978-3-030-12345-6_7"))), SourceKinds.Chapter)
    assertEquals(CardIcons.kindOf(src(doi = Some("10.1017/CBO9780511607066.004"))), SourceKinds.Chapter)
    assertEquals(CardIcons.kindOf(src(doi = Some("10.4324/9781315123456"))), SourceKinds.Book)
    assertEquals(CardIcons.kindOf(src(doi = Some("10.1038/nature14317"))), SourceKinds.Paper)
    assertEquals(CardIcons.kindOf(src(provenance = Some("calibre"))), SourceKinds.Book)
    assertEquals(CardIcons.kindOf(src(), hasCover = true), SourceKinds.Book)
    assertEquals(CardIcons.kindOf(src()), SourceKinds.Unknown)

  test("ISBN DOIs: the book, a part of it, and DOIs that are not books"):
    assertEquals(SourceKinds.isbnDoi("10.1007/978-3-030-12345-6"), Some(false))
    assertEquals(SourceKinds.isbnDoi("10.4324/9781315123456-5"), Some(true))
    assertEquals(SourceKinds.isbnDoi("10.1038/nature14317"), None)
    assertEquals(SourceKinds.isbnDoi("10.1000/978123"), None, "too short for an ISBN")

  test("source icons: a card's generic document for an unknown kind, the map's exclamation mark"):
    assertEquals(CardIcons.sourceKind(SourceKinds.Book), "book")
    assertEquals(CardIcons.sourceKind(SourceKinds.Unknown), CardIcons.GenericSource)
    assertEquals(CardIcons.sourceKind(SourceKinds.Unknown, marker = true), CardIcons.Unknown)

  test("place icons: a site its type's (a pin untyped), a region or polity a map, a people a group"):
    assertEquals(CardIcons.place(PlaceKind.Site, Some("fortification")), "castle")
    assertEquals(CardIcons.place(PlaceKind.Site, None), CardIcons.GenericPlace)
    assertEquals(CardIcons.place(PlaceKind.Region, None), CardIcons.Area)
    assertEquals(CardIcons.place(PlaceKind.Polity, None), CardIcons.Area)
    assertEquals(CardIcons.place(PlaceKind.People, None), CardIcons.People)
    assertEquals(CardIcons.place(PlaceKind.Feature, Some("cave")), "door-open")
    assertEquals(CardIcons.siteMarker(None), CardIcons.Unknown)

  test("every card kind carries its icon"):
    val s = src(doi = Some("10.1038/nature14317"))
    assertEquals(Preview.card(s).icon, Some("file-text"))
    val cover = MediaThumb("u", MediaRole.Cover, "/m/u/thumb", "c")
    assertEquals(Preview.card(SourceWithFindings(src(), Nil, illustration = Some(cover))).icon, Some("book"))
    val fort = PlaceDetail(PlaceSummary("zz_f", "Zz fort", PlaceKind.Site, None, None, None, "Zz fort"),
      None, Nil, Nil, 0, None, siteType = Some("fortification"))
    assertEquals(Preview.card(fort).icon, Some("castle"))
    assertEquals(Preview.card(fort.copy(place = fort.place.copy(kind = PlaceKind.Polity), siteType = None)).icon, Some("map"))
    val art = ArticleView(ArticleRow("zz", "Zz", "concept", "", None, Nil, Nil, None, None), Nil, Nil)
    assertEquals(Preview.card(art).icon, Some(CardIcons.Article))

  test("the HTML card icon is the same element as the card view's"):
    assertEquals(CardIcons.html("image"), s"""<span class="cite-tip-icon">${Glyphs.svg("image")}</span>""")

  // ── the mixed clusters ─────────────────────────────────────────────────────────────────────────

  private def hit(s: Long, e: Long, c: Confidence = Confidence.High) =
    GeoHit("zz", "Zz", "c", None, Some(30.0), Some(37.0), Some(s), Some(e), Some(c))
  private def dot(k: DotKind, mid: Option[Double] = None) =
    Member(k, 1, mid.toList, Certainty.NoSay, entry = Some(DotEntry(k, k.toString, k.toString, "x", mid)))
  private def finds(hs: GeoHit*) =
    Member(DotKind.Finding, hs.size, hs.toList.flatMap(MapView.midOf),
      Certainty.ofAll(hs.map(_.confidence)), findings = hs.toList)

  test("a bubble counts every point, a findings' point by its findings, and says what it holds in a fixed order"):
    val c = DotCluster.Contents(List(finds(hit(-1300, -1200), hit(-1250, -1250)),
      dot(DotKind.Event), dot(DotKind.Site), dot(DotKind.Site), dot(DotKind.Media)))
    assertEquals(c.total, 6)
    assertEquals(DotCluster.summary(c), "2 findings · 2 sites · 1 event · 1 picture")
    assert(DotCluster.mixed(c))
    assertEquals(c.findings.size, 2)
    assertEquals(c.entries.map(_.kind), List(DotKind.Event, DotKind.Site, DotKind.Site, DotKind.Media))
    assertEquals(DotCluster.summary(DotCluster.Contents(List(dot(DotKind.Site)))), "1 site")
    assert(!DotCluster.mixed(DotCluster.Contents(List(finds(hit(1, 2))))))
    assertEquals(DotCluster.summary(Map.empty), "")

  test("a bubble's colour spans every dated point, whatever its kind; the undated have no say"):
    val c = DotCluster.Contents(List(finds(hit(-1300, -1200)), dot(DotKind.Site, Some(-1600.0)), dot(DotKind.Event)))
    assertEquals(c.mids.sorted, List(-1600.0, -1250.0))
    assertEquals(MapView.rangeOf(c.mids), Some((-1600.0, -1250.0)))
    assert(MapView.gradientOf(c.mids, (-1700L, -1200L), c.certainty).startsWith("linear-gradient("))
    assertEquals(DotCluster.Contents(List(dot(DotKind.Site))).certainty, Certainty.NoSay)
    assertEquals(DotCluster.Contents(List(finds(hit(1, 2, Confidence.Low)), dot(DotKind.Site))).certainty, Certainty.Stated(Confidence.Low))
    assertEquals(MapView.gradientOf(Nil, (0L, 1L), Certainty.NoSay), MapView.colorFor(None, (0L, 1L), Certainty.NoSay))

  test("a bubble's list: by kind, then by name"):
    val open: org.scalajs.dom.Element => Unit = _ => ()
    val xs = List(DotEntry(DotKind.Event, "e", "b", "x", None), DotEntry(DotKind.Site, "s2", "Zeta", "x", None),
      DotEntry(DotKind.Site, "s1", "alpha", "x", None), DotEntry(DotKind.Media, "m", "a", "x", None)).map(_ -> open)
    assertEquals(DotCluster.ordered(xs).map(_._1.id), List("s1", "s2", "e", "m"))

  test("a dated span's middle"):
    assertEquals(DotCluster.mid(Some(-1300L), Some(-1200L)), Some(-1250.0))
    assertEquals(DotCluster.mid(None, Some(1L)), None)

  test("a bubble's markup: its count, its colours as data, and what it holds as its title and accessible name"):
    val h = DotCluster.bubbleHtml(5, 24, ("red", "blue"), "hdb1", "hd-cluster--mixed", Some("2 findings · 3 sites"))
    assert(h.startsWith("""<span class="hd-dot hd-cluster hd-cluster--mixed" style="--d:24px;--fs:12px;--ring:5px;--g0:red;--g1:blue" """ +
      """role="img" title="2 findings · 3 sites" aria-label="2 findings · 3 sites">"""), h)
    assert(h.endsWith("""<span class="hd-dot-count">5</span></span>"""), h)
    assert(h.contains("""<linearGradient id="hdb1"""") && h.contains("""fill="url(#hdb1)""""), h)
    // a single dot's bubble has no count; without words, no role
    val one = DotCluster.bubbleHtml(1, 18, ("red", "red"), "k", "", None)
    assert(one.contains("""<span class="hd-dot-count"></span>""") && !one.contains("role="), one)

  // the user's report: "one circle is not ideally in the centre of another" — the disc and its rings are one
  // SVG, every circle about the SAME centre, at every count width
  test("a bubble's disc and rings are concentric, at 1-, 2- and 3-digit counts"):
    val circle = """<circle class="([^"]+)" cx="([^"]+)" cy="([^"]+)" r="([^"]+)"""".r
    for n <- List(3, 42, 120) do
      val d = 2 * (5 + 4 + 3 * n.toString.length)
      val h = DotCluster.bubbleHtml(n, d, ("red", "blue"), "g", "", None)
      val cs = circle.findAllMatchIn(h).map(m => (m.group(1), m.group(2).toDouble, m.group(3).toDouble, m.group(4).toDouble)).toList
      assertEquals(cs.map(_._1), List("hd-bubble-fill", "hd-bubble-line", "hd-bubble-coin", "hd-bubble-line"), h)
      val s = d + 2 * DotCluster.Ring
      assert(h.contains(s"""viewBox="0 0 $s $s""""), h)
      cs.foreach((_, cx, cy, _) => { assertEquals(cx, s / 2.0); assertEquals(cy, s / 2.0) })
      // the disc, a 1-px line, the 3-px coin, a 1-px line: radii out from the disc's edge, the last line
      // ending exactly at the ring's reach
      assertEquals(cs.map(_._4), List(d / 2.0, d / 2.0 + 0.5, d / 2.0 + 2.5, d / 2.0 + 4.5))
      assertEquals(d / 2.0 + 4.5 + 0.5, s / 2.0)

  test("a bubble's colours: the ramp's ends of its dates, the same twice when they agree or none is dated"):
    val span = (-2000L, 0L)
    def at(x: Double) = Fmt.desaturate(Fmt.color(x), Confidence.High)
    val (a, b) = MapView.rampEnds(List(-1900.0, -100.0), span, Certainty.NoSay)
    assertEquals(a, at(0.05))
    assertEquals(b, at(0.95))
    assertEquals(MapView.rampEnds(List(-1000.0, -1000.0), span, Certainty.NoSay), (at(0.5), at(0.5)))
    val undated = MapView.rampEnds(Nil, span, Certainty.NoSay)
    assertEquals(undated._1, undated._2)
    // the findings' gradient is built from the same ends
    assertEquals(MapView.gradientOf(List(-1900.0, -100.0), span, Certainty.NoSay), s"linear-gradient(135deg,$a,$b)")
    assertEquals(MapView.gradientOf(List(-1000.0), span, Certainty.NoSay), at(0.5))

  test("points join a bubble closer than its widest diameter, or than the largest marker box if wider"):
    assertEquals(MapView.clusterRadius(18, List((24, 24), (34, 34), (32, 24))), 36)
    assertEquals(MapView.clusterRadius(10, List((24, 24), (34, 34))), 34)
    assertEquals(MapView.clusterRadius(18, Nil), 36)
    // the map's own markers: none is wider than the bubbles' reach, so two drawn apart never overlap
    assert(PointKinds.all.forall(k => k.box._1 <= MapView.clusterRadius(18, Nil) && k.box._2 <= MapView.clusterRadius(18, Nil)))

