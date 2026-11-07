package com.histmersion.domain.catalogue.places

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.util.database.given

import com.histmersion.api.{GeoPoint, Span}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.catalogue.Geocode.KindHint

import com.augustnagro.magnum.*
import com.histmersion.util.database.Migrate
import com.histmersion.db.*
import com.histmersion.api.catalogue.{
  GazDescription,
  Gazetteer,
  GazGeometry,
  GazName,
  GazRecord,
  Geocode,
  Latitude,
  LinkScheme,
  Longitude,
  Period,
  Place
}
import com.histmersion.api.library.{ExternalId, Finding, Source}

/** What an imported place's silence about time means (V074, 2026-10-09), against a real PostGIS
  * (HISTMERSION_TEST_DB_URL; skipped otherwise):
  *   - a Wikidata state with an end and no start (the Kingdom of Patones, dissolved 1769) is seen by every time filter
  *     only in the window before its end — the map feed, search, the geocoder and `finding_places` — and reported as
  *     "end 1769, start unknown";
  *   - an undated GeoNames place is findable by name at any time and never on a time-sliced map;
  *   - the seed's and an LPF dataset's open ranges are untouched;
  *   - the base map layers draw an imported place only once a finding is attached to it;
  *   - descriptions are stored per source and language, and a re-import is a diff;
  *   - V074's repair of stored rows and the importer agree (a re-run after it changes nothing).
  * Fixtures sit at 150°W 60°S, where no other suite has any; ids carry `99950` or `zzpt`. The real
  * `wikidata`/`geonames` source keys are written WITHOUT the sweep, so no other suite's rows go.
  */
