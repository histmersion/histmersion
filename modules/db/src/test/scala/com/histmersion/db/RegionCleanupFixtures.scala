package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.values.{PlaceKind, SourceStatus}

/** The fixtures of [[RegionCleanupSuite]] and [[PlaceReviewSuite]]: a city with two Regiones, an
  * Athenian tribe beside Athens, provinces and a site on atlas grid squares' centres, a withdrawn
  * duplicate and its replacement, a deme filed as a region. Around 140–145°E 45–50°S, where no other
  * suite has any; ids are `pleiades_99910…` and `zzrc…`, the source `zzrc_src`. */
trait RegionCleanupFixtures extends com.histmersion.db.DbSuite with Bounded:
  protected val testDb              = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  protected lazy val cfg = AppConfig.load()
  protected lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d

  protected def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zzrc%'".update.run()
    val _ = sql"DELETE FROM place_aliases WHERE alias LIKE 'pleiades_99910%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'pleiades_99910%' OR id LIKE 'zzrc%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit                       = if testDb.isDefined then clean()

  protected def pt(lon: Double, lat: Double) = Some(s"""{"type":"Point","coordinates":[$lon,$lat]}""")
  protected def types(t: String) = Some(s"""{"feature_types":"$t"}""")

  protected val City    = "zzrc_city"
  protected val Regio1  = "pleiades_999100001"
  protected val Regio2  = "pleiades_999100002"
  protected val Aiantis = "pleiades_999100003"
  protected val Athens  = "zzrc_athens"
  protected val Thess   = "pleiades_999100004"   // a province on a 5° cell's centre
  protected val Achaia  = "pleiades_999100005"   // a region on a ½° cell's centre
  protected val Grid    = "pleiades_999100006"   // a SITE on a 1° cell's centre
  protected val Dup     = "pleiades_999100007"
  protected val Into    = "pleiades_999100008"
  protected val Peir    = "pleiades_999100009"

  protected val places = List(
    Place(City, "Zzrc City", PlaceKind.Site, pt(140.0, -50.0)),
    Place(Regio1, "Zzrc Regio I (Porta Capena)", PlaceKind.Region, pt(140.02, -50.01), properties = types("region")),
    Place(Regio2, "Zzrc Regio II (Caelimontium)", PlaceKind.Region, pt(140.03, -49.99), properties = types("region")),
    Place(Aiantis, "Zzrc Aiantis", PlaceKind.Region, pt(140.2, -50.0), properties = types("people")),
    Place(Athens, "Zzrc Athens", PlaceKind.Site, pt(140.21, -50.02)),
    Place(Thess, "Zzrc Thessalia", PlaceKind.Region, pt(142.5, -47.5), properties = types("province")),
    Place(Achaia, "Zzrc Achaia", PlaceKind.Region, pt(141.25, -47.75), properties = types("region")),
    Place(Grid, "Zzrc Gridsite", PlaceKind.Site, pt(143.5, -46.5), properties = types("settlement")),
    Place(Dup, "Withdrawn (duplicate): Zzrc Creta et Cyrene", PlaceKind.Region, pt(144.0, -45.0), properties = types("province")),
    Place(Into, "Zzrc Creta et Cyrene (Roman province)", PlaceKind.Site, pt(144.3, -45.2), properties = types("province-2")),
    Place(Peir, "Zzrc Peiraieus", PlaceKind.Region, pt(140.25, -50.05), properties = types("region,settlement,deme-attic")))

  protected def f(n: Int, region: Option[String], at: Option[GeoPoint]) =
    Finding(s"zzrc_src#$n", "zzrc_src", s"claim $n", region, at, None, None, Nil, None)

  protected val findings = List(
    f(0, Some("Zzrc City"), Some(GeoPoint(140.0, -50.0))),
    f(1, Some("Zzrc Regio I (Porta Capena)"), Some(GeoPoint(140.0, -50.0))), // named, placed on the city
    f(2, Some("Zzrc Athens"), Some(GeoPoint(140.21, -50.02))),
    f(3, Some("Zzrc Aiantis"), Some(GeoPoint(140.2, -50.0))),                 // on the tribe's own point
    f(4, Some("near the grid site"), Some(GeoPoint(143.52, -46.51))),        // 2 km from a grid centre
    f(5, Some("Zzrc Achaia"), None),                                          // a name, no point
    f(6, Some("Zzrc Hellas"), Some(GeoPoint(142.5, -47.5))))                  // round numbers ON a grid centre

  protected def seed(): Unit =
    val _ = PlaceRepo.upsertPlaces(ds, places.map(p => PlaceInput(p, List(PlaceName(p.label)))))
    SourceRepo.upsert(ds, Source(citekey = "zzrc_src", title = "Region cleanup fixture", authors = List("T"), doi = None,
      year = Some(2026), venue = None, status = SourceStatus.Current, topics = Nil, regions = Nil, tags = Nil,
      period = None, summary = None, citedByCount = None, influentialCitations = None, provenance = None,
      calibreId = None, findings = findings), cfg.limits.chunkChars, cfg.places.pointToleranceKm)

  /** The fixture places finding `n` belongs to. */
  protected def at(n: Int): Set[String] =
    PlaceRepo.placesOf(ds, s"zzrc_src#$n").map(_.id).filter(id => id.startsWith("pleiades_99910") || id.startsWith("zzrc")).toSet

  protected def assignmentsHold(): Unit =
    assertEquals(at(0), Set(City), "the Regiones are within 10 km of the city, and take nothing by distance")
    assertEquals(at(1), Set(City, Regio1), "its NAME resolves to Regio I: an explicit match; Regio II, as near, gets none")
    assertEquals(at(2), Set(Athens), "the tribe's point is 2 km away: no evidence")
    assertEquals(at(3), Set(Aiantis), "a finding ON the tribe's point belongs to it — and, the point being a placeholder, to no site near it")
    assertEquals(at(4), Set.empty[String], "a grid square's centre takes nothing by distance, even a site's")
    assertEquals(at(5), Set(Achaia), "a name with no point: the geocoder's choice, a grid-square region")
    assertEquals(at(6), Set.empty[String], "ON a grid centre with another name: a grid point decides nothing")

