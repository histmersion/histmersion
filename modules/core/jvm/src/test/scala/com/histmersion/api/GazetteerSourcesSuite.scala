package com.histmersion.api

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given


import io.circe.parser.parse

/** The external gazetteer sources' pure half (#1064), on the JVM (Lpf's year parser uses a look-behind
  * Scala.js cannot compile): identifiers, open time spans, the LPF and LP-TSV
  * readers, name merging, and which place a record joins. */
class GazetteerSourcesSuite extends munit.FunSuite:

  private def json(s: String) = parse(s).fold(e => fail(e.getMessage), identity)

  test("ExternalId: prefixed ids and authority URIs, normalised; anything else is no identity"):
    assertEquals(ExternalId.parse("wd:q42"), Some(ExternalId(LinkScheme.Wikidata, "Q42")))
    assertEquals(ExternalId.parse("http://www.wikidata.org/entity/Q5684"), Some(ExternalId(LinkScheme.Wikidata, "Q5684")))
    assertEquals(ExternalId.parse("https://www.wikidata.org/wiki/Q5684"), Some(ExternalId(LinkScheme.Wikidata, "Q5684")))
    assertEquals(ExternalId.parse("gn:750539"), Some(ExternalId(LinkScheme.Geonames, "750539")))
    assertEquals(ExternalId.parse("https://sws.geonames.org/750539/"), Some(ExternalId(LinkScheme.Geonames, "750539")))
    assertEquals(ExternalId.parse("pl:579885"), Some(ExternalId(LinkScheme.Pleiades, "579885")))
    assertEquals(ExternalId.parse("https://pleiades.stoa.org/places/579885"), Some(ExternalId(LinkScheme.Pleiades, "579885")))
    assertEquals(ExternalId.parse("tgn:7011944"), Some(ExternalId(LinkScheme.Tgn, "7011944")))
    assertEquals(ExternalId.parse("http://vocab.getty.edu/page/tgn/7011944"), Some(ExternalId(LinkScheme.Tgn, "7011944")))
    assertEquals(ExternalId.parse("viaf:12345"), None)
    assertEquals(ExternalId.parse("wd:Paris"), None)
    assertEquals(ExternalId.parse("my-dataset-17"), None)
    assertEquals(ExternalId(LinkScheme.Wikidata, "Q42").placeId, "wikidata_q42")

  test("span: an open side stays open; several timespans give the widest; a bare year is that year"):
    assertEquals(Gazetteer.span(json("""{"timespans":[{"end":{"in":"1935"}}]}""")), Span(None, Some(1935L)))
    assertEquals(Gazetteer.span(json("""{"timespans":[{"start":{"in":"-1650"},"end":{"in":"-1180"}},
                                       {"start":{"earliest":"-1700"},"end":{"latest":"-1100"}}]}""")),
      Span(Some(-1700L), Some(-1100L)))
    assertEquals(Gazetteer.span(json("""{"timespans":[{"start":{"in":"330"}},{"start":{"in":"-660"},"end":{"in":"330"}}]}""")),
      Span(Some(-660L), None))
    assertEquals(Gazetteer.span(json("-500")), Span(Some(-500L), Some(-500L)))
    assertEquals(Gazetteer.span(json("{}")), Span.always)

  test("mergeNames: one name per (name, lang); a dated entry beats an undated repeat; citations kept"):
    val ns = Gazetteer.mergeNames(List(
      GazName("Mikoyan", "en"), GazName("Mikoyan", "en", Span(Some(1935), Some(1956))),
      GazName("Smyrna", "la", Span(Some(-300), Some(100)), Some("Barrington 56")),
      GazName("Smyrna", "la", Span(Some(-600), None), Some("Strabo")),
      GazName(" ", "en")))
    assertEquals(ns, List(
      GazName("Mikoyan", "en", Span(Some(1935), Some(1956))),
      GazName("Smyrna", "la", Span(Some(-600), None), Some("Barrington 56; Strabo"))))

  test("fromFeature: identity, links, dated names with citations, kind and the place id"):
    val f = json("""{"@id":"http://whgazetteer.org/api/db/?id=99","type":"Feature",
      "properties":{"title":"Hattusa","kind":"site"},
      "when":{"timespans":[{"start":{"in":"-1650"},"end":{"in":"-1180"}}]},
      "names":[{"toponym":"Ḫattuša","lang":"hit","when":{"timespans":[{"start":{"in":"-1650"},"end":{"in":"-1180"}}]},
                "citations":[{"label":"Bryce 2005","year":2005}]},
               {"toponym":"Boğazköy","lang":"tr"}],
      "links":[{"type":"closeMatch","identifier":"wd:Q193236"},{"type":"exactMatch","identifier":"pl:638753"},
               {"type":"primaryTopicOf","identifier":"https://en.wikipedia.org/wiki/Hattusa"}],
      "geometry":{"type":"Point","coordinates":[34.615,40.019]}}""")
    val r = Gazetteer.fromFeature(f, "anatolia", 0).fold(e => fail(e), identity)
    assertEquals(r.own, Some(ExternalId(LinkScheme.Whg, "99")))
    assertEquals(r.ref, "whg:99")
    assertEquals(r.placeId, "whg_99")
    assertEquals(r.kind, PlaceKind.Site)
    assertEquals(r.span, Span(Some(-1650), Some(-1180)))
    assertEquals(r.links, List(GazLink(ExternalId(LinkScheme.Wikidata, "Q193236"), LinkMatch.Close),
      GazLink(ExternalId(LinkScheme.Pleiades, "638753"), LinkMatch.Exact)))
    assertEquals(r.names.find(_.lang == "hit"),
      Some(GazName("Ḫattuša", "hit", Span(Some(-1650), Some(-1180)), Some("Bryce 2005 (2005)"))))
    assert(r.names.exists(_.name == "Hattusa"), "the title is a name")
    assertEquals(r.placeLinks.head, GazLink(ExternalId(LinkScheme.Whg, "99"), LinkMatch.Exact))

  test("fromFeature: a dataset-local @id makes an lpf_<dataset>_ place id; no @id is an error"):
    val r = Gazetteer.fromFeature(json("""{"@id":"https://example.org/gaz/place/Tell-Brak","properties":{"title":"Tell Brak"},"geometry":null}"""), "nagar", 0)
    assertEquals(r.map(_.placeId), Right("lpf_nagar_tell_brak"))
    assertEquals(r.map(_.ref), Right("https://example.org/gaz/place/Tell-Brak"))
    assert(Gazetteer.fromFeature(json("""{"properties":{"title":"x"}}"""), "d", 3).isLeft)

  test("fromTsvRow: LP-TSV id, title, variants with @lang, matches, point and years"):
    val header = Gazetteer.tsvCells("id\ttitle\ttitle_source\tccodes\tmatches\tvariants\ttypes\tlon\tlat\tstart\tend")
    val row = Gazetteer.tsvCells("p1\tKaneš\tKt archive\tTR\twd:Q1192387; tgn:7016542\tKanesh@en; Neša@hit; Kültepe\tsettlement\t35.635\t38.850\t-2000\t-1700")
    val r = Gazetteer.fromTsvRow(header, row, "kultepe", 2).fold(e => fail(e), identity)
    assertEquals(r.placeId, "lpf_kultepe_p1")
    assertEquals(r.kind, PlaceKind.Site)
    assertEquals(r.span, Span(Some(-2000), Some(-1700)))
    assertEquals(r.links.map(_.id.key), List("wikidata:Q1192387", "tgn:7016542"))
    assertEquals(r.names.map(n => n.name -> n.lang).toSet, Set("Kaneš" -> "", "Kanesh" -> "en", "Neša" -> "hit", "Kültepe" -> ""))
    assertEquals(r.geometry, Some(GazGeometry.GeoJson("""{"type":"Point","coordinates":[35.635,38.85]}""")))
    assertEquals(r.source, Some("Kt archive"))
    assert(Gazetteer.fromTsvRow(header, Gazetteer.tsvCells("\tno id"), "x", 3).isLeft)

  test("dataset: title, citation, licence and span from the file, a sidecar winning"):
    val top = json("""{"type":"FeatureCollection","title":"Hittite sites","license":"CC BY 4.0",
                      "when":{"timespans":[{"start":{"in":"-1650"},"end":{"in":"-1180"}}]}}""").asObject.get
    val side = json("""{"citation":"Doe, J. (2024). Hittite sites. WHG."}""").asObject.get
    assertEquals(Gazetteer.dataset(top, Some(side)),
      LpfDataset(Some("Hittite sites"), Some("Doe, J. (2024). Hittite sites. WHG."), Some("CC BY 4.0"), Span(Some(-1650), Some(-1180))))
    assertEquals(Gazetteer.datasetSlug("WHG Hittite Sites.lpf.json"), "whg_hittite_sites")
    assertEquals(Gazetteer.datasetSlug("kultepe.tsv"), "kultepe")
    assertEquals(Gazetteer.sourceKey(GazetteerKind.Lpf, Some("kultepe")), "lpf:kultepe")

  test("canonical: the seed before an LPF dataset before Wikidata before GeoNames; the smaller id breaks a tie"):
    val seed = PlaceMatch("pleiades_638753", None)
    val wd   = PlaceMatch("wikidata_q193236", Some("wikidata"))
    val gn   = PlaceMatch("geonames_750539", Some("geonames"))
    val lpf  = PlaceMatch("lpf_x_1", Some("lpf:x"))
    assertEquals(Gazetteer.canonical(List(gn, wd, seed, lpf)), Some("pleiades_638753"))
    assertEquals(Gazetteer.canonical(List(gn, wd, lpf)), Some("lpf_x_1"))
    assertEquals(Gazetteer.canonical(List(gn, wd)), Some("wikidata_q193236"))
    assertEquals(Gazetteer.canonical(List(PlaceMatch("geonames_9", Some("geonames")), PlaceMatch("geonames_10", Some("geonames")))),
      Some("geonames_10"))
    assertEquals(Gazetteer.canonical(Nil), None)

  test("Getty TGN (#1403): a source kind of its own, ranked between Wikidata and GeoNames, an imported source"):
    assertEquals(GazetteerKind.parse("tgn"), Some(GazetteerKind.Tgn))
    assertEquals(GazetteerKind.ofKey("tgn"), Some(GazetteerKind.Tgn))
    assertEquals(Gazetteer.sourceKey(GazetteerKind.Tgn), "tgn")
    val wd  = PlaceMatch("wikidata_q1", Some("wikidata"))
    val tgn = PlaceMatch("tgn_7000001", Some("tgn"))
    val gn  = PlaceMatch("geonames_1", Some("geonames"))
    assertEquals(Gazetteer.canonical(List(gn, tgn, wd)), Some("wikidata_q1"))
    assertEquals(Gazetteer.canonical(List(gn, tgn)), Some("tgn_7000001"))
    assertEquals(List(None, Some("lpf:x"), Some("wikidata"), Some("tgn"), Some("geonames"), Some("other"))
      .map(Geocode.tierOf), List(0, 1, 2, 3, 4, 5))
    assertEquals(Gazetteer.ImportedSources, List("wikidata", "geonames", "tgn"))
    assert(Gazetteer.unknownWhenSilent("tgn") && !Gazetteer.unknownWhenSilent("lpf:x") && !Gazetteer.unknownWhenSilent(""))
    assert(Gazetteer.PointAreaSources.contains("tgn"))
    val r = Gazetteer.fromFeature(io.circe.parser.parse(
      """{"@id":"http://vocab.getty.edu/tgn/7001393","type":"Feature","properties":{"title":"Roma","kind":"site"},
        |"geometry":{"type":"Point","coordinates":[12.483,41.883]}}""".stripMargin).toOption.get, "", 1)
    assertEquals(r.map(x => (x.ref, x.placeId)), Right(("tgn:7001393", "tgn_7001393")))

  test("canonical: the record's own id decides before its links; a native id before a copied one"):
    // GeoNames 3188382 (Varaždin) names wd:Q5420; Wikidata Q5420 names gn:3188382 (native on its place);
    // a neighbouring record that joined wikidata_q1059359 copied wd:Q5420 there. The copy must not win.
    val q5420  = PlaceMatch("wikidata_q5420", Some("wikidata"))
    val copied = PlaceMatch("wikidata_q1059359", Some("wikidata"), native = false)
    assertEquals(Gazetteer.canonical(byOwn = List(q5420), byLinks = List(copied, q5420)), Some("wikidata_q5420"))
    assertEquals(Gazetteer.canonical(Nil, List(copied, q5420)), Some("wikidata_q5420"))
    assertEquals(Gazetteer.canonical(Nil, List(copied)), Some("wikidata_q1059359"))
    // Tridentum (wd:Q3998737) links pl:383804 (a seed place); GeoNames' Trento copied wd:Q3998737 onto
    // the Natural Earth city. The record's own id now names the city — by a copy — and must not win.
    val pleiades = PlaceMatch("pleiades_383804", None)
    val city     = PlaceMatch("ne_city_1159139597", None, native = false)
    assertEquals(Gazetteer.canonical(byOwn = List(city), byLinks = List(pleiades)), Some("pleiades_383804"))
    // between two native places, the record's own id decides before a link
    assertEquals(Gazetteer.canonical(byOwn = List(PlaceMatch("wikidata_q9", Some("wikidata"))),
      byLinks = List(PlaceMatch("pleiades_1", None))), Some("wikidata_q9"))

  test("load counts: a re-run of the same version is unchanged"):
    assert(GazetteerLoadCounts("geonames", "v1", records = 10, joined = 4).unchanged)
    assert(!GazetteerLoadCounts("geonames", "v1", namesAdded = 1).unchanged)
    assert(!GazetteerLoadCounts("geonames", "v1", linksChanged = 1).unchanged, "a link's item facts changed")

  // ── what an imported place's silence about time means (V074, 2026-10-09) ─────────────────────

  test("validity: a GeoNames/Wikidata end with no start is a window before the end, start unknown"):
    assertEquals(Gazetteer.validity("wikidata", Span(None, Some(1769L)), 300), Validity(Some(1469L), Some(1769L), startUnknown = true))
    assertEquals(Gazetteer.validity("geonames", Span(None, Some(-1200L)), 100), Validity(Some(-1300L), Some(-1200L), startUnknown = true))
    assertEquals(Gazetteer.validity("wikidata", Span(Some(1299L), Some(1922L)), 300), Validity(Some(1299L), Some(1922L), false))
    assertEquals(Gazetteer.validity("wikidata", Span(Some(1200L), None), 300), Validity(Some(1200L), None, false),
      "a known start with no end: still there (a town founded in 1200)")
    assertEquals(Gazetteer.validity("geonames", Span.always, 300), Validity(None, None, false), "undated: told apart by its source")

  test("validity: an LPF dataset (and anything that is not GeoNames or Wikidata) states its open ranges on purpose"):
    assertEquals(Gazetteer.validity("lpf:whg_cities", Span(None, Some(1769L)), 300), Validity(None, Some(1769L), false))
    assertEquals(Gazetteer.validity("", Span(None, Some(1769L)), 300), Validity(None, Some(1769L), false))

  test("the window's default is the configured one (and V074's, checked in the db module)"):
    assertEquals(AppConfig.load().places.sources.openStartWindowYears, Gazetteer.DefaultOpenStartWindowYears)

  test("inDataset: an LPF feature with no `when` takes its dataset's; a dated one keeps its own"):
    val r = GazRecord("x", None, "lpf_d_x", "X", PlaceKind.Site, None, Span.always, Nil, Nil, None, None)
    val ds = LpfDataset(None, None, None, Span(Some(-800L), Some(-300L)))
    assertEquals(Gazetteer.inDataset(r, ds).span, Span(Some(-800L), Some(-300L)))
    assertEquals(Gazetteer.inDataset(r.copy(span = Span(Some(-500L), None)), ds).span, Span(Some(-500L), None))
    assertEquals(Gazetteer.inDataset(r, LpfDataset(None, None, None)).span, Span.always)

  test("an LPF feature's descriptions (Wikidata's schema:description) are read, trimmed and de-duplicated"):
    val f = json("""{"@id":"http://www.wikidata.org/entity/Q131839713","type":"Feature",
      "properties":{"title":"Kingdom of Patones","kind":"polity"},
      "geometry":{"type":"Point","coordinates":[-3.48,40.88]},
      "when":{"timespans":[{"end":{"in":"1769"}}]},
      "descriptions":[{"value":" former country in Modern Day Spain ","lang":"EN"},
                      {"value":"microestado de Europa (siglo VIII-1769)","lang":"es"},
                      {"value":"former country in Modern Day Spain","lang":"en"}, {"value":"  "}]}""")
    val r = Gazetteer.fromFeature(f, "", 1).fold(fail(_), identity)
    assertEquals(r.descriptions, List(GazDescription("former country in Modern Day Spain", "en"),
      GazDescription("microestado de Europa (siglo VIII-1769)", "es")))
    assertEquals(r.span, Span(None, Some(1769L)))
    assert(!r.properties.exists(_.contains("descriptions")), "not also dumped into properties")

  test("a century or a millennium is read as its widest years (an LPF earliest/latest bound)"):
    assertEquals(Gazetteer.span(json("""{"timespans":[{"start":{"earliest":"-3000","latest":"-2001"},"end":{"earliest":"1701","latest":"1800"}}]}""")),
      Span(Some(-3000L), Some(1800L)))

  test("PlaceDating.describe: a card says what the source states"):
    assertEquals(PlaceDating.describe(PlaceDating.EndOnly, None, Some(1769L)), "end 1769 CE, start unknown")
    assertEquals(PlaceDating.describe(PlaceDating.Unknown, None, None), "date unknown")
    assertEquals(PlaceDating.describe(PlaceDating.Stated, Some(-1650L), Some(-1180L)), "1650 BCE–1180 BCE")
    assertEquals(PlaceDating.describe(PlaceDating.Stated, Some(1200L), None), "from 1200 CE")
    assertEquals(PlaceDating.describe(PlaceDating.Stated, None, Some(1453L)), "until 1453 CE")
    assertEquals(PlaceDating.describe(PlaceDating.Stated, None, None), "", "a timeless seed place says nothing")
    assertEquals(PlaceDating.values.toList.flatMap(d => PlaceDating.parse(d.wire)), PlaceDating.values.toList)

  test("PlaceShape: a region or polity is a shape; a point-only one has no boundary yet; a site is a point by nature"):
    assert(PlaceShape.pointOnlyArea(PlaceKind.Region, hasBoundary = false))
    assert(PlaceShape.pointOnlyArea(PlaceKind.Polity, hasBoundary = false))
    assert(!PlaceShape.pointOnlyArea(PlaceKind.Region, hasBoundary = true))
    assert(!PlaceShape.pointOnlyArea(PlaceKind.Site, hasBoundary = false))
    assert(!PlaceShape.NoBoundaryNote.contains("_"))

  test("PlaceLink.url: the authority's own page"):
    assertEquals(PlaceLink.url("wikidata", "Q131839713"), Some("https://www.wikidata.org/wiki/Q131839713"))
    assertEquals(PlaceLink.url("geonames", "3114531"), Some("https://www.geonames.org/3114531"))
    assertEquals(PlaceLink.url("pleiades", "579885"), Some("https://pleiades.stoa.org/places/579885"))
    assertEquals(PlaceLink.url("viaf", "1"), None)
    assertEquals(PlaceLink.schemeTitle("geonames"), "GeoNames")

  test("load counts: descriptions count as a change, and add up"):
    val a = GazetteerLoadCounts("wikidata", "v2", descriptionsChanged = 2)
    assert(!a.unchanged)
    assert(a.describe.contains("descriptions +0 −0 ~2"), a.describe)
    assertEquals((a + GazetteerLoadCounts("wikidata", "v2", descriptionsAdded = 1)).descriptionsAdded, 1L)
    assert(GazetteerLoadCounts("wikidata", "v2").unchanged)

  // ── an area known only by a point (2026-10-09) ────────────────────────────────────────────────

  private def gj(t: String) = Some(GazGeometry.GeoJson(t))
  private val Pt   = gj("""{"type":"Point","coordinates":[30,40]}""")
  private val Poly = gj("""{"type":"Polygon","coordinates":[[[30,40],[31,40],[31,41],[30,40]]]}""")

  test("pointOnly: a Point, a MultiPoint or no geometry; a polygon, a line or anything unreadable is a shape"):
    assert(Gazetteer.pointOnly(Pt))
    assert(Gazetteer.pointOnly(gj("""{"type":"MultiPoint","coordinates":[[30,40],[31,41]]}""")))
    assert(Gazetteer.pointOnly(None))
    assert(Gazetteer.pointOnly(Some(GazGeometry.Wkt("POINT(30 40)"))))
    assert(Gazetteer.pointOnly(Some(GazGeometry.Wkt(" srid=4326;multipoint((30 40))"))))
    assert(!Gazetteer.pointOnly(Poly))
    assert(!Gazetteer.pointOnly(gj("""{"type":"LineString","coordinates":[[30,40],[31,41]]}""")))
    assert(!Gazetteer.pointOnly(Some(GazGeometry.Wkt("POLYGON((30 40,31 40,31 41,30 40))"))))
    assert(!Gazetteer.pointOnly(gj("not json")), "never dropped on a guess")

  test("dropsPointArea: a GeoNames/Wikidata region, polity or people with only a point; nothing else"):
    val kinds = Gazetteer.DefaultPointAreaKinds.toSet
    def r(kind: String, g: Option[GazGeometry]) =
      GazRecord("wikidata:Q1", Some(ExternalId(LinkScheme.Wikidata, "Q1")), "wikidata_q1", "X", kind, g, Span.always,
        Nil, Nil, None, None)
    for k <- List(PlaceKind.Region, PlaceKind.Polity, PlaceKind.People); src <- List("wikidata", "geonames") do
      assert(Gazetteer.dropsPointArea(src, r(k, Pt), kinds), s"$src $k")
    assert(!Gazetteer.dropsPointArea("wikidata", r(PlaceKind.Region, Poly), kinds), "a shape is a region")
    assert(!Gazetteer.dropsPointArea("wikidata", r(PlaceKind.Site, Pt), kinds), "a site is a point by nature")
    assert(!Gazetteer.dropsPointArea("geonames", r(PlaceKind.Feature, Pt), kinds))
    assert(!Gazetteer.dropsPointArea("lpf:whg", r(PlaceKind.Region, Pt), kinds), "an LPF dataset keeps its points")
    assert(!Gazetteer.dropsPointArea("wikidata", r(PlaceKind.Region, Pt), Set.empty), "\"\" keeps every record")
    assert(Gazetteer.dropsPointArea("wikidata", r(PlaceKind.Polity, Pt), Set(PlaceKind.Polity)))
    assert(!Gazetteer.dropsPointArea("wikidata", r(PlaceKind.Region, Pt), Set(PlaceKind.Polity)))

  test("the point-area kinds' default is the configured one"):
    assertEquals(AppConfig.load().places.sources.pointAreaKindSet, Gazetteer.DefaultPointAreaKinds.toSet)
    assert(AppConfig.load().places.sources.pointAreaFixBatch > 0)
    assert(AppConfig.load().places.sources.pointAreaFixSeconds <= 50, "well inside netty's 60 s idle timeout")

  test("load counts: a dropped point-only area is reported, and is no change by itself"):
    val c = GazetteerLoadCounts("wikidata", "v1", records = 3, pointAreasSkipped = 2)
    assert(c.unchanged)
    assert(c.describe.contains("2 point-only region(s)/polity(ies) not kept as places"), c.describe)
    assertEquals((c + c).pointAreasSkipped, 4L)
