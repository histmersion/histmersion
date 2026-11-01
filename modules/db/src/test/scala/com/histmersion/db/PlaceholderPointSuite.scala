package com.histmersion.db

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.values.{PlaceKind, SourceStatus}

/** A finding whose POINT is a placeholder (2026-10-09), against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped
  * otherwise). 12 findings on "Greece" sat on 37.5 N 22.5 E, an atlas grid square's centre near Tegea, and had joined
  * Tegea, Hysiai and the Temple of Athena Alea by the 10 km tolerance and the Peloponnese by containment. Here, at
  * 150–156°E 40–46°S ("Zzph"):
  *   - a placeholder takes no site by distance; when its name resolves, it belongs to that place and to the areas
  *     covering at least a quarter of it (Mykenai's realm, which misses the point), not to an area merely covering the
  *     point (the Peloponnesos, 15 % of Hellas);
  *   - the representative point the geocoder gives an area is a placeholder too;
  *   - an unnamed placeholder keeps its areas by containment and is listed by `finding-placeholder`;
  *   - a precise point is matched as before; nobody loses their last place; a write and a rebuild agree.
  */
class PlaceholderPointSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d
  private def tol = cfg.places.pointToleranceKm

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zzph%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zzph%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def pt(lon: Double, lat: Double) = Some(s"""{"type":"Point","coordinates":[$lon,$lat]}""")
  private def box(x0: Double, y0: Double, x1: Double, y1: Double) =
    Some(s"""{"type":"Polygon","coordinates":[[[$x0,$y0],[$x1,$y0],[$x1,$y1],[$x0,$y1],[$x0,$y0]]]}""")

  private val Hellas = "zzph_hellas" // 150–154 E, 40–44 S: area 16
  private val Balkans = "zzph_balkans" // contains Hellas
  private val Pelop = "zzph_pelop" // covers the grid point; 2.34 = 15 % of Hellas
  private val Mykenai = "zzph_mykenai" // 150–152 E: 37.5 % of Hellas, misses the grid point; -1600..-1100
  private val Tegea = "zzph_tegea" // 3.5 km from the grid point
  private val Mantinea = "zzph_mantinea" // 7 km from the grid point, 11 km from Tegea
  private val NearRep = "zzph_nearrep" // 3 km from Hellas' representative point

  private val Grid = GeoPoint(152.5, -42.5) // a 5° and a 1° square's centre

  private lazy val rep: GeoPoint = connect(ds)(
    sql"SELECT ST_X(ST_PointOnSurface(geom)), ST_Y(ST_PointOnSurface(geom)) FROM places WHERE id = $Hellas"
      .query[(Double, Double)]
      .run()
      .head
  ) match
    case (x, y) => GeoPoint(x, y)

  private def seed(): Unit =
    def in(p: Place) = PlaceInput(p, List(PlaceName(p.label)))
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        in(Place(Hellas, "Zzph Hellas", PlaceKind.Region, box(150, -44, 154, -40))),
        in(Place(Balkans, "Zzph Balkans", PlaceKind.Region, box(149, -46, 156, -39))),
        in(Place(Pelop, "Zzph Peloponnesos", PlaceKind.Polity, box(151.0, -43.5, 152.8, -42.2))),
        in(Place(Mykenai, "Zzph Mykenai Realm", PlaceKind.Polity, box(150, -44, 152, -41), Some(Period(-1600, -1100)))),
        in(Place(Tegea, "Zzph Tegea", PlaceKind.Site, pt(152.52, -42.53))),
        in(Place(Mantinea, "Zzph Mantinea", PlaceKind.Site, pt(152.45, -42.45)))
      )
    )
    val r = rep
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(in(Place(NearRep, "Zzph Nearrep", PlaceKind.Site, pt(r.lon + 0.02, r.lat + 0.01))))
    )
    def f(n: Int, region: Option[String], at: Option[GeoPoint], period: Option[Period] = None) =
      Finding(s"zzph_src#$n", "zzph_src", s"claim $n", region, at, period, None, Nil, None)
    SourceRepo.upsert(
      ds,
      Source(
        citekey = "zzph_src",
        title = "Placeholder fixture",
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
          f(0, Some("Zzph Hellas"), Some(Grid), Some(Period(-1300, -1200))), // a model's "Greece" on round numbers
          f(1, None, Some(Grid), Some(Period(-1300, -1200))), // the same point, no name
          f(2, Some("Zzph Hellas"), Some(r)), // the geocoder's point for the name
          f(3, Some("Zzph Tegea"), Some(GeoPoint(152.52, -42.53))), // a precise point
          f(4, Some("Zzph Hellas"), Some(Grid), Some(Period(-500, -400)))
        )
      ), // after the Mykenai realm
      cfg.limits.chunkChars,
      tol
    )

  private def at(n: Int): Set[String] =
    PlaceFixtures.placesOf(ds, s"zzph_src#$n").map(_.id).filter(_.startsWith("zzph")).toSet

  private def holds(): Unit =
    assertEquals(
      at(0),
      Set(Hellas, Balkans, Mykenai),
      "its name: Hellas and what covers a quarter of it — not the Peloponnesos round the point, nor Tegea"
    )
    assertEquals(at(1), Set(Hellas, Balkans, Pelop), "no name: areas by containment, still no site")
    assertEquals(at(2), Set(Hellas, Balkans, Mykenai), "the representative point is a placeholder: no site near it")
    assertEquals(
      at(3),
      Set(Tegea, Hellas, Balkans, Pelop),
      "a precise point: as before (Mantinea is 11 km off, Mykenai west of it)"
    )
    assertEquals(at(4), Set(Hellas, Balkans), "the periods still decide")

  test("a placeholder point takes no site by distance; a resolved name decides its areas — on write and on rebuild"):
    seed()
    holds()
    val _ = PlaceFixtures.rebuildFindingPlaces(ds, tol)
    holds()

  test("re-matching only some places (materialisePlaces) agrees with the rebuild"):
    seed()
    connect(ds):
      val _ = sql"DELETE FROM finding_places WHERE finding_id LIKE 'zzph_src#%'".update.run()
    transact(ds):
      val _ = PlaceFixtures.materialisePlacesIn(List(Hellas, Balkans, Pelop, Mykenai, Tegea, Mantinea, NearRep), tol)
    holds()

  test(
    "the placeholder test: grid centres, an area's representative point, an explicit-only point; not a precise point"
  ):
    seed()
    def isPh(p: GeoPoint) = connect(ds)(
      Sql
        .raw(
          s"SELECT ${PlaceFixtures.placeholderSql("f")} FROM (SELECT ST_SetSRID(ST_MakePoint(${p.lon}, ${p.lat}), 4326) AS geom) f"
        )
        .frag
        .query[Boolean]
        .run()
        .head
    )
    assert(isPh(Grid))
    assert(isPh(rep))
    assert(!isPh(GeoPoint(152.52, -42.53)), "a site's own point")
    assert(!isPh(GeoPoint(rep.lon + 0.001, rep.lat)), "100 m off the representative point")
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        PlaceInput(
          Place("zzph_tribe", "Zzph Tribe", PlaceKind.People, pt(153.31, -41.17)),
          List(PlaceName("Zzph Tribe"))
        )
      )
    )
    assert(isPh(GeoPoint(153.31, -41.17)), "a people's point")

  test("finding-placeholder lists the placeholders no name resolves; nobody loses their last place"):
    seed()
    val listed = connect(ds)(PlaceFixtures.unresolvedPlaceholders(ds)).filter(_._1 == "zzph_src")
    assertEquals(listed.map(_._2), List("zzph_src#1: no place name, placeholder point at -42.5000 N 152.5000 E"))
    (0 to 4).foreach(n => assert(at(n).nonEmpty, s"finding $n has a place"))

  test("the placeholder area share is the configured one"):
    assertEquals(PlaceFixtures.DefaultPlaceholderAreaShare, cfg.places.placeholderAreaShare)
