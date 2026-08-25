package histdb.db

import histdb.core.*

/** The gazetteer against a real PostGIS (HISTDB_TEST_DB_URL); skipped otherwise. Covers what cannot
  * be asserted purely: containment, the point tolerance, period overlap, the recursive hierarchy and
  * the `Filter.places` resolution that feeds [[FilterSql]]. */
class PlaceRepoSuite extends munit.FunSuite:
  private val testDb = sys.env.get("HISTDB_TEST_DB_URL")
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = Db.dataSource(cfg.db.copy(url = testDb.get))
    Migrate.run(d); d

  // a square over Anatolia, a smaller Late-Bronze-Age polity inside it, and a point site
  private def box(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
    s"""{"type":"Polygon","coordinates":[[[$minLon,$minLat],[$maxLon,$minLat],[$maxLon,$maxLat],[$minLon,$maxLat],[$minLon,$minLat]]]}"""

  private val asia    = Place("zzp_asia", "Asia", PlaceKind.Region)
  private val turkey  = Place("zzp_turkey", "Turkey", PlaceKind.Region, Some(box(26, 36, 45, 42)))
  private val hatti   = Place("zzp_hatti", "Hittite Empire", PlaceKind.Polity, Some(box(30, 38, 36, 41)),
    Some(Period(-1650, -1180)), Some("test fixture"))
  private val hattusa = Place("zzp_hattusa", "Hattusa", PlaceKind.Site,
    Some("""{"type":"Point","coordinates":[34.615,40.019]}"""))

  private val batch = List(
    PlaceInput(asia, List(PlaceName("Asia"))),
    PlaceInput(turkey, List(PlaceName("Turkey"), PlaceName("Zzpland"), PlaceName("Türkiye", "tr")),
      List(PlaceRelation("zzp_turkey", "zzp_asia"))),
    PlaceInput(hatti, List(PlaceName("Hittite Empire"), PlaceName("Hatti", "hit")),
      List(PlaceRelation("zzp_hatti", "zzp_turkey", PlaceRelation.PartOf, Some(Period(-1650, -1180))))),
    PlaceInput(hattusa, List(PlaceName("Hattusa"), PlaceName("Boğazkale", "tr")),
      List(PlaceRelation("zzp_hattusa", "zzp_hatti"))),
    // a relation to a place nobody declared → reported, not fatal
    PlaceInput(Place("zzp_orphan", "Orphan", PlaceKind.Site), Nil,
      List(PlaceRelation("zzp_orphan", "zzp_nowhere"))))

  private val src = Source(
    citekey = "zzp_src", title = "Gazetteer fixture", authors = List("T"), doi = None, year = Some(2020),
    venue = None, status = SourceStatus.Current, topics = Nil, regions = List("Anatolia"), tags = Nil,
    period = Some(Period(-1400, -1100)), summary = None, citedByCount = None, influentialCitations = None,
    provenance = None, calibreId = None,
    findings = List(
      // inside Hattusa/Hatti/Turkey, LBA
      Finding("zzp_src#0", "zzp_src", "Destruction layer", Some("Hattusa"),
        Some(GeoPoint(34.615, 40.019)), Some(Period(-1401, -1351)), None, Nil, Some("high")),
      // inside Turkey but NOT inside Hatti's polygon, and after Hatti's period anyway
      Finding("zzp_src#1", "zzp_src", "Iron Age pottery", Some("Gordion"),
        Some(GeoPoint(31.99, 39.65)), Some(Period(-801, -701)), None, Nil, None),
      // no geometry: only the region_name fallback can place it
      Finding("zzp_src#2", "zzp_src", "Textual reference", Some("Zzpland"),
        None, Some(Period(-1301, -1201)), None, Nil, None),
      // nowhere near any place
      Finding("zzp_src#3", "zzp_src", "Atlantic sediment", None,
        Some(GeoPoint(-30.0, 45.0)), Some(Period(-1302, -1202)), None, Nil, None)))

  private def seed(): Unit =
    val _ = PlaceRepo.upsertPlaces(ds, batch)
    SourceRepo.upsert(ds, src, cfg.limits.chunkChars)
    val _ = PlaceRepo.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)

  test("upsert is idempotent and reports a dangling parent instead of failing"):
    val r1 = PlaceRepo.upsertPlaces(ds, batch)
    val r2 = PlaceRepo.upsertPlaces(ds, batch)
    assertEquals(r1.places, 5)
    assertEquals(r1.relations, 3, "the edge to zzp_nowhere is dropped")
    assert(r1.failed.exists(_.contains("zzp_nowhere")), r1.failed.toString)
    assertEquals((r2.places, r2.names, r2.relations), (r1.places, r1.names, r1.relations))

  test("rebuild: containment + point tolerance + period overlap, and the region_name fallback"):
    seed()
    def ids(f: String) = PlaceRepo.placesOf(ds, f).map(_.id).toSet
    assert(ids("zzp_src#0").contains("zzp_turkey"), "inside the polygon")
    assert(ids("zzp_src#0").contains("zzp_hatti"), "inside the polity, and the periods overlap")
    assert(ids("zzp_src#0").contains("zzp_hattusa"), "within the point tolerance of the site")
    assertEquals(ids("zzp_src#1"), Set("zzp_turkey"),
      "outside Hatti's polygon and 400 years after it; the modern country has no period so it holds")
    assertEquals(ids("zzp_src#2"), Set("zzp_turkey"), "no geometry → region_name 'Zzpland' = a place name")
    assertEquals(ids("zzp_src#3"), Set.empty[String], "unlocated")

  test("rebuild counts add up"):
    seed()
    val r = PlaceRepo.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)
    assertEquals(r.links, r.byGeometry + r.byName)
    assert(r.located >= 3 && r.unlocated >= 1, r.toString)
    assert(r.places >= 5)

  test("a finding on a border counts in EVERY containing place (counts are non-additive)"):
    seed()
    // the shared edge of two adjoining boxes: a point ON it is within both
    val west = Place("zzp_west", "West", PlaceKind.Polity, Some(box(0, 0, 10, 10)))
    val east = Place("zzp_east", "East", PlaceKind.Polity, Some(box(10, 0, 20, 10)))
    val _ = PlaceRepo.upsertPlaces(ds, List(PlaceInput(west, List(PlaceName("West"))),
      PlaceInput(east, List(PlaceName("East")))))
    val border = src.copy(citekey = "zzp_border", findings = List(
      Finding("zzp_border#0", "zzp_border", "On the frontier", None, Some(GeoPoint(10.0, 5.0)),
        Some(Period(-1303, -1203)), None, Nil, None)))
    SourceRepo.upsert(ds, border, cfg.limits.chunkChars)
    val _ = PlaceRepo.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)
    assertEquals(PlaceRepo.placesOf(ds, "zzp_border#0").map(_.id).toSet, Set("zzp_west", "zzp_east"))

  test("search: names in any language, exact hits first, asOf filters period-scoped rows"):
    seed()
    val hits = PlaceRepo.search(ds, Some("Zzpland"), None, 10)
    assertEquals(hits.head.id, "zzp_turkey")
    assertEquals(hits.head.matchedName, Some("Zzpland"))
    assert(hits.head.names.contains("Türkiye"), hits.head.names)
    assert(PlaceRepo.search(ds, Some("Hatti"), None, 10).map(_.id).contains("zzp_hatti"))
    // the Hittite Empire row is only valid inside its own period
    assert(PlaceRepo.search(ds, Some("Hatti"), Some(-1300), 10).map(_.id).contains("zzp_hatti"))
    assert(!PlaceRepo.search(ds, Some("Hatti"), Some(-700), 10).map(_.id).contains("zzp_hatti"))

  test("get: geometry as GeoJSON, parents/children as of a year, union for a shapeless place"):
    seed()
    val t = PlaceRepo.get(ds, "zzp_turkey", Some(-1300)).get
    assert(t.geometry.exists(_.contains("Polygon")), t.geometry.toString)
    assertEquals(t.parents.map(_.id), List("zzp_asia"))
    assertEquals(t.children.map(_.id), List("zzp_hatti"))
    assert(t.findings >= 3, t.findings.toString)
    // …but Hatti is not a child of Turkey in the Iron Age: the EDGE carries its own validity
    assertEquals(PlaceRepo.get(ds, "zzp_turkey", Some(-700)).get.children, Nil)
    // Asia has no shape of its own → the union of its children's
    assert(PlaceRepo.get(ds, "zzp_asia", None).get.geometry.exists(_.contains("olygon")))

  test("hierarchy: a recursive walk of the edges valid at asOf"):
    seed()
    val lba = PlaceRepo.hierarchy(ds, Some(-1300), Some("zzp_asia"), 6, 100)
    assertEquals(lba.map(r => (r.id, r.depth)),
      Vector(("zzp_asia", 0), ("zzp_turkey", 1), ("zzp_hatti", 2), ("zzp_hattusa", 3)))
    val iron = PlaceRepo.hierarchy(ds, Some(-700), Some("zzp_asia"), 6, 100)
    assertEquals(iron.map(_.id), Vector("zzp_asia", "zzp_turkey"), "the Hatti edge is out of validity")
    assert(PlaceRepo.hierarchy(ds, Some(-1300), None, 6, 100).map(_.id).contains("zzp_asia"),
      "no root → every place with children but no parent")

  test("scopeFor: a place id expands to its descendants as of the filter period; a name does not"):
    seed()
    val lba = PlaceRepo.scopeFor(ds, List("zzp_asia"), Some(Period(-1400, -1200)))
    assertEquals(lba.idsFor("zzp_asia").map(_.toSet),
      Some(Set("zzp_asia", "zzp_turkey", "zzp_hatti", "zzp_hattusa")))
    val iron = PlaceRepo.scopeFor(ds, List("zzp_asia"), Some(Period(-800, -700)))
    assertEquals(iron.idsFor("zzp_asia").map(_.toSet), Some(Set("zzp_asia", "zzp_turkey")))
    assert(PlaceRepo.scopeFor(ds, List("Zzpland"), None).isEmpty, "a plain name is not a place id")
    assert(PlaceRepo.scopeFor(ds, Nil, None).isEmpty)

  test("Filter.places by id reaches the source through finding_places and the hierarchy"):
    seed()
    def found(place: String, from: Long, to: Long) =
      val f = Filter(places = List(place), period = Some(Period(from, to)), limit = 50)
      val r = SourceRepo.sources(ds, f, cfg.limits.snippetWords, PlaceRepo.scopeFor(ds, f.places, f.period))
      r.hits.map(_.source.citekey).toSet
    assert(found("zzp_asia", -1400, -1200).contains("zzp_src"), "Hattusa → Hatti → Turkey → Asia")
    assert(found("zzp_hatti", -1400, -1200).contains("zzp_src"))
    assert(!found("zzp_hatti", -600, -500).contains("zzp_src"), "no finding of it is in that window")
    // the matched finding explains WHICH place chip it satisfied — by the place's NAME, not by the
    // gazetteer id the filter carries (item 15: `place: pleiades_589831` explains nothing)
    val f = Filter(places = List("zzp_hattusa"), period = Some(Period(-1400, -1200)), limit = 50)
    val hit = SourceRepo.sources(ds, f, cfg.limits.snippetWords, PlaceRepo.scopeFor(ds, f.places, f.period))
      .hits.find(_.source.citekey == "zzp_src").get
    assert(!hit.matched.exists(_.why.exists(_.contains("zzp_hattusa"))),
      "the id is never shown to a reader: " + hit.matched.flatMap(_.why))
    val byPlace = hit.matched.filter(_.why.contains("place: Hattusa"))
    assertEquals(byPlace.map(_.id), List("zzp_src#0"), "only the finding actually AT Hattusa")
    assert(byPlace.head.places.contains("Hattusa"), byPlace.head.places)

  test("unlocated counts the findings the gazetteer could not place"):
    seed()
    assert(PlaceRepo.unlocated(ds, Filter(period = Some(Period(-1302, -1202)))) >= 1)

  test("the places facet reports the gazetteer label AND the id behind it"):
    seed()
    val rows = SourceRepo.facets(ds, FacetField.Places, Some("Tur"), Filter(), 20)
    val turkey = rows.find(_.value == "Turkey")
    assert(turkey.isDefined, rows.toString)
    assertEquals(turkey.get.id, Some("zzp_turkey"))
    assert(turkey.get.count >= 1)
