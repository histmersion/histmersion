package com.histmersion
package db

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.labels.LabelFixtures
import com.histmersion.domain.library.search.FilterSql
import com.histmersion.domain.library.search.SearchFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.places.FindingPlacement

import com.histmersion.api.GeoPoint
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.catalogue.Place.Id as PlaceId
import com.histmersion.api.library.Confidence
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{Period, Place}
import com.histmersion.api.library.{Filter, Finding, Source}
import com.histmersion.api.library.Facet
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.library.search.Search

/** The gazetteer against a real PostGIS (HISTMERSION_TEST_DB_URL); skipped otherwise. Covers what cannot be asserted
  * purely: containment, the point tolerance, period overlap, the recursive hierarchy and the `Filter.places` resolution
  * that feeds [[FilterSql]].
  */
class PlaceRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  // a square over Anatolia, a smaller Late-Bronze-Age polity inside it, and a point site
  private def box(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
    Place.Geometry(
      s"""{"type":"Polygon","coordinates":[[[$minLon,$minLat],[$maxLon,$minLat],[$maxLon,$maxLat],[$minLon,$maxLat],[$minLon,$minLat]]]}"""
    )

  private val asia = Place(Place.Id("zzp_asia"), Place.Label("Asia"), Place.KindValue(Place.Kind.Region))
  private val turkey =
    Place(Place.Id("zzp_turkey"), Place.Label("Turkey"), Place.KindValue(Place.Kind.Region), Some(box(26, 36, 45, 42)))
  private val hatti = Place(
    Place.Id("zzp_hatti"),
    Place.Label("Hittite Empire"),
    Place.KindValue(Place.Kind.Polity),
    Some(box(30, 38, 36, 41)),
    Some(Period(Year(-1650), Year(-1180))),
    Some(Place.Source("test fixture"))
  )
  private val hattusa =
    Place(
      Place.Id("zzp_hattusa"),
      Place.Label("Hattusa"),
      Place.KindValue(Place.Kind.Site),
      Some(Place.Geometry("""{"type":"Point","coordinates":[34.615,40.019]}"""))
    )

  private val batch = List(
    Place.Input(asia, List(Place.Name(Place.Name.Value("Asia")))),
    Place.Input(
      turkey,
      List(
        Place.Name(Place.Name.Value("Turkey")),
        Place.Name(Place.Name.Value("Zzpland")),
        Place.Name(Place.Name.Value("Türkiye"), Place.Name.Lang("tr"))
      ),
      List(Place.Relation(Place.Relation.ChildId("zzp_turkey"), Place.Relation.ParentId("zzp_asia")))
    ),
    Place.Input(
      hatti,
      List(
        Place.Name(Place.Name.Value("Hittite Empire")),
        Place.Name(Place.Name.Value("Hatti"), Place.Name.Lang("hit"))
      ),
      List(
        Place.Relation(
          Place.Relation.ChildId("zzp_hatti"),
          Place.Relation.ParentId("zzp_turkey"),
          Place.Relation.Kind(Place.Relation.PartOf),
          Some(Period(Year(-1650), Year(-1180)))
        )
      )
    ),
    Place.Input(
      hattusa,
      List(Place.Name(Place.Name.Value("Hattusa")), Place.Name(Place.Name.Value("Boğazkale"), Place.Name.Lang("tr"))),
      List(Place.Relation(Place.Relation.ChildId("zzp_hattusa"), Place.Relation.ParentId("zzp_hatti")))
    ),
    // a relation to a place nobody declared → reported, not fatal
    Place.Input(
      Place(Place.Id("zzp_orphan"), Place.Label("Orphan"), Place.KindValue(Place.Kind.Site)),
      Nil,
      List(Place.Relation(Place.Relation.ChildId("zzp_orphan"), Place.Relation.ParentId("zzp_nowhere")))
    )
  )

  private val src = Source(
    citekey = Citekey("zzp_src"),
    title = api.library.Title("Gazetteer fixture"),
    authors = (List("T")).map(api.library.AuthorName.apply),
    doi = None,
    year = (Some(2020)).map(Year.apply),
    venue = None,
    status = Source.Status.Current,
    topics = Nil,
    regions = List(Source.Regions("Anatolia")),
    tags = Nil,
    period = Some(Period(Year(-1400), Year(-1100))),
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    findings = List(
      // inside Hattusa/Hatti/Turkey, LBA
      Finding(
        Finding.Id("zzp_src#0"),
        Citekey("zzp_src"),
        Finding.Claim("Destruction layer"),
        Some(Finding.Region("Hattusa")),
        Some(GeoPoint(Longitude(34.615), Latitude(40.019))),
        Some(Period(Year(-1401), Year(-1351))),
        None,
        Nil,
        Some(Confidence.High)
      ),
      // inside Turkey but NOT inside Hatti's polygon, and after Hatti's period anyway
      Finding(
        Finding.Id("zzp_src#1"),
        Citekey("zzp_src"),
        Finding.Claim("Iron Age pottery"),
        Some(Finding.Region("Gordion")),
        Some(GeoPoint(Longitude(31.99), Latitude(39.65))),
        Some(Period(Year(-801), Year(-701))),
        None,
        Nil,
        None
      ),
      // no geometry: only the region_name fallback can place it
      Finding(
        Finding.Id("zzp_src#2"),
        Citekey("zzp_src"),
        Finding.Claim("Textual reference"),
        Some(Finding.Region("Zzpland")),
        None,
        Some(Period(Year(-1301), Year(-1201))),
        None,
        Nil,
        None
      ),
      // nowhere near any place
      Finding(
        Finding.Id("zzp_src#3"),
        Citekey("zzp_src"),
        Finding.Claim("Atlantic sediment"),
        None,
        Some(GeoPoint(Longitude(-30.0), Latitude(45.0))),
        Some(Period(Year(-1302), Year(-1202))),
        None,
        Nil,
        None
      )
    )
  )

  private def seed(): Unit =
    val _ = PlaceFixtures.upsertPlaces(ds, batch)
    SourceFixtures.upsert(ds, src, cfg.limits.chunkChars)
    val _ = PlaceFixtures.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)

  test("upsert is idempotent and reports a dangling parent instead of failing"):
    val r1 = PlaceFixtures.upsertPlaces(ds, batch)
    val r2 = PlaceFixtures.upsertPlaces(ds, batch)
    assertEquals(r1.places, 5)
    assertEquals(r1.relations, 3, "the edge to zzp_nowhere is dropped")
    assert(r1.failed.exists(_.value.contains("zzp_nowhere")), r1.failed.toString)
    assertEquals((r2.places, r2.names, r2.relations), (r1.places, r1.names, r1.relations))

  test("rebuild: containment + point tolerance + period overlap, and the region_name fallback"):
    seed()
    def ids(f: String) = PlaceFixtures.placesOf(ds, Finding.Id(f)).map(_.id).toSet
    assert(ids("zzp_src#0").contains(Place.Id("zzp_turkey")), "inside the polygon")
    assert(ids("zzp_src#0").contains(Place.Id("zzp_hatti")), "inside the polity, and the periods overlap")
    assert(ids("zzp_src#0").contains(Place.Id("zzp_hattusa")), "within the point tolerance of the site")
    assertEquals(
      ids("zzp_src#1"),
      Set("zzp_turkey"),
      "outside Hatti's polygon and 400 years after it; the modern country has no period so it holds"
    )
    assertEquals(ids("zzp_src#2"), Set("zzp_turkey"), "no geometry → region_name 'Zzpland' = a place name")
    assertEquals(ids("zzp_src#3"), Set.empty[String], "unlocated")

  test("rebuild counts add up"):
    seed()
    val r = PlaceFixtures.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)
    assertEquals(r.links.value, r.byGeometry.value + r.byName.value)
    assert(r.located.value >= 3 && r.unlocated.value >= 1, r.toString)
    assert(r.places.value >= 5)

  test("a finding on a border counts in EVERY containing place (counts are non-additive)"):
    seed()
    // the shared edge of two adjoining boxes: a point ON it is within both
    val west =
      Place(Place.Id("zzp_west"), Place.Label("West"), Place.KindValue(Place.Kind.Polity), Some(box(0, 0, 10, 10)))
    val east =
      Place(Place.Id("zzp_east"), Place.Label("East"), Place.KindValue(Place.Kind.Polity), Some(box(10, 0, 20, 10)))
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        Place.Input(west, List(Place.Name(Place.Name.Value("West")))),
        Place.Input(east, List(Place.Name(Place.Name.Value("East"))))
      )
    )
    val border = src
      .modify(_.citekey)
      .setTo(Citekey("zzp_border"))
      .modify(_.findings)
      .setTo(
        List(
          Finding(
            Finding.Id("zzp_border#0"),
            Citekey("zzp_border"),
            Finding.Claim("On the frontier"),
            None,
            Some(GeoPoint(Longitude(10.0), Latitude(5.0))),
            Some(Period(Year(-1303), Year(-1203))),
            None,
            Nil,
            None
          )
        )
      )
    SourceFixtures.upsert(ds, border, cfg.limits.chunkChars)
    val _ = PlaceFixtures.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)
    assertEquals(PlaceFixtures.placesOf(ds, Finding.Id("zzp_border#0")).map(_.id).toSet, Set("zzp_west", "zzp_east"))

  test("search: names in any language, exact hits first, asOf filters period-scoped rows"):
    seed()
    val hits = PlaceFixtures.search(ds, Some(PlaceFixtures.SearchText("Zzpland")), None, PlaceFixtures.Limit(10))
    assertEquals(hits.head.id, "zzp_turkey")
    assertEquals(hits.head.matchedName, Some("Zzpland"))
    assert(hits.head.names.value.contains("Türkiye"), hits.head.names)
    assert(
      PlaceFixtures
        .search(ds, Some(PlaceFixtures.SearchText("Hatti")), None, PlaceFixtures.Limit(10))
        .map(_.id.value)
        .contains("zzp_hatti")
    )
    // the Hittite Empire row is only valid inside its own period
    assert(
      PlaceFixtures
        .search(ds, Some(PlaceFixtures.SearchText("Hatti")), Some(DeepTime.SignedYear(-1300L)), PlaceFixtures.Limit(10))
        .map(_.id.value)
        .contains("zzp_hatti")
    )
    assert(
      !PlaceFixtures
        .search(ds, Some(PlaceFixtures.SearchText("Hatti")), Some(DeepTime.SignedYear(-700L)), PlaceFixtures.Limit(10))
        .map(_.id.value)
        .contains("zzp_hatti")
    )

  // #769: `ESCAPE '\'` inside a Magnum sql"…" reached Postgres as `ESCAPE ''`, so `_` never matched
  test("search: `_` and `%` in the query are literal characters"):
    seed()
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        Place.Input(
          Place(Place.Id("zzp_under"), Place.Label("Zzp_Under 50%"), Place.KindValue(Place.Kind.Site)),
          List(Place.Name(Place.Name.Value("Zzp_Under 50%")))
        )
      )
    )
    assertEquals(
      PlaceFixtures.search(ds, Some(PlaceFixtures.SearchText("Zzp_U")), None, PlaceFixtures.Limit(10)).map(_.id),
      Vector("zzp_under")
    )
    assertEquals(
      PlaceFixtures.search(ds, Some(PlaceFixtures.SearchText("50%")), None, PlaceFixtures.Limit(10)).map(_.id),
      Vector("zzp_under")
    )
    assertEquals(
      PlaceFixtures
        .search(ds, Some(PlaceFixtures.SearchText("Zzp_Under 5")), None, PlaceFixtures.Limit(10))
        .headOption
        .flatMap(_.matchedName),
      Some("Zzp_Under 50%")
    )
    assert(
      PlaceFixtures.search(ds, Some(PlaceFixtures.SearchText("Zz_l")), None, PlaceFixtures.Limit(10)).isEmpty,
      "`_` is not a wildcard (Zzpland)"
    )

  test("get: geometry as GeoJSON, parents/children as of a year, union for a shapeless place"):
    seed()
    val t = PlaceFixtures.get(ds, PlaceId("zzp_turkey"), Some(DeepTime.SignedYear(-1300L))).get
    assert(t.geometry.exists(_.value.contains("Polygon")), t.geometry.toString)
    assertEquals(t.parents.map(_.id), List("zzp_asia"))
    assertEquals(t.children.map(_.id), List("zzp_hatti"))
    assert(t.findings.value >= 3, t.findings.toString)
    // …but Hatti is not a child of Turkey in the Iron Age: the EDGE carries its own validity
    assertEquals(PlaceFixtures.get(ds, PlaceId("zzp_turkey"), Some(DeepTime.SignedYear(-700L))).get.children, Nil)
    // Asia has no shape of its own → the union of its children's
    assert(PlaceFixtures.get(ds, PlaceId("zzp_asia"), None).get.geometry.exists(_.value.contains("olygon")))

  test("hierarchy: a recursive walk of the edges valid at asOf"):
    seed()
    val lba = PlaceFixtures.hierarchy(
      ds,
      Some(DeepTime.SignedYear(-1300L)),
      Some(PlaceId("zzp_asia")),
      PlaceFixtures.Depth(6),
      PlaceFixtures.Limit(100)
    )
    // undated Hattusa's only finding (1401–1351 BCE) is not of 1300 BCE: the time rule leaves it out;
    // undated Turkey is in by its 1301–1201 BCE finding, shapeless Asia as a grouping node
    assertEquals(lba.map(r => (r.id, r.depth)), Vector(("zzp_asia", 0), ("zzp_turkey", 1), ("zzp_hatti", 2)))
    assertEquals(
      PlaceFixtures
        .hierarchy(
          ds,
          Some(DeepTime.SignedYear(-1380L)),
          Some(PlaceId("zzp_asia")),
          PlaceFixtures.Depth(6),
          PlaceFixtures.Limit(100)
        )
        .map(_.id)
        .last,
      "zzp_hattusa"
    )
    // (750 BCE: undated Turkey is in by its 801–701 BCE finding)
    val iron = PlaceFixtures.hierarchy(
      ds,
      Some(DeepTime.SignedYear(-750L)),
      Some(PlaceId("zzp_asia")),
      PlaceFixtures.Depth(6),
      PlaceFixtures.Limit(100)
    )
    assertEquals(iron.map(_.id), Vector("zzp_asia", "zzp_turkey"), "the Hatti edge is out of validity")
    assert(
      PlaceFixtures
        .hierarchy(ds, Some(DeepTime.SignedYear(-1300L)), None, PlaceFixtures.Depth(6), PlaceFixtures.Limit(100))
        .map(_.id)
        .contains("zzp_asia"),
      "no root → every place with children but no parent"
    )

  test("scopeFor: a place id expands to its descendants as of the filter period; a name does not"):
    seed()
    val lba = PlaceFixtures.scopeFor(ds, List(Filter.Places("zzp_asia")), Some(Period(Year(-1400), Year(-1200))))
    assertEquals(
      lba.idsFor(PlaceScope.Value("zzp_asia")).map(_.toSet),
      Some(Set("zzp_asia", "zzp_turkey", "zzp_hatti", "zzp_hattusa"))
    )
    val iron = PlaceFixtures.scopeFor(ds, List(Filter.Places("zzp_asia")), Some(Period(Year(-800), Year(-700))))
    assertEquals(iron.idsFor(PlaceScope.Value("zzp_asia")).map(_.toSet), Some(Set("zzp_asia", "zzp_turkey")))
    assert(PlaceFixtures.scopeFor(ds, List(Filter.Places("Zzpland")), None).isEmpty, "a plain name is not a place id")
    assert(PlaceFixtures.scopeFor(ds, Nil, None).isEmpty)

  test("Filter.places by id reaches the source through finding_places and the hierarchy"):
    seed()
    def found(place: String, from: Long, to: Long) =
      val f = Filter(
        places = List(Filter.Places(place)),
        period = Some(Period(Year(from), Year(to))),
        limit = Filter.Limit(50)
      )
      val r = SearchFixtures.sources(
        ds,
        f,
        Search.SnippetWords(cfg.limits.snippetWords.value),
        PlaceFixtures.scopeFor(ds, f.places, f.period)
      )
      r.hits.map(_.source.citekey).toSet
    assert(found("zzp_asia", -1400, -1200).contains(Citekey("zzp_src")), "Hattusa → Hatti → Turkey → Asia")
    assert(found("zzp_hatti", -1400, -1200).contains(Citekey("zzp_src")))
    assert(!found("zzp_hatti", -600, -500).contains(Citekey("zzp_src")), "no finding of it is in that window")
    // the matched finding explains WHICH place chip it satisfied — by the place's NAME, not by the
    // gazetteer id the filter carries (item 15: `place: pleiades_589831` explains nothing)
    val f = Filter(
      places = List(Filter.Places("zzp_hattusa")),
      period = Some(Period(Year(-1400), Year(-1200))),
      limit = Filter.Limit(50)
    )
    val hit = SearchFixtures
      .sources(
        ds,
        f,
        Search.SnippetWords(cfg.limits.snippetWords.value),
        PlaceFixtures.scopeFor(ds, f.places, f.period)
      )
      .hits
      .find(_.source.citekey.value == "zzp_src")
      .get
    assert(
      !hit.matched.exists(_.why.exists(w => s"$w".contains("zzp_hattusa"))),
      "the id is never shown to a reader: " + hit.matched.flatMap(_.why)
    )
    val byPlace = hit.matched.filter(_.why.map(w => s"$w").contains("place: Hattusa"))
    assertEquals(byPlace.map(_.id), List("zzp_src#0"), "only the finding actually AT Hattusa")
    assert(byPlace.head.places.contains("Hattusa"), byPlace.head.places)

  test("unlocated counts the findings the gazetteer could not place"):
    seed()
    assert(SearchFixtures.unlocated(ds, Filter(period = Some(Period(Year(-1302), Year(-1202))))).value >= 1)

  test("the places facet reports the gazetteer label AND the id behind it"):
    seed()
    val rows = LabelFixtures.facets(ds, Facet.Field.Places, Some(FilterSql.FacetPrefix("Tur")), Filter(), PageSize(20))
    val turkey = rows.find(_.value.value == "Turkey")
    assert(turkey.isDefined, rows.toString)
    assertEquals(turkey.get.id, Some("zzp_turkey"))
    assert(turkey.get.count.value >= 1)

  // ── a source write must not un-locate its own findings ───────────────────────────────────────

  test("patching a source keeps finding_places: the write re-materialises instead of cascading"):
    seed()
    val before = PlaceFixtures.placesOf(ds, Finding.Id("zzp_src#0")).map(_.id).toSet
    assert(before.nonEmpty, "fixture: the finding starts out located")
    // a patch that touches NOTHING spatial. This used to DELETE FROM findings, and
    // finding_places cascades off findings(id), so every assignment vanished.
    assert(
      SourceFixtures
        .patch(
          ds,
          domain.library.Citekey("zzp_src"),
          Source.Patch(notes = Some(Source.Notes("an agent wrote a note"))),
          cfg.limits.chunkChars,
          cfg.places.pointToleranceKm
        )
        .isRight
    )
    assertEquals(PlaceFixtures.placesOf(ds, Finding.Id("zzp_src#0")).map(_.id).toSet, before)
    assertEquals(PlaceFixtures.placesOf(ds, Finding.Id("zzp_src#2")).map(_.id).toSet, Set("zzp_turkey"))

  test("a put re-materialises: a finding that MOVED lands in its new places"):
    seed()
    val moved = src
      .modify(_.findings)
      .setTo(src.findings.map {
        case f if f.id.value == "zzp_src#0" =>
          f.modify(_.geo).setTo(Some(GeoPoint(Longitude(-30.0), Latitude(45.0)))) // out to the Atlantic
        case f => f
      })
    val _ = SourceFixtures.put(ds, moved, cfg.limits.chunkChars, cfg.places.pointToleranceKm)
    assertEquals(
      PlaceFixtures.placesOf(ds, Finding.Id("zzp_src#0")).map(_.id).toSet,
      Set.empty[String],
      "it is no longer inside anything"
    )
    // …and moving it back puts it back
    val _ = SourceFixtures.put(ds, src, cfg.limits.chunkChars, cfg.places.pointToleranceKm)
    assert(PlaceFixtures.placesOf(ds, Finding.Id("zzp_src#0")).map(_.id.value).toSet.contains("zzp_hattusa"))

  test("a finding dropped from the source is deleted; the others keep their places"):
    seed()
    val fewer = src.modify(_.findings).setTo(src.findings.filter(_.id.value != "zzp_src#1"))
    val _ = SourceFixtures.put(ds, fewer, cfg.limits.chunkChars, cfg.places.pointToleranceKm)
    assertEquals(
      SourceFixtures.load(ds, domain.library.Citekey("zzp_src")).get.findings.map(_.id),
      List("zzp_src#0", "zzp_src#2", "zzp_src#3")
    )
    assert(PlaceFixtures.placesOf(ds, Finding.Id("zzp_src#0")).nonEmpty)

  test("the fallback tolerance matches the configured one, so they cannot drift"):
    assertEquals(FindingPlacement.DefaultToleranceKm.value, cfg.places.pointToleranceKm.value)

  // ── the hierarchy walk's bounds ──────────────────────────────────────────────────────────────

  test("a part_of CYCLE does not repeat a place down the walk"):
    seed()
    // close the loop: Asia is part of Hattusa, which is (transitively) part of Asia
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        Place.Input(
          asia,
          List(Place.Name(Place.Name.Value("Asia"))),
          List(Place.Relation(Place.Relation.ChildId("zzp_asia"), Place.Relation.ParentId("zzp_hattusa")))
        )
      )
    )
    val rows =
      PlaceFixtures.hierarchy(ds, None, Some(PlaceId("zzp_turkey")), PlaceFixtures.Depth(20), PlaceFixtures.Limit(500))
    val ids = rows.map(_.id)
    assertEquals(ids.size, ids.distinct.size, s"a place appears twice on one walk: $ids")
    assert(ids.contains("zzp_hatti"), ids.toString)
    // put the gazetteer back the way the other tests expect it
    val _ = PlaceFixtures.upsertPlaces(ds, List(Place.Input(asia, List(Place.Name(Place.Name.Value("Asia"))))))

  test("depthCap bounds a caller asking for an absurd depth"):
    seed()
    val deep = PlaceFixtures.hierarchy(
      ds,
      None,
      Some(PlaceId("zzp_asia")),
      PlaceFixtures.Depth(10000),
      PlaceFixtures.Limit(500),
      depthCap = PlaceFixtures.Depth(1)
    )
    assert(deep.forall(_.depth.value <= 1), deep.map(r => r.id -> r.depth).toString)
    val deeper = PlaceFixtures.hierarchy(
      ds,
      None,
      Some(PlaceId("zzp_asia")),
      PlaceFixtures.Depth(10000),
      PlaceFixtures.Limit(500),
      depthCap = PlaceFixtures.Depth(6)
    )
    assert(deeper.size >= deep.size)
    // …and the caller's own smaller number still wins
    assert(
      PlaceFixtures
        .hierarchy(
          ds,
          None,
          Some(PlaceId("zzp_asia")),
          PlaceFixtures.Depth(0),
          PlaceFixtures.Limit(500),
          depthCap = PlaceFixtures.Depth(6)
        )
        .forall(_.depth.value == 0)
    )
