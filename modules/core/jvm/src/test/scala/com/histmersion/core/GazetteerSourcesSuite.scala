package com.histmersion.core

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