class ImportedPlaceTimeSuite extends com.histmersion.db.DbSuite with com.histmersion.db.Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zzpt%'".update.run()
    val _ =
      sql"DELETE FROM places WHERE id LIKE 'wikidata_q99950%' OR id LIKE 'geonames_99950%' OR id LIKE 'zzpt%' OR id LIKE 'lpf_zzpt%'".update
        .run()
    val _ = sql"DELETE FROM place_names WHERE import_ref LIKE '%99950%' OR import_source LIKE 'lpf:zzpt%'".update.run()
    val _ = sql"DELETE FROM place_links WHERE external_id LIKE '99950%' OR external_id LIKE 'Q99950%'".update.run()
    val _ = sql"DELETE FROM place_descriptions WHERE import_ref LIKE '%99950%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private val Lon = -150.0
  private val Lat = -60.0
  private def pt(dx: Double = 0) = Some(GazGeometry.GeoJson(GazGeometry.GeoJson.Text(s"""{"type":"Point","coordinates":[${Lon + dx},$Lat]}""")))
  private def wd(q: ExternalId.Id) = ExternalId(LinkScheme.Wikidata, q)
  private def gn(n: ExternalId.Id) = ExternalId(LinkScheme.Geonames, n)

  private def rec(
      own: ExternalId,
      label: GazRecord.Label,
      kind: GazRecord.Kind,
      span: Span,
      dx: Double = 0,
      descriptions: List[GazDescription] = Nil
  ): GazRecord =
    GazRecord(
      GazRecord.Ref(own.key.value),
      Some(own),
      Place.Id(own.placeId.value),
      label,
      kind,
      pt(dx),
      span,
      Gazetteer.mergeNames(List(GazName(GazName.Name(label.value)))),
      Nil,
      Some(com.histmersion.api.catalogue.GazRecord.Source("fixture")),
      None,
      descriptions
    )

  /** One batch of `source` WITHOUT the sweep (the real source keys hold other suites' rows). The fixtures are
    * point-only polities — what the importer no longer keeps since 2026-10-09 ([[Gazetteer.dropsPointArea]], tested in
    * GazetteerRepoSuite) — so the rule is off here: these tests are about time, which applies to every imported place,
    * a site's as much as a state's.
    */
  private val Wikidata = Gazetteer.SourceInfo.Key("wikidata")
  private val Geonames = Gazetteer.SourceInfo.Key("geonames")
  private val LpfZzpt = Gazetteer.SourceInfo.Key("lpf:zzpt")
  private val V1 = Gazetteer.LoadCounts.Version("v1")
  private val V2 = Gazetteer.LoadCounts.Version("v2")

  private def load(source: Gazetteer.SourceInfo.Key, version: Gazetteer.LoadCounts.Version, rs: List[GazRecord]): Gazetteer.LoadCounts =
    connect(ds)(GazetteerImport.session(source, version, apply = true, pointAreaKinds = Set.empty).batch(rs))

  private val Patones = rec(
    wd(ExternalId.Id("Q999500001")),
    GazRecord.Label("Zzpt Kingdom of Patones"),
    GazRecord.Kind(Place.Kind.Polity),
    Span(None, Some(com.histmersion.api.catalogue.Year(1769)))
  )
  private val PatonesId = com.histmersion.domain.catalogue.Place.Id("wikidata_q999500001")
  private val PatonesHit = Place.Id("wikidata_q999500001")
  private val Src = com.histmersion.api.library.Citekey("zzpt_src")
  private val PolityKind = com.histmersion.domain.catalogue.Place.Kind.Wire("polity")
  private val RegionKind = com.histmersion.domain.catalogue.Place.Kind.Wire("region")
  private val SiteKind = com.histmersion.domain.catalogue.Place.Kind.Wire("site")
  private val PatonesQuery = PlaceRepo.SearchText("Zzpt Kingdom of Patones")
  private val BogazQuery = PlaceRepo.SearchText("Zzpt Bogazkale")
  private val LpfQuery = PlaceRepo.SearchText("Zzpt Lpf State")

  /** A source whose findings sit ON the fixtures' point, one per period (materialised on write). */
  private def findingsAt(ck: com.histmersion.api.library.Citekey, periods: Period*): Unit =
    SourceFixtures.upsert(
      ds,
      Source(
        citekey = ck,
        title = com.histmersion.api.library.Title("Imported place fixture"),
        authors = (List("T")).map(com.histmersion.api.library.AuthorName.apply),
        doi = None,
        year = (Some(2026)).map(com.histmersion.api.catalogue.Year.apply),
        venue = None,
        status = com.histmersion.api.library.Source.Status.Current,
        topics = Nil,
        regions = Nil,
        tags = Nil,
        period = None,
        summary = None,
        citedByCount = None,
        influentialCitations = None,
        provenance = None,
        calibreId = None,
        findings = periods.toList.zipWithIndex.map((p, i) =>
          Finding(Finding.Id(s"$ck#$i"), ck, Finding.Claim(s"claim $i"), Some(com.histmersion.api.library.Finding.Region("Zzpt")), Some(GeoPoint(Longitude(Lon), Latitude(Lat))), Some(p), None, Nil, None)
        )
      ),
      cfg.limits.chunkChars,
      cfg.places.pointToleranceKm
    )

  private def onMap(kind: com.histmersion.domain.catalogue.Place.Kind.Wire, asOf: Option[DeepTime.SignedYear]): Set[String] =
    "\"id\":\"((?:wikidata_q|geonames_)99950[0-9]+|zzpt[a-z_]*|lpf_zzpt[a-z0-9_]*)\"".r
      .findAllMatchIn(PlaceFixtures.geoJson(ds, Some(kind), asOf, com.histmersion.domain.catalogue.places.PlaceRepo.Limit(100000)).value)
      .map(_.group(1))
      .toSet

  private def found(q: PlaceRepo.SearchText, asOf: Option[DeepTime.SignedYear]): Set[Place.Id] =
    PlaceFixtures.search(ds, Some(q), asOf, com.histmersion.domain.catalogue.places.PlaceRepo.Limit(50)).map(_.id).toSet

  test("an end-only Wikidata place is stored as a window before its end, and reported as the source states it"):
    val c = load(Wikidata, V1, List(Patones))
    assertEquals(c.created, 1L)
    val stored = connect(ds)(
      sql"SELECT lower(period), upper(period) - 1, start_unknown FROM places WHERE id = ${PatonesId.value}"
        .query[(Option[Long], Option[Long], Boolean)]
        .run()
        .head
    )
    assertEquals(stored, (Some(1769L - Gazetteer.DefaultOpenStartWindowYears.value), Some(1769L), true))
    val d = PlaceFixtures.get(ds, PatonesId).get
    assertEquals((d.place.periodStart, d.place.periodEnd, d.place.dating), (None, Some(1769L), Place.Dating.EndOnly))
    assertEquals(d.place.when, "end 1769 CE, start unknown")
    assertEquals(d.place.importSource, Some("wikidata"))
    assert(!d.hasBoundary.value, "a point")

  test("time filters see the end-only place only in its window: search, the map feed, finding_places"):
    load(Wikidata, V1, List(Patones))
    assert(!found(PatonesQuery, Some(DeepTime.SignedYear(-4000L))).contains(PatonesHit), "4000 BCE: not 'since forever'")
    assert(found(PatonesQuery, Some(DeepTime.SignedYear(1600L))).contains(PatonesHit))
    assert(found(PatonesQuery, None).contains(PatonesHit), "no time: by name")
    // the 4664–3153 BCE finding the user saw it beside is NOT assigned to it; the 1600s one is
    findingsAt(Src, Period(com.histmersion.api.catalogue.Year(-4664), com.histmersion.api.catalogue.Year(-3153)), Period(com.histmersion.api.catalogue.Year(1600), com.histmersion.api.catalogue.Year(1650)))
    assert(!PlaceFixtures.placesOf(ds, com.histmersion.api.library.Finding.Id("zzpt_src#0")).exists(_.id == PatonesHit))
    assert(PlaceFixtures.placesOf(ds, com.histmersion.api.library.Finding.Id("zzpt_src#1")).exists(_.id == PatonesHit))
    assertEquals(onMap(PolityKind, Some(DeepTime.SignedYear(-3900L))), Set.empty[String])
    assertEquals(onMap(PolityKind, Some(DeepTime.SignedYear(1620L))), Set(PatonesId))
    assert(PlaceFixtures.geoJson(ds, Some(com.histmersion.domain.catalogue.Place.Kind.Wire("polity")), Some(com.histmersion.domain.DeepTime.SignedYear(1620L)), com.histmersion.domain.catalogue.places.PlaceRepo.Limit(100000)).value.contains("\"hasBoundary\":false"))

  test("the geocoder: the end-only place loses to an undated namesake outside its window, wins inside it"):
    load(Wikidata, V1, List(Patones))
    load(
      Geonames,
      V1,
      List(
        rec(
          gn(ExternalId.Id("999500002")),
          GazRecord.Label("Zzpt Kingdom of Patones"),
          GazRecord.Kind(Place.Kind.Polity),
          Span.always,
          dx = 0.5
        )
      )
    )
    def at(p: Period) =
      connect(ds)(
        PlaceFixtures.resolve(com.histmersion.api.catalogue.Geocode.Attempt.Name("Zzpt Kingdom of Patones"), KindHint.Unknown, Some(p), GeocodeSettings.PlaceholderMin(cfg.places.geocodePlaceholderMin.value))
      )
        .map(_.placeId)
    // the candidate carries the WINDOW, so the time rule reads it; who brought a place in (the tier)
    // still ranks before time, so the time rule is compared at one tier
    val cands = connect(ds)(PlaceFixtures.candidates(Geocode.key("Zzpt Kingdom of Patones"), None))
    assertEquals(cands.find(_.id == PatonesHit).map(c => (c.from, c.to)), Some((Some(1469L), Some(1769L))))
    val ranked = Geocode.rank(cands.map(_.modify(_.tier).setTo(Gazetteer.Rank(0))), KindHint.Unknown, Some(Period(com.histmersion.api.catalogue.Year(-4664), com.histmersion.api.catalogue.Year(-3153))), com.histmersion.api.catalogue.Geocode.Placeholder.Min(4))
    assertEquals(ranked.head.id, "geonames_999500002", "outside its window the dated place ranks after the undated one")
    val inside = Geocode.rank(cands.map(_.modify(_.tier).setTo(Gazetteer.Rank(0))), KindHint.Unknown, Some(Period(com.histmersion.api.catalogue.Year(1600), com.histmersion.api.catalogue.Year(1650))), com.histmersion.api.catalogue.Geocode.Placeholder.Min(4))
    assertEquals(inside.head.id, PatonesId)
    assertEquals(at(Period(com.histmersion.api.catalogue.Year(1600), com.histmersion.api.catalogue.Year(1650))), Some(PatonesId))
    assertEquals(PlaceFixtures.get(ds, com.histmersion.domain.catalogue.Place.Id("geonames_999500002")).map(_.place.dating), Some(Place.Dating.Unknown))

  test(
    "an undated GeoNames place: found by name with no time, at a time only by a finding dated then, never on a time-sliced map"
  ):
    val g = rec(gn(ExternalId.Id("999500003")), GazRecord.Label("Zzpt Bogazkale"), GazRecord.Kind(Place.Kind.Site), Span.always)
    load(Geonames, V1, List(g))
    val id = com.histmersion.domain.catalogue.Place.Id("geonames_999500003")
    val hit = Place.Id("geonames_999500003")
    assertEquals(
      PlaceFixtures.get(ds, id).map(p => (p.place.dating, p.place.when)),
      Some((Place.Dating.Unknown, "date unknown"))
    )
    assert(found(BogazQuery, None).contains(hit), "picking with no time finds it")
    // the time rule (2026-10-09): an undated place is in a time view only by a finding dated then
    assert(!found(BogazQuery, Some(DeepTime.SignedYear(-1400L))).contains(hit), "undated, no finding: not at 1400 BCE")
    assertEquals(onMap(SiteKind, None), Set.empty[String], "no finding: not on the base layer")
    findingsAt(Src, Period(com.histmersion.api.catalogue.Year(-1400), com.histmersion.api.catalogue.Year(-1300)))
    assertEquals(onMap(SiteKind, None), Set(id), "a finding is attached: drawn")
    assert(found(BogazQuery, Some(DeepTime.SignedYear(-1350L))).contains(hit), "a finding dated then: found")
    assertEquals(onMap(SiteKind, Some(DeepTime.SignedYear(-1350L))), Set.empty[String], "never on a time-sliced map")

  test("the seed's and an LPF dataset's open ranges are untouched"):
    val seedRegion = Place.Input(
      Place(
        com.histmersion.api.catalogue.Place.Id("zzpt_region"),
        com.histmersion.api.catalogue.Place.Label("Zzpt Region"),
        Place.KindValue(Place.Kind.Region),
        Some(Place.Geometry(
          s"""{"type":"Polygon","coordinates":[[[${Lon - 1},${Lat - 1}],[${Lon + 1},${Lat - 1}],[${Lon + 1},${Lat + 1}],[${Lon - 1},${Lat + 1}],[${Lon - 1},${Lat - 1}]]]}"""
        )),
        None,
        Some(com.histmersion.api.catalogue.Place.Source("fixture")),
        None
      ),
      List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzpt Region")))
    )
    val _ = PlaceFixtures.upsertPlaces(ds, List(seedRegion))
    val lpf = GazRecord(
      com.histmersion.api.catalogue.GazRecord.Ref("lpf:zzpt:1"),
      None,
      com.histmersion.api.catalogue.Place.Id("lpf_zzpt_1"),
      com.histmersion.api.catalogue.GazRecord.Label("Zzpt Lpf State"),
      GazRecord.Kind(Place.Kind.Polity),
      pt(),
      Span(None, Some(com.histmersion.api.catalogue.Year(1769))),
      Gazetteer.mergeNames(List(GazName(com.histmersion.api.catalogue.GazName.Name("Zzpt Lpf State")))),
      Nil,
      None,
      None
    )
    load(LpfZzpt, V1, List(lpf))
    // an undated seed region is in a time view only by a finding dated then (the time rule)
    assertEquals(onMap(RegionKind, Some(DeepTime.SignedYear(-4000L))), Set.empty[String])
    assertEquals(onMap(RegionKind, None), Set("zzpt_region"))
    assert(found(LpfQuery, Some(DeepTime.SignedYear(-4000L))).contains(Place.Id("lpf_zzpt_1")), "an LPF 'until 1769' is as the dataset says")
    val d = PlaceFixtures.get(ds, com.histmersion.domain.catalogue.Place.Id("lpf_zzpt_1")).get.place
    assertEquals((d.periodStart, d.periodEnd, d.dating), (None, Some(1769L), Place.Dating.Stated))
    assert(PlaceFixtures.get(ds, com.histmersion.domain.catalogue.Place.Id("zzpt_region")).get.hasBoundary)
    assertEquals(PlaceFixtures.get(ds, com.histmersion.domain.catalogue.Place.Id("zzpt_region")).get.place.dating, Place.Dating.Stated)

  test("the base layers draw an imported place only once a finding is attached"):
    val dated = rec(
      wd(ExternalId.Id("Q999500004")),
      GazRecord.Label("Zzpt Dated Site"),
      GazRecord.Kind(Place.Kind.Site),
      Span(Some(com.histmersion.api.catalogue.Year(-1500)), Some(com.histmersion.api.catalogue.Year(-1200)))
    )
    load(Wikidata, V1, List(dated))
    assertEquals(onMap(SiteKind, Some(DeepTime.SignedYear(-1300L))), Set.empty[String])
    findingsAt(Src, Period(com.histmersion.api.catalogue.Year(-1350), com.histmersion.api.catalogue.Year(-1300)))
    assertEquals(onMap(SiteKind, Some(DeepTime.SignedYear(-1300L))), Set("wikidata_q999500004"))

  test(
    "descriptions: per source and language; a re-run changes nothing; a new version updates only descriptions and dates"
  ):
    val v1 = Patones.modify(_.descriptions).setTo(List(
        GazDescription(GazDescription.Text("former country in Modern Day Spain"), GazDescription.Lang("en")),
        GazDescription(GazDescription.Text("microestado de Europa (siglo VIII-1769)"), GazDescription.Lang("es"))
      )
    )
    assert(!load(Wikidata, V1, List(v1)).unchanged)
    assert(load(Wikidata, V1, List(v1)).unchanged, "a re-run of the same version")
    val d = PlaceFixtures.get(ds, PatonesId).get
    assertEquals(
      d.descriptions.map(x => (x.lang, x.text)),
      List("en" -> "former country in Modern Day Spain", "es" -> "microestado de Europa (siglo VIII-1769)")
    )
    assertEquals(d.descriptions.map(_.source).distinct, List("wikidata"))
    val v2 = v1.modify(
      _.span).setTo(Span(None, Some(com.histmersion.api.catalogue.Year(1750)))).modify(_.descriptions).setTo(List(GazDescription(GazDescription.Text("former microstate in Spain"), GazDescription.Lang("en")), v1.descriptions(1))
    )
    val c = load(Wikidata, V2, List(v2))
    assertEquals((c.created, c.placesChanged, c.descriptionsAdded, c.descriptionsRemoved), (0L, 1L, 0L, 0L))
    assertEquals(c.descriptionsChanged, 2L, "the new text, and the version on the other")
    val rows =
      connect(ds)(sql"SELECT count(*)::int FROM place_descriptions WHERE place_id = ${PatonesId.value}".query[Int].run().head)
    assertEquals(rows, 2, "no duplicates")
    assertEquals(PlaceFixtures.get(ds, PatonesId).get.place.periodEnd, Some(1750L))
    assertEquals(
      connect(ds)(sql"SELECT count(*)::int FROM places WHERE import_ref = 'wikidata:Q999500001'".query[Int].run().head),
      1
    )

  // ── imported places are no part of the hierarchy and get no findings by proximity ──────────────

  test("a filter's scope, a place's children and the tree leave GeoNames/Wikidata places out"):
    val region = Place.Input(
      Place(
        com.histmersion.api.catalogue.Place.Id("zzpt_land"),
        com.histmersion.api.catalogue.Place.Label("Zzpt Land"),
        Place.KindValue(Place.Kind.Region),
        Some(Place.Geometry(
          s"""{"type":"Polygon","coordinates":[[[${Lon - 2},${Lat - 2}],[${Lon + 2},${Lat - 2}],[${Lon + 2},${Lat + 2}],[${Lon - 2},${Lat + 2}],[${Lon - 2},${Lat - 2}]]]}"""
        )),
        None,
        Some(com.histmersion.api.catalogue.Place.Source("fixture")),
        None
      ),
      List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzpt Land")))
    )
    val town = Place.Input(
      Place(
        com.histmersion.api.catalogue.Place.Id("zzpt_town"),
        com.histmersion.api.catalogue.Place.Label("Zzpt Town"),
        Place.KindValue(Place.Kind.Site),
        Some(Place.Geometry(s"""{"type":"Point","coordinates":[${Lon + 1},$Lat]}""")),
        None,
        Some(com.histmersion.api.catalogue.Place.Source("fixture")),
        None
      ),
      List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzpt Town"))),
      List(Place.Relation(com.histmersion.api.catalogue.Place.Relation.ChildId("zzpt_town"), com.histmersion.api.catalogue.Place.Relation.ParentId("zzpt_land")))
    )
    val _ = PlaceFixtures.upsertPlaces(ds, List(region, town))
    load(
      Geonames,
      V1,
      List(
        rec(gn(ExternalId.Id("999500010")), GazRecord.Label("Zzpt Village"), GazRecord.Kind(Place.Kind.Site), Span.always, dx = 0.5)
      )
    )
    // an edge to an imported place (an old spatial-hierarchy run, a hand edit) is not followed
    connect(ds)(sql"""INSERT INTO place_relations (child_id, parent_id, kind, period)
                      VALUES ('geonames_999500010', 'zzpt_land', 'part_of', int8range(NULL, NULL))""".update.run())
    val scope = PlaceFixtures.scopeFor(ds, List(com.histmersion.api.library.Filter.Places("zzpt_land")), None)
    assertEquals(scope.idsFor(com.histmersion.domain.catalogue.places.PlaceScope.Value("zzpt_land")).map(_.toSet), Some(Set("zzpt_land", "zzpt_town")))
    assertEquals(PlaceFixtures.children(ds, com.histmersion.domain.catalogue.Place.Id("zzpt_land"), None, com.histmersion.domain.catalogue.places.PlaceRepo.Limit(100)).map(_.id).toSet, Set("zzpt_town"))
    assertEquals(
      PlaceFixtures.hierarchy(ds, None, Some(com.histmersion.domain.catalogue.Place.Id("zzpt_land")), com.histmersion.domain.catalogue.places.PlaceRepo.Depth(3), com.histmersion.domain.catalogue.places.PlaceRepo.Limit(100)).map(_.id).toSet,
      Set("zzpt_land", "zzpt_town")
    )
    // a picked imported place still scopes to itself
    assertEquals(
      PlaceFixtures.scopeFor(ds, List(com.histmersion.api.library.Filter.Places("geonames_999500010")), None).idsFor(com.histmersion.domain.catalogue.places.PlaceScope.Value("geonames_999500010")),
      Some(List("geonames_999500010"))
    )

  test(
    "an imported point gets only the finding the geocoder put ON it; a seed point within the tolerance gets its neighbours"
  ):
    load(
      Wikidata,
      V1,
      List(
        rec(
          wd(ExternalId.Id("Q999500011")),
          GazRecord.Label("Zzpt Near"),
          GazRecord.Kind(Place.Kind.Site),
          Span(Some(com.histmersion.api.catalogue.Year(-2000)), Some(com.histmersion.api.catalogue.Year(2000))),
          dx = 0.02
        ),
        rec(
          wd(ExternalId.Id("Q999500012")),
          GazRecord.Label("Zzpt On"),
          GazRecord.Kind(Place.Kind.Site),
          Span(Some(com.histmersion.api.catalogue.Year(-2000)), Some(com.histmersion.api.catalogue.Year(2000)))
        )
      )
    )
    val seedPt = Place.Input(
      Place(
        com.histmersion.api.catalogue.Place.Id("zzpt_seed_site"),
        com.histmersion.api.catalogue.Place.Label("Zzpt Seed Site"),
        Place.KindValue(Place.Kind.Site),
        Some(Place.Geometry(s"""{"type":"Point","coordinates":[${Lon + 0.02},$Lat]}""")),
        None,
        Some(com.histmersion.api.catalogue.Place.Source("fixture")),
        None
      ),
      Nil
    )
    val _ = PlaceFixtures.upsertPlaces(ds, List(seedPt))
    findingsAt(Src, Period(com.histmersion.api.catalogue.Year(-1000), com.histmersion.api.catalogue.Year(-900))) // ON the fixtures' point; ~1.1 km from the other two
    val got = PlaceFixtures.placesOf(ds, com.histmersion.api.library.Finding.Id("zzpt_src#0")).map(_.id).toSet
    assert(got.contains(com.histmersion.api.catalogue.Place.Id("wikidata_q999500012")), s"the place the finding sits on: $got")
    assert(!got.contains(com.histmersion.api.catalogue.Place.Id("wikidata_q999500011")), s"an imported neighbour 1 km off: $got")
    assert(got.contains(com.histmersion.api.catalogue.Place.Id("zzpt_seed_site")), s"a seed site within the tolerance: $got")
