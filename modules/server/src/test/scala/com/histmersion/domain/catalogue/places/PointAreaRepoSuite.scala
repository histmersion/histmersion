package com.histmersion
package domain.catalogue.places

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.util.database.given

import com.histmersion.api.GeoPoint
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.catalogue.places.PointAreas.Dating

import com.augustnagro.magnum.*
import com.histmersion.util.database.Migrate
import com.histmersion.db.*
import com.histmersion.api.catalogue.{Gazetteer, Place}
import com.histmersion.api.checks.Lint
import com.histmersion.api.library.{Finding, Source}
import com.histmersion.db.DbSuite
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.util.database.Transactor
import com.histmersion.util.time.SystemTimer
import com.histmersion.util.database.TestPermits.given
import com.histmersion.lint.{LintRule, tolerated}

/** `lint --rule place-imported-point-area` (2026-10-09), against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped
  * otherwise): the listing (counts, every finding holder, evidence keeping a place) and `--fix` — bounded per call,
  * every dependent row gone, the affected findings matched again, a shape, a site and a seed place untouched. Fixtures
  * around 150°E 60°S, where no other suite has any; ids `wikidata_q9992…`, `geonames_9992…`, `zzpa…`.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PointAreaRepoSuite extends DbSuite with db.Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zzpa%'".update.run()
    val _ = sql"DELETE FROM place_aliases WHERE alias LIKE 'zzpa%'".update.run()
    val _ =
      sql"DELETE FROM places WHERE id LIKE 'wikidata_q9992%' OR id LIKE 'geonames_9992%' OR id LIKE 'zzpa%'".update
        .run()

  override def beforeEach(context: BeforeEach): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private val Site = "zzpa_site" // a seed site on the region's point
  private val Region = "wikidata_q999200001" // holds finding 1, which Site holds too
  private val Polity = "wikidata_q999200002" // dated; the ONLY place of finding 2
  private val Pinned = "wikidata_q999200003" // an attestation names it
  private val Shaped = "wikidata_q999200004" // a polygon: a real region
  private val GnArea = "geonames_999200005" // names, links, a description, an alias, a relation
  private val GnSite = "geonames_999200006" // a site: a point by nature
  private val Thrace = "wikidata_q999200007" // labelled "Zzpa Thrace": the geocoder's choice for that name
  private val Thracia = "zzpa_thracia" // a seed region known as "Zzpa Thrace" too
  private val Eastern = "geonames_999200008" // labelled "Eastern Zzpa Thracia": that name's first reading
  private val Curated = "wikidata_q999200009" // a person set its kind (`places set`, V080 place_overrides)
  private val Mine = List(Region, Polity, Pinned, Shaped, GnArea, GnSite, Thrace, Thracia, Eastern, Curated)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def seed(): Unit =
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        Place.Input(
          Place(
            Place.Id(Site),
            Place.Label("Zzpa Site"),
            Place.KindValue(Place.Kind.Site),
            Some(Place.Geometry("""{"type":"Point","coordinates":[150.0,-60.0]}"""))
          ),
          List(Place.Name(Place.Name.Value("Zzpa Site")))
        ),
        Place.Input(
          Place(
            Place.Id(Thracia),
            Place.Label("Zzpa Thracia"),
            Place.KindValue(Place.Kind.Region),
            Some(Place.Geometry("""{"type":"Point","coordinates":[150.8,-60.2]}"""))
          ),
          List(Place.Name(Place.Name.Value("Zzpa Thracia")), Place.Name(Place.Name.Value("Zzpa Thrace")))
        )
      )
    )
    connect(ds):
      def imported(id: String, label: String, kind: String, wkt: String, src: String, period: String = "(,)"): Unit =
        val _ =
          sql"""INSERT INTO places (id, label, kind, geom, period, dataset, import_source, import_ref, import_version, creator)
                      VALUES ($id, $label, $kind, ST_GeomFromText($wkt, 4326), $period::int8range, 'seed', $src,
                              $src || ':' || $id, 'v1', jsonb_build_object('kind', 'unknown'))""".update.run()
      imported(Region, "Zzpa Region", domain.catalogue.Place.Kind.Region, "POINT(150 -60)", "wikidata")
      imported(
        Polity,
        "Zzpa Kingdom",
        domain.catalogue.Place.Kind.Polity,
        "POINT(150.5 -60)",
        "wikidata",
        "[-900,-699)"
      )
      imported(Pinned, "Zzpa Pinned", domain.catalogue.Place.Kind.Region, "POINT(151 -60)", "wikidata")
      imported(
        Shaped,
        "Zzpa Shaped",
        domain.catalogue.Place.Kind.Region,
        "POLYGON((150 -61,151 -61,151 -60.5,150 -61))",
        "wikidata"
      )
      imported(
        GnArea,
        "Zzpa Gn Area",
        domain.catalogue.Place.Kind.Region,
        "POINT(151.5 -60)",
        "geonames"
      )
      imported(GnSite, "Zzpa Gn Site", domain.catalogue.Place.Kind.Site, "POINT(152 -60)", "geonames")
      imported(
        Thrace,
        "Zzpa Thrace",
        domain.catalogue.Place.Kind.Region,
        "POINT(151.2 -60.2)",
        "wikidata"
      )
      imported(
        Eastern,
        "Eastern Zzpa Thracia",
        domain.catalogue.Place.Kind.Region,
        "POINT(151.4 -60.2)",
        "geonames"
      )
      imported(
        Curated,
        "Zzpa Curated",
        domain.catalogue.Place.Kind.Region,
        "POINT(152.5 -60)",
        "wikidata"
      )
      val _ = sql"""INSERT INTO place_overrides (place_id, kind, reason, source_kind, source_period, creator)
                    VALUES ($Curated, 'region', 'a person decided', 'region', '(,)'::int8range, jsonb_build_object('kind', 'unknown'))""".update
        .run()
      val _ = sql"""INSERT INTO place_names (place_id, name, lang, import_source, import_ref, import_version)
                    VALUES ($GnArea, 'Zzpa Gn Area', '', 'geonames', 'geonames:999200005', 'v1')""".update.run()
      val _ = sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, import_source, import_ref)
                    VALUES ($GnArea, 'geonames', '999200005', 'exact_match', 'geonames', 'geonames:999200005')""".update
        .run()
      val _ = sql"""INSERT INTO place_descriptions (place_id, lang, text, import_source, import_ref, import_version)
                    VALUES ($GnArea, 'en', 'a region', 'geonames', 'geonames:999200005', 'v1')""".update.run()
      val _ = sql"INSERT INTO place_aliases (alias, place_id) VALUES ('zzpa_old', $GnArea)".update.run()
      val _ =
        sql"""INSERT INTO place_relations (child_id, parent_id, kind) VALUES ($Site, $GnArea, 'part_of')""".update.run()
    def f(n: Int, at: GeoPoint, name: Finding.Region = Finding.Region("somewhere")) =
      Finding(
        Finding.Id(s"zzpa_src#$n"),
        Citekey("zzpa_src"),
        Finding.Claim(s"claim $n"),
        Some(name),
        Some(at),
        None,
        None,
        Nil,
        None
      )
    SourceFixtures.upsert(
      ds,
      Source(
        citekey = Citekey("zzpa_src"),
        title = api.library.Title("Point area fixture"),
        authors = (List("T")).map(api.library.AuthorName.apply),
        doi = None,
        year = (Some(2026)).map(Year.apply),
        venue = None,
        status = Source.Status.Current,
        topics = Nil,
        regions = Nil,
        tags = Nil,
        period = None,
        summary = None,
        citedByCount = None,
        influentialCitations = None,
        provenance = None,
        calibreId = None,
        findings = List(
          f(1, GeoPoint(Longitude(150.0), Latitude(-60.0))),
          f(2, GeoPoint(Longitude(150.5), Latitude(-60.0))),
          f(3, GeoPoint(Longitude(150.95), Latitude(-60.6)), Finding.Region("Zzpa Thrace")),
          f(4, GeoPoint(Longitude(150.9), Latitude(-60.7)), Finding.Region("Eastern Zzpa Thracia"))
        )
      ),
      cfg.limits.chunkChars,
      cfg.places.pointToleranceKm
    )
    connect(ds):
      val _ = sql"""INSERT INTO attestations (citekey, target_type, target_id, quote, creator)
                    VALUES ('zzpa_src', 'place', $Pinned, 'here', jsonb_build_object('kind', 'unknown'))""".update.run()

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def at(n: Int): Set[String] =
    PlaceFixtures
      .placesOf(ds, Finding.Id(s"zzpa_src#$n"))
      .map(_.id.value)
      .filter(id => Mine.contains(id) || id == Site)
      .toSet
  private def exists(id: String): Boolean =
    connect(ds)(sql"SELECT 1 FROM places WHERE id = $id".query[Int].run().nonEmpty)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val kinds =
    Gazetteer.DefaultPointAreaKinds.map(k => domain.catalogue.Place.Kind.Wire(k.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val rule = Lint.rule(Lint.Rule.Name(Lint.PlaceImportedPointArea)).get
  private lazy val geometry = PlaceGeometry(Transactor(ds))(using new SystemTimer)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def settings(batch: PointAreaRepo.Settings.Batch, budgetMs: PointAreaRepo.Settings.BudgetMs) =
    PointAreaRepo.Settings(
      kinds.map(k => PointAreaRepo.Settings.Kinds(k.value)),
      batch,
      budgetMs,
      PointAreaRepo.Settings.ToleranceKm(cfg.places.pointToleranceKm.value)
    )

  test("the listing: counts by source, kind and dating; every holder, with what it alone holds; evidence keeps one"):
    seed()
    assertEquals(at(1), Set(Site, Region))
    assertEquals(at(2), Set(Polity), "an imported point takes the finding ON its point")
    assertEquals(
      at(3),
      Set.empty[String],
      "'Zzpa Thrace' resolves to the imported label, which takes no finding by name"
    )
    assertEquals(at(4), Set.empty[String], "'Eastern Zzpa Thracia' too, by its first reading")
    val holders = connect(ds)(PointAreaRepo.holdersIn(kinds))
      .filter(l => Mine.contains(l.id.value))
      .map(l => (l.id.value: String) -> l)
      .toMap
    assertEquals(holders.keySet, Set(Region, Polity, Pinned, Curated))
    assertEquals((holders(Region).findings, holders(Region).alone), (1, 0))
    assertEquals((holders(Polity).findings, holders(Polity).alone, holders(Polity).dating), (1, 1, Dating.Dated))
    assertEquals(holders(Pinned).pinned, 1)
    assertEquals(holders(Curated).pinned, 1, "a place override is a person's decision: listed, kept")
    val groups = connect(ds)(PointAreaRepo.groupsIn(kinds))
    assert(
      groups.exists(g =>
        g.source == PointAreas.Group.Source("geonames") && g.kind == PointAreas.Group.Kind(
          "region"
        ) && g.dating == Dating.Undated && g.places.value >= 1
      ),
      groups.toString
    )
    assert(
      groups.exists(g =>
        g.source == PointAreas.Group.Source("wikidata") && g.kind == PointAreas.Group.Kind(
          "polity"
        ) && g.dating == Dating.Dated && g.places.value >= 1
      ),
      groups.toString
    )
    val r = connect(ds)(PointAreaRepo.reportIn(rule, kinds, PointAreas.ReportMaxItems(50)))
    assert(r.count.value >= 4, r.toString)
    assert(
      r.items.exists(i => i.citekey == Citekey(Polity) && i.detail.value.contains("1 finding, at no other place")),
      r.items.toString
    )
    assert(
      !r.items.exists(i => i.citekey == Citekey(Shaped) || i.citekey == Citekey(GnSite) || i.citekey == Citekey(Site)),
      "a shape, a site and the seed are no point areas"
    )
    assertEquals(
      connect(ds)(PointAreaRepo.reportIn(rule, Nil, PointAreas.ReportMaxItems(50))).count,
      0,
      "no kinds, no rule"
    )
    assert(Mine.forall(exists), "a listing writes nothing")

  test(
    "--fix: bounded per call, dependents gone, findings matched again, a shape, a site, the seed and evidence untouched"
  ):
    seed()
    // a zero budget still makes progress: one batch per call
    val one = geometry.removePointAreas(
      settings(batch = PointAreaRepo.Settings.Batch(1), budgetMs = PointAreaRepo.Settings.BudgetMs(0))
    )
    assertEquals(one.removed, 1)
    assert(one.remaining.value >= 3, one.toString)
    val rest = geometry.removePointAreas(
      settings(batch = PointAreaRepo.Settings.Batch(2), budgetMs = PointAreaRepo.Settings.BudgetMs(60000))
    )
    assertEquals(rest.remaining, 0)
    assert(!exists(Region) && !exists(Polity) && !exists(GnArea))
    assert(exists(Pinned) && exists(Shaped) && exists(GnSite) && exists(Site) && exists(Thracia) && !exists(Thrace))
    assert(exists(Curated), "an overridden place is never removed")
    assertEquals(at(1), Set(Site), "finding 1 keeps its other place")
    assertEquals(at(2), Set.empty[String], "finding 2 had only the polity")
    assertEquals(
      at(3),
      Set(Thracia),
      "the name now resolves to the seed region, and the fix matched that finding again too"
    )
    assertEquals(
      at(4),
      Set(Thracia),
      "…and a name whose first reading named the removed place falls through to the seed's"
    )
    assert(
      one.findings.value + rest.findings.value >= 2 && one.sources.value + rest.sources.value >= 1,
      (one, rest).toString
    )
    val left = connect(ds)(
      sql"""SELECT (SELECT count(*) FROM place_names WHERE place_id = $GnArea)
                                      + (SELECT count(*) FROM place_links WHERE place_id = $GnArea)
                                      + (SELECT count(*) FROM place_descriptions WHERE place_id = $GnArea)
                                      + (SELECT count(*) FROM place_aliases WHERE place_id = $GnArea)
                                      + (SELECT count(*) FROM place_relations WHERE parent_id = $GnArea)
                                      + (SELECT count(*) FROM finding_places WHERE place_id = ANY(${List(
          Region,
          Polity
        )}::text[]))"""
        .query[Long]
        .run()
        .head
    )
    assertEquals(left, 0L)
    // the fix re-matched the affected source; a whole rebuild agrees with it
    val fixed = (1 to 4).map(at).toList
    val _ = PlaceFixtures.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)
    assertEquals((1 to 4).map(at).toList, fixed)
    assertEquals(
      geometry.removePointAreas(
        settings(batch = PointAreaRepo.Settings.Batch(10), budgetMs = PointAreaRepo.Settings.BudgetMs(1000))
      ),
      PointAreaRepo.Removal(
        PointAreaRepo.Removal.Removed(0),
        PointAreaRepo.Removal.Remaining(0),
        PointAreaRepo.Removal.Findings(0),
        PointAreaRepo.Removal.Sources(0)
      ),
      "nothing left to do"
    )

  test("the importer's prune passes over a place a person curated (place_overrides), as over evidence"):
    seed()
    val ids = connect(ds)(PointAreaRepo.ofSourceIn(PointAreas.Group.Source("wikidata"), kinds)).toSet
    assert(
      ids.contains(domain.catalogue.Place.Id.applyUnsafe(Region)) && ids.contains(
        domain.catalogue.Place.Id.applyUnsafe(Polity)
      ),
      ids.toString
    )
    assert(
      !ids.contains(domain.catalogue.Place.Id.applyUnsafe(Curated)) && !ids.contains(
        domain.catalogue.Place.Id.applyUnsafe(Pinned)
      ),
      ids.toString
    )
