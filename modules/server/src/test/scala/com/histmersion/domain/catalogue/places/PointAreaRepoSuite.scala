package com.histmersion.domain.catalogue.places

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.util.database.given

import com.histmersion.api.GeoPoint
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.catalogue.places.PointAreas.Dating

import com.augustnagro.magnum.*
import com.histmersion.util.database.Migrate
import com.histmersion.db.*
import com.histmersion.api.catalogue.{Gazetteer, Place}
import com.histmersion.api.checks.Lint
import com.histmersion.api.library.{Finding, Source}

/** `lint --rule place-imported-point-area` (2026-10-09), against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped
  * otherwise): the listing (counts, every finding holder, evidence keeping a place) and `--fix` — bounded per call,
  * every dependent row gone, the affected findings matched again, a shape, a site and a seed place untouched. Fixtures
  * around 150°E 60°S, where no other suite has any; ids `wikidata_q9992…`, `geonames_9992…`, `zzpa…`.
  */
class PointAreaRepoSuite extends com.histmersion.db.DbSuite with com.histmersion.db.Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(testDb.get))
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

  private def seed(): Unit =
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        Place.Input(
          Place(
            com.histmersion.api.catalogue.Place.Id(Site),
            "Zzpa Site",
            com.histmersion.domain.catalogue.Place.Kind.Site,
            Some("""{"type":"Point","coordinates":[150.0,-60.0]}""")
          ),
          List(Place.Name("Zzpa Site"))
        ),
        Place.Input(
          Place(
            com.histmersion.api.catalogue.Place.Id(Thracia),
            "Zzpa Thracia",
            com.histmersion.domain.catalogue.Place.Kind.Region,
            Some("""{"type":"Point","coordinates":[150.8,-60.2]}""")
          ),
          List(Place.Name("Zzpa Thracia"), Place.Name("Zzpa Thrace"))
        )
      )
    )
    connect(ds):
      def imported(id: String, label: String, kind: String, wkt: String, src: String, period: String = "(,)"): Unit =
        val _ =
          sql"""INSERT INTO places (id, label, kind, geom, period, dataset, import_source, import_ref, import_version)
                      VALUES ($id, $label, $kind, ST_GeomFromText($wkt, 4326), $period::int8range, 'seed', $src,
                              $src || ':' || $id, 'v1')""".update.run()
      imported(Region, "Zzpa Region", com.histmersion.domain.catalogue.Place.Kind.Region, "POINT(150 -60)", "wikidata")
      imported(
        Polity,
        "Zzpa Kingdom",
        com.histmersion.domain.catalogue.Place.Kind.Polity,
        "POINT(150.5 -60)",
        "wikidata",
        "[-900,-699)"
      )
      imported(Pinned, "Zzpa Pinned", com.histmersion.domain.catalogue.Place.Kind.Region, "POINT(151 -60)", "wikidata")
      imported(
        Shaped,
        "Zzpa Shaped",
        com.histmersion.domain.catalogue.Place.Kind.Region,
        "POLYGON((150 -61,151 -61,151 -60.5,150 -61))",
        "wikidata"
      )
      imported(
        GnArea,
        "Zzpa Gn Area",
        com.histmersion.domain.catalogue.Place.Kind.Region,
        "POINT(151.5 -60)",
        "geonames"
      )
      imported(GnSite, "Zzpa Gn Site", com.histmersion.domain.catalogue.Place.Kind.Site, "POINT(152 -60)", "geonames")
      imported(
        Thrace,
        "Zzpa Thrace",
        com.histmersion.domain.catalogue.Place.Kind.Region,
        "POINT(151.2 -60.2)",
        "wikidata"
      )
      imported(
        Eastern,
        "Eastern Zzpa Thracia",
        com.histmersion.domain.catalogue.Place.Kind.Region,
        "POINT(151.4 -60.2)",
        "geonames"
      )
      imported(
        Curated,
        "Zzpa Curated",
        com.histmersion.domain.catalogue.Place.Kind.Region,
        "POINT(152.5 -60)",
        "wikidata"
      )
      val _ = sql"""INSERT INTO place_overrides (place_id, kind, reason, source_kind, source_period)
                    VALUES ($Curated, 'region', 'a person decided', 'region', '(,)'::int8range)""".update.run()
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
    def f(n: Int, at: GeoPoint, name: String = "somewhere") =
      Finding(s"zzpa_src#$n", com.histmersion.api.library.Citekey("zzpa_src"), s"claim $n", Some(name), Some(at), None, None, Nil, None)
    SourceFixtures.upsert(
      ds,
      Source(
        citekey = com.histmersion.api.library.Citekey("zzpa_src"),
        title = com.histmersion.api.library.Title("Point area fixture"),
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
        findings = List(
          f(1, GeoPoint(150.0, -60.0)),
          f(2, GeoPoint(150.5, -60.0)),
          f(3, GeoPoint(150.95, -60.6), "Zzpa Thrace"),
          f(4, GeoPoint(150.9, -60.7), "Eastern Zzpa Thracia")
        )
      ),
      cfg.limits.chunkChars,
      cfg.places.pointToleranceKm
    )
    connect(ds):
      val _ = sql"""INSERT INTO attestations (citekey, target_type, target_id, quote)
                    VALUES ('zzpa_src', 'place', $Pinned, 'here')""".update.run()

  private def at(n: Int): Set[String] =
    PlaceFixtures.placesOf(ds, s"zzpa_src#$n").map(_.id).filter(id => Mine.contains(id) || id == Site).toSet
  private def exists(id: String): Boolean =
    connect(ds)(sql"SELECT 1 FROM places WHERE id = $id".query[Int].run().nonEmpty)
  private val kinds = Gazetteer.DefaultPointAreaKinds
  private val rule = Lint.rule(Lint.PlaceImportedPointArea).get
  private lazy val geometry = PlaceGeometry(com.histmersion.util.database.Transactor(ds))(using new com.histmersion.util.time.SystemTimer)
  private def settings(batch: Int, budgetMs: Long) =
    PointAreaRepo.Settings(kinds, batch, budgetMs, cfg.places.pointToleranceKm)

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
    val holders = connect(ds)(PointAreaRepo.holdersIn(kinds)).filter(l => Mine.contains(l.id)).map(l => l.id -> l).toMap
    assertEquals(holders.keySet, Set(Region, Polity, Pinned, Curated))
    assertEquals((holders(Region).findings, holders(Region).alone), (1, 0))
    assertEquals((holders(Polity).findings, holders(Polity).alone, holders(Polity).dating), (1, 1, Dating.Dated))
    assertEquals(holders(Pinned).pinned, 1)
    assertEquals(holders(Curated).pinned, 1, "a place override is a person's decision: listed, kept")
    val groups = connect(ds)(PointAreaRepo.groupsIn(kinds))
    assert(
      groups.exists(g => g.source == "geonames" && g.kind == "region" && g.dating == Dating.Undated && g.places >= 1),
      groups.toString
    )
    assert(
      groups.exists(g => g.source == "wikidata" && g.kind == "polity" && g.dating == Dating.Dated && g.places >= 1),
      groups.toString
    )
    val r = connect(ds)(PointAreaRepo.reportIn(rule, kinds, 50))
    assert(r.count >= 4, r.toString)
    assert(
      r.items.exists(i => i.citekey == Polity && i.detail.contains("1 finding, at no other place")),
      r.items.toString
    )
    assert(
      !r.items.exists(i => i.citekey == Shaped || i.citekey == GnSite || i.citekey == Site),
      "a shape, a site and the seed are no point areas"
    )
    assertEquals(connect(ds)(PointAreaRepo.reportIn(rule, Nil, 50)).count, 0, "no kinds, no rule")
    assert(Mine.forall(exists), "a listing writes nothing")

  test(
    "--fix: bounded per call, dependents gone, findings matched again, a shape, a site, the seed and evidence untouched"
  ):
    seed()
    // a zero budget still makes progress: one batch per call
    val one = geometry.removePointAreas(settings(batch = 1, budgetMs = 0))
    assertEquals(one.removed, 1)
    assert(one.remaining >= 3, one.toString)
    val rest = geometry.removePointAreas(settings(batch = 2, budgetMs = 60000))
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
    assert(one.findings + rest.findings >= 2 && one.sources + rest.sources >= 1, (one, rest).toString)
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
      geometry.removePointAreas(settings(batch = 10, budgetMs = 1000)),
      PointAreaRepo.Removal(0, 0, 0, 0),
      "nothing left to do"
    )

  test("the importer's prune passes over a place a person curated (place_overrides), as over evidence"):
    seed()
    val ids = connect(ds)(PointAreaRepo.ofSourceIn("wikidata", kinds)).toSet
    assert(ids.contains(Region) && ids.contains(Polity), ids.toString)
    assert(!ids.contains(Curated) && !ids.contains(Pinned), ids.toString)
