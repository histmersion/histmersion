package com.histmersion.db

import com.histmersion.domain.library.sources.SourceRepo

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.values.{PlaceKind, SourceStatus}

/** A person's kind and period for a place (V080), against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise):
  * setting one re-matches the place's findings; every writer of `places` — a re-seed, a gazetteer-style upsert, the
  * place-kind lint — keeps it, and the source's value is remembered; it follows a rename; a reset restores the source's
  * kind and period; the place-kind lint no longer lists a place a person decided. A merge moves a place into the one it
  * is the same as, a removal deletes one nothing refers to; no seed import brings either back. Around 150–152°E
  * 30–32°S; ids `zzpo…`.
  */
class PlaceOverrideSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d
  private def tol = cfg.places.pointToleranceKm

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zzpo%'".update.run()
    val _ = sql"DELETE FROM place_aliases WHERE alias LIKE 'zzpo%'".update.run()
    val _ = sql"DELETE FROM place_links WHERE place_id LIKE 'zzpo%' OR place_id LIKE 'pleiades\\_zzpo%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zzpo%' OR id LIKE 'pleiades\\_zzpo%'".update.run()
    val _ = sql"DELETE FROM place_removals WHERE id LIKE 'zzpo%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def pt(lon: Double, lat: Double) = Some(s"""{"type":"Point","coordinates":[$lon,$lat]}""")

  private val Regio = "zzpo_regio"
  private val City = "zzpo_city"
  private val Tribe = "zzpo_tribe"

  private def regio(kind: String = PlaceKind.Site, period: Option[Period] = None) =
    Place(
      Regio,
      "Zzpo Regio XII (Piscina Publica)",
      kind,
      pt(150.5, -30.6),
      period,
      properties = Some("""{"feature_types":"urban"}""")
    )

  private def seed(): Unit =
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        PlaceInput(regio(), List(PlaceName("Zzpo Regio XII (Piscina Publica)"))),
        PlaceInput(Place(City, "Zzpo Roma", PlaceKind.Site, pt(150.51, -30.61)), List(PlaceName("Zzpo Roma"))),
        PlaceInput(
          Place(
            Tribe,
            "Zzpo Aiantis",
            PlaceKind.Region,
            pt(151.5, -31.6),
            properties = Some("""{"feature_types":"people"}""")
          ),
          List(PlaceName("Zzpo Aiantis"))
        )
      ),
      Dataset.Seed
    )
    SourceRepo.upsert(
      ds,
      Source(
        citekey = "zzpo_src",
        title = "Override fixture",
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
        findings = List(
          Finding(
            "zzpo_src#0",
            "zzpo_src",
            "the city",
            Some("Zzpo Roma"),
            Some(GeoPoint(150.51, -30.61)),
            None,
            None,
            Nil,
            None
          ),
          Finding(
            "zzpo_src#1",
            "zzpo_src",
            "near the city",
            None,
            Some(GeoPoint(150.53, -30.62)),
            None,
            None,
            Nil,
            None
          )
        )
      ),
      cfg.limits.chunkChars,
      tol
    )

  private def placesOf(n: Int): Set[String] =
    PlaceFixtures.placesOf(ds, s"zzpo_src#$n").map(_.id).filter(_.startsWith("zzpo")).toSet

  private def row(id: String): (String, String, Boolean) =
    connect(ds)(
      sql"SELECT kind, period::text, start_unknown FROM places WHERE id = $id"
        .query[(String, String, Boolean)]
        .run()
        .head
    )

  private def put(
      kind: Option[String] = None,
      from: Option[Long] = None,
      to: Option[Long] = None,
      reason: Option[String] = None
  ) =
    PlaceFixtures.setOverride(
      ds,
      Regio,
      PlaceOverrideEdit.Put(kind, Option.when(from.isDefined || to.isDefined)((from, to)), reason),
      tol
    )

  test("setting a kind re-matches the place's findings at once, and lists the override with the source's kind"):
    seed()
    assertEquals(placesOf(0), Set(Regio, City), "as a site, Regio XII takes the city's findings by distance")
    val r = put(kind = Some(PlaceKind.Region), reason = Some("one of the 14 regiones")).toOption.get
    assertEquals(
      (r.kindBefore, r.kindAfter, r.findingsBefore, r.findingsAfter, r.unlocated),
      ("site", "region", 2, 0, 0)
    )
    assertEquals(placesOf(0), Set(City), "a point-only region takes nothing by distance")
    assertEquals(placesOf(1), Set(City))
    val o = PlaceFixtures.overrides(ds, Some(Regio))
    assertEquals(
      o.map(x => (x.kind, x.periodSet, x.sourceKind, x.reason)),
      List((Some("region"), false, "site", Some("one of the 14 regiones")))
    )
    assertEquals(PlaceFixtures.overrides(ds, None).filter(_.placeId.startsWith("zzpo")).size, 1)

  test("a re-seed, an upsert with ON CONFLICT and a raw update keep the override; the source's value is remembered"):
    seed()
    val _ = put(kind = Some(PlaceKind.Region), from = Some(-7))
    assertEquals(row(Regio), ("region", "[-7,)", false))
    // the seed again, now with a period of its own: the override wins, the source's period is remembered
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(PlaceInput(regio(period = Some(Period(1700, 2100))), List(PlaceName("Zzpo Regio XII (Piscina Publica)")))),
      Dataset.Seed
    )
    assertEquals(row(Regio), ("region", "[-7,)", false))
    assertEquals(
      PlaceFixtures.overrides(ds, Some(Regio)).map(o => (o.sourceKind, o.sourcePeriodStart, o.sourcePeriodEnd)),
      List(("site", Some(1700L), Some(2100L)))
    )
    // a gazetteer-style write (start unknown) and a lint-style UPDATE alike
    connect(ds):
      val _ = sql"UPDATE places SET kind = 'feature', start_unknown = true WHERE id = $Regio".update.run()
    assertEquals(row(Regio), ("region", "[-7,)", false))
    assertEquals(PlaceFixtures.overrides(ds, Some(Regio)).map(_.sourceKind), List("feature"))
    // a later set changes only what it names
    val _ = put(to = Some(410))
    assertEquals(row(Regio), ("region", "[-7,411)", false))
    assertEquals(PlaceFixtures.overrides(ds, Some(Regio)).map(_.kind), List(Some("region")))

  test("the override follows a rename; a reset restores the source's kind and period and the findings"):
    seed()
    val _ = put(kind = Some(PlaceKind.Region), from = Some(-7), to = Some(410))
    assertEquals(PlaceFixtures.rename(ds, Regio, "zzpo_regio_xii"), Right(()))
    assertEquals(PlaceFixtures.overrides(ds, Some("zzpo_regio_xii")).map(_.kind), List(Some("region")))
    assertEquals(PlaceFixtures.rename(ds, "zzpo_regio_xii", Regio), Right(()))
    val r = PlaceFixtures.setOverride(ds, Regio, PlaceOverrideEdit.Reset, tol).toOption.get
    assertEquals(
      (r.reset, r.kindAfter, r.periodBefore, r.periodAfter, r.findingsAfter),
      (true, "site", "7 BCE–410 CE", "undated", 2)
    )
    assertEquals(row(Regio), ("site", "(,)", false))
    assertEquals(placesOf(0), Set(Regio, City))
    assertEquals(
      PlaceFixtures.setOverride(ds, Regio, PlaceOverrideEdit.Reset, tol).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(
      PlaceFixtures.setOverride(ds, "zzpo_nope", PlaceOverrideEdit.Reset, tol).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    // after the reset, the next seed writes the source's kind freely
    connect(ds):
      val _ = sql"UPDATE places SET kind = 'feature' WHERE id = $Regio".update.run()
    assertEquals(row(Regio)._1, "feature")

  test("the place-kind lint no longer lists a place a person decided"):
    seed()
    def listed = PlaceFixtures.proposals(ds).map(_.id).filter(_.startsWith("zzpo")).toSet
    assertEquals(listed, Set(Tribe), "a people filed as a region")
    val _ = PlaceFixtures.setOverride(
      ds,
      Tribe,
      PlaceOverrideEdit.Put(None, Some((Some(-508L), Some(-322L))), Some("kept a region")),
      tol
    )
    assertEquals(listed, Set.empty[String])
    assertEquals(row(Tribe), ("region", "[-508,-321)", false))

  test("a merge moves the findings and the id; a re-seed of the merged id does not bring it back"):
    seed()
    val dup = Place("zzpo_dup", "Withdrawn: Zzpo Roma", PlaceKind.Site, pt(150.52, -30.62))
    val _ = PlaceFixtures.upsertPlaces(ds, List(PlaceInput(dup, List(PlaceName(dup.label)))), Dataset.Seed)
    connect(ds):
      val _ = sql"""INSERT INTO place_links (place_id, scheme, external_id, relation)
                    VALUES ('zzpo_dup', 'pleiades', 'zzpo1', 'exact_match')""".update.run()
    val _ = transact(ds)(PlaceFixtures.materialisePlacesIn(List("zzpo_dup"), tol))
    assertEquals(placesOf(1), Set(Regio, City, "zzpo_dup"))
    val r = PlaceFixtures.merge(ds, "zzpo_dup", City, tol).toOption.get
    assertEquals((r.into, r.findingsBefore, r.findingsAfter, r.unlocated), (Some(City), 2, 2, 0))
    assertEquals(PlaceFixtures.renamedTo(ds, "zzpo_dup"), Some(City))
    assertEquals(PlaceFixtures.links(ds, City).map(_.externalId).toList, List("zzpo1"))
    assertEquals(placesOf(1), Set(Regio, City))
    // the seed sends the duplicate again: it is left out, the rest of the batch goes in
    val res = PlaceFixtures.upsertPlaces(
      ds,
      List(PlaceInput(dup, List(PlaceName(dup.label))), PlaceInput(regio(), Nil)),
      Dataset.Seed
    )
    assertEquals(res.places, 1)
    assertEquals(PlaceFixtures.get(ds, "zzpo_dup"), None)
    assertEquals(PlaceFixtures.merge(ds, City, City, tol).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(PlaceFixtures.merge(ds, "zzpo_nope", City, tol).left.map(_.code), Left(Some(Write.CodeNotFound)))

  test("a removal is refused while something refers to the place; done, no re-seed brings it back"):
    seed()
    // the city is the only place of both findings once the Regio is a region
    val _ = put(kind = Some(PlaceKind.Region))
    val refused = PlaceFixtures.remove(ds, City, None)
    assert(refused.left.exists(_.message.contains("2 finding(s) it is the only place of")), refused.toString)
    val road = Place("zzpo_road", "Withdrawn: Untitled", PlaceKind.Site, pt(151.9, -31.9))
    val _ = PlaceFixtures.upsertPlaces(ds, List(PlaceInput(road, List(PlaceName(road.label)))), Dataset.Seed)
    val _ = PlaceFixtures.addName(ds, "zzpo_road", "Zzpo Via", "")
    assert(PlaceFixtures.remove(ds, "zzpo_road", None).left.exists(_.message.contains("1 name(s) added by hand")))
    val _ = PlaceFixtures.removeName(ds, "zzpo_road", "Zzpo Via", "")
    val r = PlaceFixtures.remove(ds, "zzpo_road", Some("withdrawn by Pleiades, no replacement")).toOption.get
    assertEquals((r.placeId, r.into, r.findingsBefore, r.unlocated), ("zzpo_road", None, 0, 0))
    val _ = PlaceFixtures.upsertPlaces(ds, List(PlaceInput(road, List(PlaceName(road.label)))), Dataset.Seed)
    assertEquals(PlaceFixtures.get(ds, "zzpo_road"), None, "the seed leaves a removed place out")
    assert(
      PlaceFixtures.addPlace(ds, PlaceInput(road.copy(label = "Zzpo Road"), Nil)).isLeft,
      "nor may a person reuse its id"
    )
    connect(ds)(sql"DELETE FROM place_removals WHERE id = 'zzpo_road'".update.run())
    val _ = PlaceFixtures.upsertPlaces(ds, List(PlaceInput(road, List(PlaceName(road.label)))), Dataset.Seed)
    assert(PlaceFixtures.get(ds, "zzpo_road").isDefined, "deleting the removal lets the seed bring it back")

  // ── a point set by hand (V081) ────────────────────────────────────────────────────────────────

  private def geom(id: String): String =
    connect(ds)(sql"SELECT ST_AsText(geom) FROM places WHERE id = $id".query[String].run().head)

  private def tribe(geometry: Option[String]) =
    PlaceInput(
      Place(Tribe, "Zzpo Aiantis", PlaceKind.Region, geometry, properties = Some("""{"feature_types":"people"}""")),
      List(PlaceName("Zzpo Aiantis"))
    )

  private def movePoint(id: String, lon: Double, lat: Double, reason: String = "Pleiades representative point") =
    PlaceFixtures.setOverride(ds, id, PlaceOverrideEdit.Put(None, None, Some(reason), Some(GeoPoint(lon, lat))), tol)

  test("a point set by hand moves a place known by a point; every writer keeps it; a reset restores the source's"):
    seed()
    val r = movePoint(Tribe, 150.9, -30.9).toOption.get
    assertEquals(
      (r.latBefore, r.lonBefore, r.latAfter, r.lonAfter),
      (Some(-31.6), Some(151.5), Some(-30.9), Some(150.9))
    )
    assertEquals(geom(Tribe), "POINT(150.9 -30.9)")
    assertEquals(
      PlaceFixtures.overrides(ds, Some(Tribe)).map(o => (o.lat, o.lon, o.sourceLat, o.sourceLon)),
      List((Some(-30.9), Some(150.9), Some(-31.6), Some(151.5)))
    )
    // the seed again, with its own (changed) point: the person's point stays, the source's is remembered
    val _ = PlaceFixtures.upsertPlaces(ds, List(tribe(pt(151.4, -31.5))), Dataset.Seed)
    assertEquals(geom(Tribe), "POINT(150.9 -30.9)")
    assertEquals(
      PlaceFixtures.overrides(ds, Some(Tribe)).map(o => (o.sourceLat, o.sourceLon)),
      List((Some(-31.5), Some(151.4)))
    )
    // a lint-style UPDATE alike; a later kind changes only the kind
    connect(ds):
      val _ = sql"UPDATE places SET geom = ST_SetSRID(ST_MakePoint(1, 1), 4326) WHERE id = $Tribe".update.run()
    assertEquals(geom(Tribe), "POINT(150.9 -30.9)")
    val k =
      PlaceFixtures.setOverride(ds, Tribe, PlaceOverrideEdit.Put(Some(PlaceKind.People), None, None), tol).toOption.get
    assertEquals((k.kindAfter, k.latAfter), ("people", Some(-30.9)))
    // reset: the source's kind and its latest point come back
    val back = PlaceFixtures.setOverride(ds, Tribe, PlaceOverrideEdit.Reset, tol).toOption.get
    assertEquals((back.kindAfter, back.latAfter, back.lonAfter), ("region", Some(1.0), Some(1.0)))
    assertEquals(PlaceFixtures.overrides(ds, Some(Tribe)), Nil)

  test("a point never replaces a shape: refused on an area, and a shape a source gives later is kept"):
    seed()
    val square = Some("""{"type":"Polygon","coordinates":[[[151,-32],[152,-32],[152,-31],[151,-31],[151,-32]]]}""")
    val _ = PlaceFixtures.upsertPlaces(ds, List(tribe(square)), Dataset.Seed)
    assertEquals(movePoint(Tribe, 150.9, -30.9).left.map(_.code), Left(Some(Write.CodeInvalid)))
    // back to a point, moved by hand; then the source gives the shape again — the shape wins
    val _ = PlaceFixtures.upsertPlaces(ds, List(tribe(pt(151.5, -31.6))), Dataset.Seed)
    val _ = movePoint(Tribe, 150.9, -30.9)
    val _ = PlaceFixtures.upsertPlaces(ds, List(tribe(square)), Dataset.Seed)
    assert(geom(Tribe).startsWith("POLYGON"), geom(Tribe))
    val _ = PlaceFixtures.setOverride(ds, Tribe, PlaceOverrideEdit.Put(Some(PlaceKind.People), None, None), tol)
    assert(geom(Tribe).startsWith("POLYGON"), "a later set does not put the point over the shape")
    assertEquals(PlaceFixtures.setOverride(ds, Tribe, PlaceOverrideEdit.Reset, tol).map(_.latAfter), Right(None))
    assert(geom(Tribe).startsWith("POLYGON"))

  test("the place-grid-point lint leaves a place alone once a person set its point"):
    seed()
    // a Pleiades region the lint had moved to its Wikidata item's point (a capital, not the region)
    val id = "pleiades_zzpo_parthia"
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        PlaceInput(Place(id, "Zzpo Parthyene", PlaceKind.Region, pt(152.5, -32.5)), List(PlaceName("Zzpo Parthyene")))
      ),
      Dataset.Seed
    )
    connect(ds):
      val _ = sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, item_geom)
                    VALUES ($id, 'wikidata', 'Qzzpo', 'exact_match', ST_SetSRID(ST_MakePoint(151.1, -31.3), 4326))""".update
        .run()
    def listed = PlaceFixtures.relocations(ds).map(_.id).filter(_ == id)
    assertEquals(listed, List(id), "a grid square's centre with a linked point elsewhere")
    assertEquals(
      PlaceFixtures.relocate(ds, PlaceFixtures.relocations(ds).filter(_.id == id), tol),
      1
    )
    assertEquals(geom(id), "POINT(151.1 -31.3)")
    // the person puts back Pleiades' own point — the grid square's centre — and the lint stops offering the move
    val _ = movePoint(id, 152.5, -32.5, "Pleiades reprPoint; the Wikidata coordinate is the capital")
    assertEquals(geom(id), "POINT(152.5 -32.5)")
    assertEquals(listed, Nil)
