package com.histmersion
package api

import com.histmersion.api.catalogue.gazetteer.SourceInfo
import com.histmersion.api.catalogue.gazetteer.LoadCounts
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import com.histmersion.api.catalogue.{
  GazDescription,
  Gazetteer,
  GazGeometry,
  GazLink,
  GazName,
  GazRecord,
  Geocode,
  LinkMatch,
  LinkScheme
}
import com.histmersion.api.library.ExternalId
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{tolerated, LintRule}

/** The external gazetteer sources' pure half (#1064), on the JVM (Lpf's year parser uses a look-behind Scala.js cannot
  * compile): identifiers, open time spans, the LPF and LP-TSV readers, name merging, and which place a record joins.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
class GazetteerSourcesSuite extends munit.FunSuite:

  private def json(s: String) = RawJson.parse(s).fold(e => fail(e), identity)

  test("ExternalId: prefixed ids and authority URIs, normalised; anything else is no identity"):
    assertEquals(
      ExternalId.parse(ExternalId.Reference("wd:q42")),
      Some(ExternalId(LinkScheme.Wikidata, ExternalId.Id("Q42")))
    )
    assertEquals(
      ExternalId.parse(ExternalId.Reference("http://www.wikidata.org/entity/Q5684")),
      Some(ExternalId(LinkScheme.Wikidata, ExternalId.Id("Q5684")))
    )
    assertEquals(
      ExternalId.parse(ExternalId.Reference("https://www.wikidata.org/wiki/Q5684")),
      Some(ExternalId(LinkScheme.Wikidata, ExternalId.Id("Q5684")))
    )
    assertEquals(
      ExternalId.parse(ExternalId.Reference("gn:750539")),
      Some(ExternalId(LinkScheme.Geonames, ExternalId.Id("750539")))
    )
    assertEquals(
      ExternalId.parse(ExternalId.Reference("https://sws.geonames.org/750539/")),
      Some(ExternalId(LinkScheme.Geonames, ExternalId.Id("750539")))
    )
    assertEquals(
      ExternalId.parse(ExternalId.Reference("pl:579885")),
      Some(ExternalId(LinkScheme.Pleiades, ExternalId.Id("579885")))
    )
    assertEquals(
      ExternalId.parse(ExternalId.Reference("https://pleiades.stoa.org/places/579885")),
      Some(ExternalId(LinkScheme.Pleiades, ExternalId.Id("579885")))
    )
    assertEquals(
      ExternalId.parse(ExternalId.Reference("tgn:7011944")),
      Some(ExternalId(LinkScheme.Tgn, ExternalId.Id("7011944")))
    )
    assertEquals(
      ExternalId.parse(ExternalId.Reference("http://vocab.getty.edu/page/tgn/7011944")),
      Some(ExternalId(LinkScheme.Tgn, ExternalId.Id("7011944")))
    )
    assertEquals(ExternalId.parse(ExternalId.Reference("viaf:12345")), None)
    assertEquals(ExternalId.parse(ExternalId.Reference("wd:Paris")), None)
    assertEquals(ExternalId.parse(ExternalId.Reference("my-dataset-17")), None)
    assertEquals(ExternalId(LinkScheme.Wikidata, ExternalId.Id("Q42")).placeId, "wikidata_q42")

  test("span: an open side stays open; several timespans give the widest; a bare year is that year"):
    assertEquals(Gazetteer.span(json("""{"timespans":[{"end":{"in":"1935"}}]}""")), Span(None, Some(Year(1935L))))
    assertEquals(
      Gazetteer.span(json("""{"timespans":[{"start":{"in":"-1650"},"end":{"in":"-1180"}},
                                       {"start":{"earliest":"-1700"},"end":{"latest":"-1100"}}]}""")),
      Span(Some(Year(-1700L)), Some(Year(-1100L)))
    )
    assertEquals(
      Gazetteer.span(json("""{"timespans":[{"start":{"in":"330"}},{"start":{"in":"-660"},"end":{"in":"330"}}]}""")),
      Span(Some(Year(-660L)), None)
    )
    assertEquals(Gazetteer.span(json("-500")), Span(Some(Year(-500L)), Some(Year(-500L))))
    assertEquals(Gazetteer.span(json("{}")), Span.always)

  test("mergeNames: one name per (name, lang); a dated entry beats an undated repeat; citations kept"):
    val ns = Gazetteer.mergeNames(
      List(
        GazName(GazName.Name("Mikoyan"), GazName.Lang("en")),
        GazName(GazName.Name("Mikoyan"), GazName.Lang("en"), Span(Some(Year(1935)), Some(Year(1956)))),
        GazName(
          GazName.Name("Smyrna"),
          GazName.Lang("la"),
          Span(Some(Year(-300)), Some(Year(100))),
          Some(GazName.Citation("Barrington 56"))
        ),
        GazName(
          GazName.Name("Smyrna"),
          GazName.Lang("la"),
          Span(Some(Year(-600)), None),
          Some(GazName.Citation("Strabo"))
        ),
        GazName(GazName.Name(" "), GazName.Lang("en"))
      )
    )
    assertEquals(
      ns,
      List(
        GazName(GazName.Name("Mikoyan"), GazName.Lang("en"), Span(Some(Year(1935)), Some(Year(1956)))),
        GazName(
          GazName.Name("Smyrna"),
          GazName.Lang("la"),
          Span(Some(Year(-600)), None),
          Some(GazName.Citation("Barrington 56; Strabo"))
        )
      )
    )

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
    val r = Gazetteer.fromFeature(f, Gazetteer.FromFeatureDataset("anatolia"), 0).fold(e => fail(e.sentence), identity)
    assertEquals(r.own, Some(ExternalId(LinkScheme.Whg, ExternalId.Id("99"))))
    assertEquals(r.ref, "whg:99")
    assertEquals(r.placeId, api.catalogue.Place.Id("whg_99"))
    assertEquals(r.kind, Place.Kind.Site)
    assertEquals(r.span, Span(Some(Year(-1650)), Some(Year(-1180))))
    assertEquals(
      r.links,
      List(
        GazLink(ExternalId(LinkScheme.Wikidata, ExternalId.Id("Q193236")), LinkMatch.Close),
        GazLink(ExternalId(LinkScheme.Pleiades, ExternalId.Id("638753")), LinkMatch.Exact)
      )
    )
    assertEquals(
      r.names.find(_.lang == GazName.Lang("hit")),
      Some(
        GazName(
          GazName.Name("Ḫattuša"),
          GazName.Lang("hit"),
          Span(Some(Year(-1650)), Some(Year(-1180))),
          Some(GazName.Citation("Bryce 2005 (2005)"))
        )
      )
    )
    assert(r.names.exists(_.name == GazName.Name("Hattusa")), "the title is a name")
    assertEquals(r.placeLinks.head, GazLink(ExternalId(LinkScheme.Whg, ExternalId.Id("99")), LinkMatch.Exact))

  test("fromFeature: a dataset-local @id makes an lpf_<dataset>_ place id; no @id is an error"):
    val r = Gazetteer.fromFeature(
      json("""{"@id":"https://example.org/gaz/place/Tell-Brak","properties":{"title":"Tell Brak"},"geometry":null}"""),
      Gazetteer.FromFeatureDataset("nagar"),
      0
    )
    assertEquals(r.map(_.placeId), Right(api.catalogue.Place.Id("lpf_nagar_tell_brak")))
    assertEquals(r.map(_.ref), Right("https://example.org/gaz/place/Tell-Brak"))
    assert(Gazetteer.fromFeature(json("""{"properties":{"title":"x"}}"""), Gazetteer.FromFeatureDataset("d"), 3).isLeft)

  test("fromTsvRow: LP-TSV id, title, variants with @lang, matches, point and years"):
    val header = Gazetteer.tsvCells("id\ttitle\ttitle_source\tccodes\tmatches\tvariants\ttypes\tlon\tlat\tstart\tend")
    val row = Gazetteer.tsvCells(
      "p1\tKaneš\tKt archive\tTR\twd:Q1192387; tgn:7016542\tKanesh@en; Neša@hit; Kültepe\tsettlement\t35.635\t38.850\t-2000\t-1700"
    )
    val r =
      Gazetteer.fromTsvRow(header, row, Gazetteer.FromTsvRowDataset("kultepe"), 2).fold(e => fail(e.sentence), identity)
    assertEquals(r.placeId, api.catalogue.Place.Id("lpf_kultepe_p1"))
    assertEquals(r.kind, Place.Kind.Site)
    assertEquals(r.span, Span(Some(Year(-2000)), Some(Year(-1700))))
    assertEquals(r.links.map(_.id.key), List("wikidata:Q1192387", "tgn:7016542"))
    assertEquals(
      r.names.map(n => n.name -> n.lang).toSet,
      Set("Kaneš" -> "", "Kanesh" -> "en", "Neša" -> "hit", "Kültepe" -> "")
    )
    assertEquals(
      r.geometry,
      Some(GazGeometry.GeoJson(GazGeometry.GeoJson.Text("""{"type":"Point","coordinates":[35.635,38.85]}""")))
    )
    assertEquals(r.source, Some("Kt archive"))
    assert(Gazetteer.fromTsvRow(header, Gazetteer.tsvCells("\tno id"), Gazetteer.FromTsvRowDataset("x"), 3).isLeft)

  test("dataset: title, citation, licence and span from the file, a sidecar winning"):
    val top = json("""{"type":"FeatureCollection","title":"Hittite sites","license":"CC BY 4.0",
                      "when":{"timespans":[{"start":{"in":"-1650"},"end":{"in":"-1180"}}]}}""").asObject.get
    val side = json("""{"citation":"Doe, J. (2024). Hittite sites. WHG."}""").asObject.get
    assertEquals(
      Gazetteer.dataset(top, Some(side)),
      LpfDataset(
        Some(LpfDataset.Title("Hittite sites")),
        Some(LpfDataset.Citation("Doe, J. (2024). Hittite sites. WHG.")),
        Some(LpfDataset.Licence("CC BY 4.0")),
        Span(Some(Year(-1650)), Some(Year(-1180)))
      )
    )
    assertEquals(SourceInfo.datasetSlug(SourceInfo.File("WHG Hittite Sites.lpf.json")), "whg_hittite_sites")
    assertEquals(SourceInfo.datasetSlug(SourceInfo.File("kultepe.tsv")), "kultepe")
    assertEquals(api.catalogue.gazetteer.Kind.Lpf.sourceKey(Some(Gazetteer.DatasetName("kultepe"))), "lpf:kultepe")

  test("canonical: the seed before an LPF dataset before Wikidata before GeoNames; the smaller id breaks a tie"):
    val seed =
      api.catalogue.Place.Match(api.catalogue.Place.Id("pleiades_638753"), None)
    val wd = api.catalogue.Place.Match(
      api.catalogue.Place.Id("wikidata_q193236"),
      Some(api.catalogue.Place.Match.ImportSource("wikidata"))
    )
    val gn = api.catalogue.Place.Match(
      api.catalogue.Place.Id("geonames_750539"),
      Some(api.catalogue.Place.Match.ImportSource("geonames"))
    )
    val lpf = api.catalogue.Place.Match(
      api.catalogue.Place.Id("lpf_x_1"),
      Some(api.catalogue.Place.Match.ImportSource("lpf:x"))
    )
    assertEquals(Gazetteer.canonical(List(gn, wd, seed, lpf)), Some("pleiades_638753"))
    assertEquals(Gazetteer.canonical(List(gn, wd, lpf)), Some("lpf_x_1"))
    assertEquals(Gazetteer.canonical(List(gn, wd)), Some("wikidata_q193236"))
    assertEquals(
      Gazetteer.canonical(
        List(
          api.catalogue.Place.Match(
            api.catalogue.Place.Id("geonames_9"),
            Some(api.catalogue.Place.Match.ImportSource("geonames"))
          ),
          api.catalogue.Place.Match(
            api.catalogue.Place.Id("geonames_10"),
            Some(api.catalogue.Place.Match.ImportSource("geonames"))
          )
        )
      ),
      Some("geonames_10")
    )
    assertEquals(Gazetteer.canonical(Nil), None)

  test("Getty TGN (#1403): a source kind of its own, ranked between Wikidata and GeoNames, an imported source"):
    assertEquals(
      api.catalogue.gazetteer.Kind.parse(api.catalogue.gazetteer.Kind.Wire("tgn")),
      Some(api.catalogue.gazetteer.Kind.Tgn)
    )
    assertEquals(api.catalogue.gazetteer.Kind.ofKey(SourceInfo.Key("tgn")), Some(api.catalogue.gazetteer.Kind.Tgn))
    assertEquals(api.catalogue.gazetteer.Kind.Tgn.sourceKey(), "tgn")
    val wd = api.catalogue.Place.Match(
      api.catalogue.Place.Id("wikidata_q1"),
      Some(api.catalogue.Place.Match.ImportSource("wikidata"))
    )
    val tgn = api.catalogue.Place.Match(
      api.catalogue.Place.Id("tgn_7000001"),
      Some(api.catalogue.Place.Match.ImportSource("tgn"))
    )
    val gn = api.catalogue.Place.Match(
      api.catalogue.Place.Id("geonames_1"),
      Some(api.catalogue.Place.Match.ImportSource("geonames"))
    )
    assertEquals(Gazetteer.canonical(List(gn, tgn, wd)), Some("wikidata_q1"))
    assertEquals(Gazetteer.canonical(List(gn, tgn)), Some("tgn_7000001"))
    assertEquals(
      List(None, Some("lpf:x"), Some("wikidata"), Some("tgn"), Some("geonames"), Some("other"))
        .map(_.map(s => api.catalogue.Place.Match.ImportSource(s)))
        .map(Geocode.tierOf),
      List(0, 1, 2, 3, 4, 5)
    )
    assertEquals(Gazetteer.ImportedSources, List("wikidata", "geonames", "tgn"))
    assert(
      SourceInfo.unknownWhenSilent(SourceInfo.Key("tgn")) && !SourceInfo.unknownWhenSilent(
        SourceInfo.Key("lpf:x")
      ) && !SourceInfo.unknownWhenSilent(SourceInfo.Key(""))
    )
    assert(Gazetteer.PointAreaSources.contains("tgn"))
    val r = Gazetteer.fromFeature(
      RawJson
        .parse(
          """{"@id":"http://vocab.getty.edu/tgn/7001393","type":"Feature","properties":{"title":"Roma","kind":"site"},
        |"geometry":{"type":"Point","coordinates":[12.483,41.883]}}""".stripMargin
        )
        .toOption
        .get,
      Gazetteer.FromFeatureDataset(""),
      1
    )
    assertEquals(
      r.map(x => (x.ref, x.placeId)),
      Right(("tgn:7001393", api.catalogue.Place.Id("tgn_7001393")))
    )

  test("canonical: the record's own id decides before its links; a native id before a copied one"):
    // GeoNames 3188382 (Varaždin) names wd:Q5420; Wikidata Q5420 names gn:3188382 (native on its place);
    // a neighbouring record that joined wikidata_q1059359 copied wd:Q5420 there. The copy must not win.
    val q5420 = api.catalogue.Place.Match(
      api.catalogue.Place.Id("wikidata_q5420"),
      Some(api.catalogue.Place.Match.ImportSource("wikidata"))
    )
    val copied = api.catalogue.Place.Match(
      api.catalogue.Place.Id("wikidata_q1059359"),
      Some(api.catalogue.Place.Match.ImportSource("wikidata")),
      native = api.catalogue.Place.Match.Native.Foreign
    )
    assertEquals(Gazetteer.canonical(byOwn = List(q5420), byLinks = List(copied, q5420)), Some("wikidata_q5420"))
    assertEquals(Gazetteer.canonical(Nil, List(copied, q5420)), Some("wikidata_q5420"))
    assertEquals(Gazetteer.canonical(Nil, List(copied)), Some("wikidata_q1059359"))
    // Tridentum (wd:Q3998737) links pl:383804 (a seed place); GeoNames' Trento copied wd:Q3998737 onto
    // the Natural Earth city. The record's own id now names the city — by a copy — and must not win.
    val pleiades =
      api.catalogue.Place.Match(api.catalogue.Place.Id("pleiades_383804"), None)
    val city = api.catalogue.Place.Match(
      api.catalogue.Place.Id("ne_city_1159139597"),
      None,
      native = api.catalogue.Place.Match.Native.Foreign
    )
    assertEquals(Gazetteer.canonical(byOwn = List(city), byLinks = List(pleiades)), Some("pleiades_383804"))
    // between two native places, the record's own id decides before a link
    assertEquals(
      Gazetteer.canonical(
        byOwn = List(
          api.catalogue.Place.Match(
            api.catalogue.Place.Id("wikidata_q9"),
            Some(api.catalogue.Place.Match.ImportSource("wikidata"))
          )
        ),
        byLinks = List(api.catalogue.Place.Match(api.catalogue.Place.Id("pleiades_1"), None))
      ),
      Some("wikidata_q9")
    )

  test("load counts: a re-run of the same version is unchanged"):
    assert(
      LoadCounts(
        LoadCounts.Source("geonames"),
        LoadCounts.Version("v1"),
        records = LoadCounts.Records(10),
        joined = LoadCounts.Joined(4)
      ).unchanged
    )
    assert(
      !LoadCounts(
        LoadCounts.Source("geonames"),
        LoadCounts.Version("v1"),
        namesAdded = LoadCounts.NamesAdded(1)
      ).unchanged
    )
    assert(
      !LoadCounts(
        LoadCounts.Source("geonames"),
        LoadCounts.Version("v1"),
        linksChanged = LoadCounts.LinksChanged(1)
      ).unchanged,
      "a link's item facts changed"
    )

  // ── what an imported place's silence about time means (V074, 2026-10-09) ─────────────────────

  test("validity: a GeoNames/Wikidata end with no start is a window before the end, start unknown"):
    assertEquals(
      Gazetteer.validity(
        Gazetteer.ValiditySourceKey("wikidata"),
        Span(None, Some(Year(1769L))),
        Gazetteer.ValidityWindowYears(300)
      ),
      Validity(Some(Year(1469L)), Some(Year(1769L)), startUnknown = Validity.StartUnknown.StartUnknown)
    )
    assertEquals(
      Gazetteer.validity(
        Gazetteer.ValiditySourceKey("geonames"),
        Span(None, Some(Year(-1200L))),
        Gazetteer.ValidityWindowYears(100)
      ),
      Validity(Some(Year(-1300L)), Some(Year(-1200L)), startUnknown = Validity.StartUnknown.StartUnknown)
    )
    assertEquals(
      Gazetteer.validity(
        Gazetteer.ValiditySourceKey("wikidata"),
        Span(Some(Year(1299L)), Some(Year(1922L))),
        Gazetteer.ValidityWindowYears(300)
      ),
      Validity(Some(Year(1299L)), Some(Year(1922L)), Validity.StartUnknown.StartKnown)
    )
    assertEquals(
      Gazetteer.validity(
        Gazetteer.ValiditySourceKey("wikidata"),
        Span(Some(Year(1200L)), None),
        Gazetteer.ValidityWindowYears(300)
      ),
      Validity(Some(Year(1200L)), None, Validity.StartUnknown.StartKnown),
      "a known start with no end: still there (a town founded in 1200)"
    )
    assertEquals(
      Gazetteer.validity(Gazetteer.ValiditySourceKey("geonames"), Span.always, Gazetteer.ValidityWindowYears(300)),
      Validity(None, None, Validity.StartUnknown.StartKnown),
      "undated: told apart by its source"
    )

  test("validity: an LPF dataset (and anything that is not GeoNames or Wikidata) states its open ranges on purpose"):
    assertEquals(
      Gazetteer.validity(
        Gazetteer.ValiditySourceKey("lpf:whg_cities"),
        Span(None, Some(Year(1769L))),
        Gazetteer.ValidityWindowYears(300)
      ),
      Validity(None, Some(Year(1769L)), Validity.StartUnknown.StartKnown)
    )
    assertEquals(
      Gazetteer
        .validity(Gazetteer.ValiditySourceKey(""), Span(None, Some(Year(1769L))), Gazetteer.ValidityWindowYears(300)),
      Validity(None, Some(Year(1769L)), Validity.StartUnknown.StartKnown)
    )

  test("the window's default is the configured one (and V074's, checked in the db module)"):
    assertEquals(
      AppConfig.load().places.sources.openStartWindowYears.value,
      Gazetteer.DefaultOpenStartWindowYears.value
    )

  test("inDataset: an LPF feature with no `when` takes its dataset's; a dated one keeps its own"):
    val r = GazRecord(
      GazRecord.Ref("x"),
      None,
      api.catalogue.Place.Id("lpf_d_x"),
      GazRecord.Label("X"),
      GazRecord.Kind(Place.Kind.Site),
      None,
      Span.always,
      Nil,
      Nil,
      None,
      None
    )
    val ds = LpfDataset(None, None, None, Span(Some(Year(-800L)), Some(Year(-300L))))
    assertEquals(Gazetteer.inDataset(r, ds).span, Span(Some(Year(-800L)), Some(Year(-300L))))
    assertEquals(
      Gazetteer.inDataset(r.modify(_.span).setTo(Span(Some(Year(-500L)), None)), ds).span,
      Span(Some(Year(-500L)), None)
    )
    assertEquals(Gazetteer.inDataset(r, LpfDataset(None, None, None)).span, Span.always)

  test("an LPF feature's descriptions (Wikidata's schema:description) are read, trimmed and de-duplicated"):
    val f = json("""{"@id":"http://www.wikidata.org/entity/Q131839713","type":"Feature",
      "properties":{"title":"Kingdom of Patones","kind":"polity"},
      "geometry":{"type":"Point","coordinates":[-3.48,40.88]},
      "when":{"timespans":[{"end":{"in":"1769"}}]},
      "descriptions":[{"value":" former country in Modern Day Spain ","lang":"EN"},
                      {"value":"microestado de Europa (siglo VIII-1769)","lang":"es"},
                      {"value":"former country in Modern Day Spain","lang":"en"}, {"value":"  "}]}""")
    val r = Gazetteer.fromFeature(f, Gazetteer.FromFeatureDataset(""), 1).fold(e => fail(e.sentence), identity)
    assertEquals(
      r.descriptions,
      List(
        GazDescription(GazDescription.Text("former country in Modern Day Spain"), GazDescription.Lang("en")),
        GazDescription(GazDescription.Text("microestado de Europa (siglo VIII-1769)"), GazDescription.Lang("es"))
      )
    )
    assertEquals(r.span, Span(None, Some(Year(1769L))))
    assert(!r.properties.exists(_.value.contains("descriptions")), "not also dumped into properties")

  test("a century or a millennium is read as its widest years (an LPF earliest/latest bound)"):
    assertEquals(
      Gazetteer.span(
        json(
          """{"timespans":[{"start":{"earliest":"-3000","latest":"-2001"},"end":{"earliest":"1701","latest":"1800"}}]}"""
        )
      ),
      Span(Some(Year(-3000L)), Some(Year(1800L)))
    )

  test("PlaceDating.describe: a card says what the source states"):
    assertEquals(
      api.catalogue.Place.Dating
        .describe(api.catalogue.Place.Dating.EndOnly, None, Some(Year(1769L))),
      "end 1769 CE, start unknown"
    )
    assertEquals(
      api.catalogue.Place.Dating
        .describe(api.catalogue.Place.Dating.Unknown, None, None),
      "date unknown"
    )
    assertEquals(
      api.catalogue.Place.Dating
        .describe(api.catalogue.Place.Dating.Stated, Some(Year(-1650L)), Some(Year(-1180L))),
      "1650 BCE–1180 BCE"
    )
    assertEquals(
      api.catalogue.Place.Dating
        .describe(api.catalogue.Place.Dating.Stated, Some(Year(1200L)), None),
      "from 1200 CE"
    )
    assertEquals(
      api.catalogue.Place.Dating
        .describe(api.catalogue.Place.Dating.Stated, None, Some(Year(1453L))),
      "until 1453 CE"
    )
    assertEquals(
      api.catalogue.Place.Dating
        .describe(api.catalogue.Place.Dating.Stated, None, None),
      "",
      "a timeless seed place says nothing"
    )
    assertEquals(
      api.catalogue.Place.Dating.values.toList.flatMap(d => api.catalogue.Place.Dating.parse(d.wire)),
      api.catalogue.Place.Dating.values.toList
    )

  test("PlaceShape: a region or polity is a shape; a point-only one has no boundary yet; a site is a point by nature"):
    assert(
      api.catalogue.Place.Shape.pointOnlyArea(
        api.catalogue.Place.Shape.PointOnlyAreaKind(Place.Kind.Region),
        hasBoundary = false
      )
    )
    assert(
      api.catalogue.Place.Shape.pointOnlyArea(
        api.catalogue.Place.Shape.PointOnlyAreaKind(Place.Kind.Polity),
        hasBoundary = false
      )
    )
    assert(
      !api.catalogue.Place.Shape.pointOnlyArea(
        api.catalogue.Place.Shape.PointOnlyAreaKind(Place.Kind.Region),
        hasBoundary = true
      )
    )
    assert(
      !api.catalogue.Place.Shape.pointOnlyArea(
        api.catalogue.Place.Shape.PointOnlyAreaKind(Place.Kind.Site),
        hasBoundary = false
      )
    )
    assert(!api.catalogue.Place.Shape.NoBoundaryNote.value.contains("_"))

  test("PlaceLink.url: the authority's own page"):
    assertEquals(
      api.catalogue.Place.Link.url(
        api.catalogue.Place.Link.UrlScheme("wikidata"),
        api.catalogue.Place.Link.UrlExternalId("Q131839713")
      ),
      Some("https://www.wikidata.org/wiki/Q131839713")
    )
    assertEquals(
      api.catalogue.Place.Link.url(
        api.catalogue.Place.Link.UrlScheme("geonames"),
        api.catalogue.Place.Link.UrlExternalId("3114531")
      ),
      Some("https://www.geonames.org/3114531")
    )
    assertEquals(
      api.catalogue.Place.Link.url(
        api.catalogue.Place.Link.UrlScheme("pleiades"),
        api.catalogue.Place.Link.UrlExternalId("579885")
      ),
      Some("https://pleiades.stoa.org/places/579885")
    )
    assertEquals(
      api.catalogue.Place.Link.url(
        api.catalogue.Place.Link.UrlScheme("viaf"),
        api.catalogue.Place.Link.UrlExternalId("1")
      ),
      None
    )
    assertEquals(
      api.catalogue.Place.Link.schemeTitle(api.catalogue.Place.Link.Scheme("geonames")),
      "GeoNames"
    )

  test("load counts: descriptions count as a change, and add up"):
    val a = LoadCounts(
      LoadCounts.Source("wikidata"),
      LoadCounts.Version("v2"),
      descriptionsChanged = LoadCounts.DescriptionsChanged(2)
    )
    assert(!a.unchanged)
    assert(a.describe.contains("descriptions +0 −0 ~2"), a.describe)
    assertEquals(
      (a + LoadCounts(
        LoadCounts.Source("wikidata"),
        LoadCounts.Version("v2"),
        descriptionsAdded = LoadCounts.DescriptionsAdded(1L)
      )).descriptionsAdded,
      1L
    )
    assert(LoadCounts(LoadCounts.Source("wikidata"), LoadCounts.Version("v2")).unchanged)

  // ── an area known only by a point (2026-10-09) ────────────────────────────────────────────────

  private def gj(t: GazGeometry.GeoJson.Text) = Some(GazGeometry.GeoJson(t))
  private val Pt = gj(GazGeometry.GeoJson.Text("""{"type":"Point","coordinates":[30,40]}"""))
  private val Poly = gj(
    GazGeometry.GeoJson.Text("""{"type":"Polygon","coordinates":[[[30,40],[31,40],[31,41],[30,40]]]}""")
  )

  test("pointOnly: a Point, a MultiPoint or no geometry; a polygon, a line or anything unreadable is a shape"):
    assert(Gazetteer.pointOnly(Pt))
    assert(
      Gazetteer.pointOnly(gj(GazGeometry.GeoJson.Text("""{"type":"MultiPoint","coordinates":[[30,40],[31,41]]}""")))
    )
    assert(Gazetteer.pointOnly(None))
    assert(Gazetteer.pointOnly(Some(GazGeometry.Wkt(GazGeometry.Wkt.Text("POINT(30 40)")))))
    assert(Gazetteer.pointOnly(Some(GazGeometry.Wkt(GazGeometry.Wkt.Text(" srid=4326;multipoint((30 40))")))))
    assert(!Gazetteer.pointOnly(Poly))
    assert(
      !Gazetteer.pointOnly(gj(GazGeometry.GeoJson.Text("""{"type":"LineString","coordinates":[[30,40],[31,41]]}""")))
    )
    assert(!Gazetteer.pointOnly(Some(GazGeometry.Wkt(GazGeometry.Wkt.Text("POLYGON((30 40,31 40,31 41,30 40))")))))
    assert(!Gazetteer.pointOnly(gj(GazGeometry.GeoJson.Text("not json"))), "never dropped on a guess")

  test("dropsPointArea: a GeoNames/Wikidata region, polity or people with only a point; nothing else"):
    val kinds = Gazetteer.DefaultPointAreaKinds.toSet
    def r(kind: GazRecord.Kind, g: Option[GazGeometry]) =
      GazRecord(
        GazRecord.Ref("wikidata:Q1"),
        Some(ExternalId(LinkScheme.Wikidata, ExternalId.Id("Q1"))),
        api.catalogue.Place.Id("wikidata_q1"),
        GazRecord.Label("X"),
        kind,
        g,
        Span.always,
        Nil,
        Nil,
        None,
        None
      )
    for
      k <- List(Place.Kind.Region, Place.Kind.Polity, Place.Kind.People);
      src <- List("wikidata", "geonames").map(s => SourceInfo.Key(s))
    do assert(SourceInfo.dropsPointArea(src, r(GazRecord.Kind(k), Pt), kinds), s"$src $k")
    assert(
      !SourceInfo.dropsPointArea(SourceInfo.Key("wikidata"), r(GazRecord.Kind(Place.Kind.Region), Poly), kinds),
      "a shape is a region"
    )
    assert(
      !SourceInfo.dropsPointArea(SourceInfo.Key("wikidata"), r(GazRecord.Kind(Place.Kind.Site), Pt), kinds),
      "a site is a point by nature"
    )
    assert(
      !SourceInfo.dropsPointArea(SourceInfo.Key("geonames"), r(GazRecord.Kind(Place.Kind.Feature), Pt), kinds)
    )
    assert(
      !SourceInfo.dropsPointArea(SourceInfo.Key("lpf:whg"), r(GazRecord.Kind(Place.Kind.Region), Pt), kinds),
      "an LPF dataset keeps its points"
    )
    assert(
      !SourceInfo.dropsPointArea(SourceInfo.Key("wikidata"), r(GazRecord.Kind(Place.Kind.Region), Pt), Set.empty),
      "\"\" keeps every record"
    )
    assert(
      SourceInfo.dropsPointArea(
        SourceInfo.Key("wikidata"),
        r(GazRecord.Kind(Place.Kind.Polity), Pt),
        Set(Gazetteer.PointAreaKinds(Place.Kind.Polity))
      )
    )
    assert(
      !SourceInfo.dropsPointArea(
        SourceInfo.Key("wikidata"),
        r(GazRecord.Kind(Place.Kind.Region), Pt),
        Set(Gazetteer.PointAreaKinds(Place.Kind.Polity))
      )
    )

  test("the point-area kinds' default is the configured one"):
    assertEquals(AppConfig.load().places.sources.pointAreaKindSet, Gazetteer.DefaultPointAreaKinds.toSet)
    assert(AppConfig.load().places.sources.pointAreaFixBatch.value > 0)
    assert(AppConfig.load().places.sources.pointAreaFixSeconds.value <= 50, "well inside netty's 60 s idle timeout")

  test("load counts: a dropped point-only area is reported, and is no change by itself"):
    val c = LoadCounts(
      LoadCounts.Source("wikidata"),
      LoadCounts.Version("v1"),
      records = LoadCounts.Records(3),
      pointAreasSkipped = LoadCounts.PointAreasSkipped(2)
    )
    assert(c.unchanged)
    assert(c.describe.contains("2 point-only region(s)/polity(ies) not kept as places"), c.describe)
    assertEquals((c + c).pointAreasSkipped, 4L)
