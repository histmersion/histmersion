package com.histmersion.domain.catalogue.places

import com.histmersion.domain.library.sources.SourceRepo

import com.histmersion.db.*
import com.histmersion.db.given

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Geocode.KindHint

import com.augustnagro.magnum.*
import com.histmersion.values.{PlaceKind, SourceStatus}

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
    val d = testPool(cfg.db.copy(url = testDb.get))
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
  private def pt(dx: Double = 0) = Some(GazGeometry.GeoJson(s"""{"type":"Point","coordinates":[${Lon + dx},$Lat]}"""))
  private def wd(q: String) = ExternalId(LinkScheme.Wikidata, q)
  private def gn(n: String) = ExternalId(LinkScheme.Geonames, n)

  private def rec(
      own: ExternalId,
      label: String,
      kind: String,
      span: Span,
      dx: Double = 0,
      descriptions: List[GazDescription] = Nil
  ): GazRecord =
    GazRecord(
      own.key,
      Some(own),
      own.placeId,
      label,
      kind,
      pt(dx),
      span,
      Gazetteer.mergeNames(List(GazName(label))),
      Nil,
      Some("fixture"),
      None,
      descriptions
    )

  /** One batch of `source` WITHOUT the sweep (the real source keys hold other suites' rows). The fixtures are
    * point-only polities — what the importer no longer keeps since 2026-10-09 ([[Gazetteer.dropsPointArea]], tested in
    * GazetteerRepoSuite) — so the rule is off here: these tests are about time, which applies to every imported place,
    * a site's as much as a state's.
    */
  private def load(source: String, version: String, rs: List[GazRecord]): GazetteerLoadCounts =
    connect(ds)(GazetteerRepo.session(source, version, apply = true, pointAreaKinds = Set.empty).batch(rs))

  private val Patones = rec(wd("Q999500001"), "Zzpt Kingdom of Patones", PlaceKind.Polity, Span(None, Some(1769)))
  private val PatonesId = "wikidata_q999500001"

  /** A source whose findings sit ON the fixtures' point, one per period (materialised on write). */
  private def findingsAt(ck: String, periods: Period*): Unit =
    SourceRepo.upsert(
      ds,
      Source(
        citekey = ck,
        title = "Imported place fixture",
        authors = List("T"),
        doi = None,
        year = Some(2026),
        venue = None,
        status = SourceStatus.Current,
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
          Finding(s"$ck#$i", ck, s"claim $i", Some("Zzpt"), Some(GeoPoint(Lon, Lat)), Some(p), None, Nil, None)
        )
      ),
      cfg.limits.chunkChars,
      cfg.places.pointToleranceKm
    )

  private def onMap(kind: String, asOf: Option[Long]): Set[String] =
    "\"id\":\"((?:wikidata_q|geonames_)99950[0-9]+|zzpt[a-z_]*|lpf_zzpt[a-z0-9_]*)\"".r
      .findAllMatchIn(PlaceFixtures.geoJson(ds, Some(kind), asOf, 100000))
      .map(_.group(1))
      .toSet

  private def found(q: String, asOf: Option[Long]): Set[String] =
    PlaceFixtures.search(ds, Some(q), asOf, 50).map(_.id).toSet

  test("an end-only Wikidata place is stored as a window before its end, and reported as the source states it"):
    val c = load("wikidata", "v1", List(Patones))
    assertEquals(c.created, 1L)
    val stored = connect(ds)(
      sql"SELECT lower(period), upper(period) - 1, start_unknown FROM places WHERE id = $PatonesId"
        .query[(Option[Long], Option[Long], Boolean)]
        .run()
        .head
    )
    assertEquals(stored, (Some(1769L - Gazetteer.DefaultOpenStartWindowYears), Some(1769L), true))
    val d = PlaceFixtures.get(ds, PatonesId).get
    assertEquals((d.place.periodStart, d.place.periodEnd, d.place.dating), (None, Some(1769L), PlaceDating.EndOnly))
    assertEquals(d.place.when, "end 1769 CE, start unknown")
    assertEquals(d.place.importSource, Some("wikidata"))
    assert(!d.hasBoundary, "a point")

  test("time filters see the end-only place only in its window: search, the map feed, finding_places"):
    load("wikidata", "v1", List(Patones))
    assert(!found("Zzpt Kingdom of Patones", Some(-4000L)).contains(PatonesId), "4000 BCE: not 'since forever'")
    assert(found("Zzpt Kingdom of Patones", Some(1600L)).contains(PatonesId))
    assert(found("Zzpt Kingdom of Patones", None).contains(PatonesId), "no time: by name")
    // the 4664–3153 BCE finding the user saw it beside is NOT assigned to it; the 1600s one is
    findingsAt("zzpt_src", Period(-4664, -3153), Period(1600, 1650))
    assert(!PlaceFixtures.placesOf(ds, "zzpt_src#0").exists(_.id == PatonesId))
    assert(PlaceFixtures.placesOf(ds, "zzpt_src#1").exists(_.id == PatonesId))
    assertEquals(onMap("polity", Some(-3900L)), Set.empty[String])
    assertEquals(onMap("polity", Some(1620L)), Set(PatonesId))
    assert(PlaceFixtures.geoJson(ds, Some("polity"), Some(1620L), 100000).contains("\"hasBoundary\":false"))

  test("the geocoder: the end-only place loses to an undated namesake outside its window, wins inside it"):
    load("wikidata", "v1", List(Patones))
    load(
      "geonames",
      "v1",
      List(rec(gn("999500002"), "Zzpt Kingdom of Patones", PlaceKind.Polity, Span.always, dx = 0.5))
    )
    def at(p: Period) =
      connect(ds)(
        Geocoder.resolve("Zzpt Kingdom of Patones", KindHint.Unknown, Some(p), cfg.places.geocodePlaceholderMin)
      )
        .map(_.placeId)
    // the candidate carries the WINDOW, so the time rule reads it; who brought a place in (the tier)
    // still ranks before time, so the time rule is compared at one tier
    val cands = connect(ds)(Geocoder.candidates(Geocode.key("Zzpt Kingdom of Patones"), None))
    assertEquals(cands.find(_.id == PatonesId).map(c => (c.from, c.to)), Some((Some(1469L), Some(1769L))))
    val ranked = Geocode.rank(cands.map(_.copy(tier = 0)), KindHint.Unknown, Some(Period(-4664, -3153)), 4)
    assertEquals(ranked.head.id, "geonames_999500002", "outside its window the dated place ranks after the undated one")
    val inside = Geocode.rank(cands.map(_.copy(tier = 0)), KindHint.Unknown, Some(Period(1600, 1650)), 4)
    assertEquals(inside.head.id, PatonesId)
    assertEquals(at(Period(1600, 1650)), Some(PatonesId))
    assertEquals(PlaceFixtures.get(ds, "geonames_999500002").map(_.place.dating), Some(PlaceDating.Unknown))

  test(
    "an undated GeoNames place: found by name with no time, at a time only by a finding dated then, never on a time-sliced map"
  ):
    val g = rec(gn("999500003"), "Zzpt Bogazkale", PlaceKind.Site, Span.always)
    load("geonames", "v1", List(g))
    val id = "geonames_999500003"
    assertEquals(
      PlaceFixtures.get(ds, id).map(p => (p.place.dating, p.place.when)),
      Some((PlaceDating.Unknown, "date unknown"))
    )
    assert(found("Zzpt Bogazkale", None).contains(id), "picking with no time finds it")
    // the time rule (2026-10-09): an undated place is in a time view only by a finding dated then
    assert(!found("Zzpt Bogazkale", Some(-1400L)).contains(id), "undated, no finding: not at 1400 BCE")
    assertEquals(onMap("site", None), Set.empty[String], "no finding: not on the base layer")
    findingsAt("zzpt_src", Period(-1400, -1300))
    assertEquals(onMap("site", None), Set(id), "a finding is attached: drawn")
    assert(found("Zzpt Bogazkale", Some(-1350L)).contains(id), "a finding dated then: found")
    assertEquals(onMap("site", Some(-1350L)), Set.empty[String], "never on a time-sliced map")

  test("the seed's and an LPF dataset's open ranges are untouched"):
    val seedRegion = PlaceInput(
      Place(
        "zzpt_region",
        "Zzpt Region",
        PlaceKind.Region,
        Some(
          s"""{"type":"Polygon","coordinates":[[[${Lon - 1},${Lat - 1}],[${Lon + 1},${Lat - 1}],[${Lon + 1},${Lat + 1}],[${Lon - 1},${Lat + 1}],[${Lon - 1},${Lat - 1}]]]}"""
        ),
        None,
        Some("fixture"),
        None
      ),
      List(PlaceName("Zzpt Region"))
    )
    val _ = PlaceFixtures.upsertPlaces(ds, List(seedRegion))
    val lpf = GazRecord(
      "lpf:zzpt:1",
      None,
      "lpf_zzpt_1",
      "Zzpt Lpf State",
      PlaceKind.Polity,
      pt(),
      Span(None, Some(1769)),
      Gazetteer.mergeNames(List(GazName("Zzpt Lpf State"))),
      Nil,
      None,
      None
    )
    load("lpf:zzpt", "v1", List(lpf))
    // an undated seed region is in a time view only by a finding dated then (the time rule)
    assertEquals(onMap("region", Some(-4000L)), Set.empty[String])
    assertEquals(onMap("region", None), Set("zzpt_region"))
    assert(found("Zzpt Lpf State", Some(-4000L)).contains("lpf_zzpt_1"), "an LPF 'until 1769' is as the dataset says")
    val d = PlaceFixtures.get(ds, "lpf_zzpt_1").get.place
    assertEquals((d.periodStart, d.periodEnd, d.dating), (None, Some(1769L), PlaceDating.Stated))
    assert(PlaceFixtures.get(ds, "zzpt_region").get.hasBoundary)
    assertEquals(PlaceFixtures.get(ds, "zzpt_region").get.place.dating, PlaceDating.Stated)

  test("the base layers draw an imported place only once a finding is attached"):
    val dated = rec(wd("Q999500004"), "Zzpt Dated Site", PlaceKind.Site, Span(Some(-1500), Some(-1200)))
    load("wikidata", "v1", List(dated))
    assertEquals(onMap("site", Some(-1300L)), Set.empty[String])
    findingsAt("zzpt_src", Period(-1350, -1300))
    assertEquals(onMap("site", Some(-1300L)), Set("wikidata_q999500004"))

  test(
    "descriptions: per source and language; a re-run changes nothing; a new version updates only descriptions and dates"
  ):
    val v1 = Patones.copy(descriptions =
      List(
        GazDescription("former country in Modern Day Spain", "en"),
        GazDescription("microestado de Europa (siglo VIII-1769)", "es")
      )
    )
    assert(!load("wikidata", "v1", List(v1)).unchanged)
    assert(load("wikidata", "v1", List(v1)).unchanged, "a re-run of the same version")
    val d = PlaceFixtures.get(ds, PatonesId).get
    assertEquals(
      d.descriptions.map(x => (x.lang, x.text)),
      List("en" -> "former country in Modern Day Spain", "es" -> "microestado de Europa (siglo VIII-1769)")
    )
    assertEquals(d.descriptions.map(_.source).distinct, List("wikidata"))
    val v2 = v1.copy(
      span = Span(None, Some(1750)),
      descriptions = List(GazDescription("former microstate in Spain", "en"), v1.descriptions(1))
    )
    val c = load("wikidata", "v2", List(v2))
    assertEquals((c.created, c.placesChanged, c.descriptionsAdded, c.descriptionsRemoved), (0L, 1L, 0L, 0L))
    assertEquals(c.descriptionsChanged, 2L, "the new text, and the version on the other")
    val rows =
      connect(ds)(sql"SELECT count(*)::int FROM place_descriptions WHERE place_id = $PatonesId".query[Int].run().head)
    assertEquals(rows, 2, "no duplicates")
    assertEquals(PlaceFixtures.get(ds, PatonesId).get.place.periodEnd, Some(1750L))
    assertEquals(
      connect(ds)(sql"SELECT count(*)::int FROM places WHERE import_ref = 'wikidata:Q999500001'".query[Int].run().head),
      1
    )

  test("V074's repair of stored rows and the importer agree; a seed row with an open start is left alone"):
    load("wikidata", "v1", List(Patones))
    connect(ds):
      // as the 2026-10-08 import stored it, and a seed row that is open on purpose
      val _ =
        sql"UPDATE places SET period = int8range(NULL, 1770), start_unknown = false WHERE id = $PatonesId".update.run()
      val _ =
        sql"""INSERT INTO places (id, label, kind, period) VALUES ('zzpt_seed_open', 'Zzpt Seed Open', 'polity', int8range(NULL, 1770))""".update
          .run()
    val migration = String(
      getClass.getClassLoader.getResourceAsStream("db/migration/V074__imported_place_time.sql").readAllBytes(),
      java.nio.charset.StandardCharsets.UTF_8
    )
    connect(ds)(scala.util.Using.resource(summon[DbCon].connection.createStatement())(_.execute(migration)))
    assert(load("wikidata", "v1", List(Patones)).unchanged, "the repaired row is what the importer writes")
    val seed = connect(ds)(
      sql"SELECT lower_inf(period), start_unknown FROM places WHERE id = 'zzpt_seed_open'"
        .query[(Boolean, Boolean)]
        .run()
        .head
    )
    assertEquals(seed, (true, false))

  // ── imported places are no part of the hierarchy and get no findings by proximity ──────────────

  test("a filter's scope, a place's children and the tree leave GeoNames/Wikidata places out"):
    val region = PlaceInput(
      Place(
        "zzpt_land",
        "Zzpt Land",
        PlaceKind.Region,
        Some(
          s"""{"type":"Polygon","coordinates":[[[${Lon - 2},${Lat - 2}],[${Lon + 2},${Lat - 2}],[${Lon + 2},${Lat + 2}],[${Lon - 2},${Lat + 2}],[${Lon - 2},${Lat - 2}]]]}"""
        ),
        None,
        Some("fixture"),
        None
      ),
      List(PlaceName("Zzpt Land"))
    )
    val town = PlaceInput(
      Place(
        "zzpt_town",
        "Zzpt Town",
        PlaceKind.Site,
        Some(s"""{"type":"Point","coordinates":[${Lon + 1},$Lat]}"""),
        None,
        Some("fixture"),
        None
      ),
      List(PlaceName("Zzpt Town")),
      List(PlaceRelation("zzpt_town", "zzpt_land"))
    )
    val _ = PlaceFixtures.upsertPlaces(ds, List(region, town))
    load("geonames", "v1", List(rec(gn("999500010"), "Zzpt Village", PlaceKind.Site, Span.always, dx = 0.5)))
    // an edge to an imported place (an old spatial-hierarchy run, a hand edit) is not followed
    connect(ds)(sql"""INSERT INTO place_relations (child_id, parent_id, kind, period)
                      VALUES ('geonames_999500010', 'zzpt_land', 'part_of', int8range(NULL, NULL))""".update.run())
    val scope = PlaceFixtures.scopeFor(ds, List("zzpt_land"), None)
    assertEquals(scope.idsFor("zzpt_land").map(_.toSet), Some(Set("zzpt_land", "zzpt_town")))
    assertEquals(PlaceFixtures.children(ds, "zzpt_land", None, 100).map(_.id).toSet, Set("zzpt_town"))
    assertEquals(
      PlaceFixtures.hierarchy(ds, None, Some("zzpt_land"), 3, 100).map(_.id).toSet,
      Set("zzpt_land", "zzpt_town")
    )
    // a picked imported place still scopes to itself
    assertEquals(
      PlaceFixtures.scopeFor(ds, List("geonames_999500010"), None).idsFor("geonames_999500010"),
      Some(List("geonames_999500010"))
    )

  test(
    "an imported point gets only the finding the geocoder put ON it; a seed point within the tolerance gets its neighbours"
  ):
    load(
      "wikidata",
      "v1",
      List(
        rec(wd("Q999500011"), "Zzpt Near", PlaceKind.Site, Span(Some(-2000), Some(2000)), dx = 0.02),
        rec(wd("Q999500012"), "Zzpt On", PlaceKind.Site, Span(Some(-2000), Some(2000)))
      )
    )
    val seedPt = PlaceInput(
      Place(
        "zzpt_seed_site",
        "Zzpt Seed Site",
        PlaceKind.Site,
        Some(s"""{"type":"Point","coordinates":[${Lon + 0.02},$Lat]}"""),
        None,
        Some("fixture"),
        None
      ),
      Nil
    )
    val _ = PlaceFixtures.upsertPlaces(ds, List(seedPt))
    findingsAt("zzpt_src", Period(-1000, -900)) // ON the fixtures' point; ~1.1 km from the other two
    val got = PlaceFixtures.placesOf(ds, "zzpt_src#0").map(_.id).toSet
    assert(got.contains("wikidata_q999500012"), s"the place the finding sits on: $got")
    assert(!got.contains("wikidata_q999500011"), s"an imported neighbour 1 km off: $got")
    assert(got.contains("zzpt_seed_site"), s"a seed site within the tolerance: $got")
