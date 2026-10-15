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
    assertEquals(Preview.card(s).icon, Some("newspaper"))
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
    Member(k, 1, mid.toList, Confidence.High, entry = Some(DotEntry(k, k.toString, k.toString, "x", mid)))
  private def finds(hs: GeoHit*) =
    Member(DotKind.Finding, hs.size, hs.toList.flatMap(MapView.midOf),
      Fmt.worstConf(hs.map(_.confidence.getOrElse(Confidence.High))), findings = hs.toList)

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
    assert(MapView.gradientOf(c.mids, (-1700L, -1200L), c.confidence).startsWith("linear-gradient("))
    assertEquals(DotCluster.Contents(List(dot(DotKind.Site))).confidence, Confidence.High)
    assertEquals(DotCluster.Contents(List(finds(hit(1, 2, Confidence.Low)), dot(DotKind.Site))).confidence, Confidence.Low)
    assertEquals(MapView.gradientOf(Nil, (0L, 1L), Confidence.High), MapView.colorFor(None, (0L, 1L), Confidence.High))

  test("a bubble's list: by kind, then by name"):
    val open: org.scalajs.dom.Element => Unit = _ => ()
    val xs = List(DotEntry(DotKind.Event, "e", "b", "x", None), DotEntry(DotKind.Site, "s2", "Zeta", "x", None),
      DotEntry(DotKind.Site, "s1", "alpha", "x", None), DotEntry(DotKind.Media, "m", "a", "x", None)).map(_ -> open)
    assertEquals(DotCluster.ordered(xs).map(_._1.id), List("s1", "s2", "e", "m"))

  test("a dated span's middle"):
    assertEquals(DotCluster.mid(Some(-1300L), Some(-1200L)), Some(-1250.0))
    assertEquals(DotCluster.mid(None, Some(1L)), None)
