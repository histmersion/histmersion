package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.api.GeoPoint
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{Latitude, Longitude, Place}
import com.histmersion.api.library.{Finding, Source}
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{LintRule, tolerated}

/** The fixtures of [[RegionCleanupSuite]] and [[PlaceReviewSuite]]: a city with two Regiones, an Athenian tribe beside
  * Athens, provinces and a site on atlas grid squares' centres, a withdrawn duplicate and its replacement, a deme filed
  * as a region. Around 140–145°E 45–50°S, where no other suite has any; ids are `pleiades_99910…` and `zzrc…`, the
  * source `zzrc_src`.
  */
trait RegionCleanupFixtures extends DbSuite with Bounded:
  protected val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  protected lazy val cfg = AppConfig.load()
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  protected def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zzrc%'".update.run()
    val _ = sql"DELETE FROM place_aliases WHERE alias LIKE 'pleiades_99910%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'pleiades_99910%' OR id LIKE 'zzrc%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected def pt(lon: Longitude, lat: Latitude) =
    Some(Place.Geometry(s"""{"type":"Point","coordinates":[${lon.value},${lat.value}]}"""))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected def types(t: String) = Some(Place.Properties(s"""{"feature_types":"$t"}"""))

  protected val City = "zzrc_city"
  protected val Regio1 = "pleiades_999100001"
  protected val Regio2 = "pleiades_999100002"
  protected val Aiantis = "pleiades_999100003"
  protected val Athens = "zzrc_athens"
  protected val Thess = "pleiades_999100004" // a province on a 5° cell's centre
  protected val Achaia = "pleiades_999100005" // a region on a ½° cell's centre
  protected val Grid = "pleiades_999100006" // a SITE on a 1° cell's centre
  protected val Dup = "pleiades_999100007"
  protected val Into = "pleiades_999100008"
  protected val Peir = "pleiades_999100009"

  protected val AiantisId = domain.catalogue.Place.Id("pleiades_999100003")
  protected val ThessId = domain.catalogue.Place.Id("pleiades_999100004")
  protected val DupId = domain.catalogue.Place.Id("pleiades_999100007")
  protected val PeirId = domain.catalogue.Place.Id("pleiades_999100009")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected val places = List(
    Place(
      Place.Id(City),
      Place.Label("Zzrc City"),
      Place.KindValue(Place.Kind.Site),
      pt(Longitude(140.0), Latitude(-50.0))
    ),
    Place(
      Place.Id(Regio1),
      Place.Label("Zzrc Regio I (Porta Capena)"),
      Place.KindValue(Place.Kind.Region),
      pt(Longitude(140.02), Latitude(-50.01)),
      properties = types("region")
    ),
    Place(
      Place.Id(Regio2),
      Place.Label("Zzrc Regio II (Caelimontium)"),
      Place.KindValue(Place.Kind.Region),
      pt(Longitude(140.03), Latitude(-49.99)),
      properties = types("region")
    ),
    Place(
      Place.Id(Aiantis),
      Place.Label("Zzrc Aiantis"),
      Place.KindValue(Place.Kind.Region),
      pt(Longitude(140.2), Latitude(-50.0)),
      properties = types("people")
    ),
    Place(
      Place.Id(Athens),
      Place.Label("Zzrc Athens"),
      Place.KindValue(Place.Kind.Site),
      pt(Longitude(140.21), Latitude(-50.02))
    ),
    Place(
      Place.Id(Thess),
      Place.Label("Zzrc Thessalia"),
      Place.KindValue(Place.Kind.Region),
      pt(Longitude(142.5), Latitude(-47.5)),
      properties = types("province")
    ),
    Place(
      Place.Id(Achaia),
      Place.Label("Zzrc Achaia"),
      Place.KindValue(Place.Kind.Region),
      pt(Longitude(141.25), Latitude(-47.75)),
      properties = types("region")
    ),
    Place(
      Place.Id(Grid),
      Place.Label("Zzrc Gridsite"),
      Place.KindValue(Place.Kind.Site),
      pt(Longitude(143.5), Latitude(-46.5)),
      properties = types("settlement")
    ),
    Place(
      Place.Id(Dup),
      Place.Label("Withdrawn (duplicate): Zzrc Creta et Cyrene"),
      Place.KindValue(Place.Kind.Region),
      pt(Longitude(144.0), Latitude(-45.0)),
      properties = types("province")
    ),
    Place(
      Place.Id(Into),
      Place.Label("Zzrc Creta et Cyrene (Roman province)"),
      Place.KindValue(Place.Kind.Site),
      pt(Longitude(144.3), Latitude(-45.2)),
      properties = types("province-2")
    ),
    Place(
      Place.Id(Peir),
      Place.Label("Zzrc Peiraieus"),
      Place.KindValue(Place.Kind.Region),
      pt(Longitude(140.25), Latitude(-50.05)),
      properties = types("region,settlement,deme-attic")
    )
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected def f(n: Int, region: Option[Finding.Region], at: Option[GeoPoint]) =
    Finding(
      Finding.Id(s"zzrc_src#$n"),
      Citekey("zzrc_src"),
      Finding.Claim(s"claim $n"),
      region,
      at,
      None,
      None,
      Nil,
      None
    )

  protected val findings = List(
    f(0, Some(Finding.Region("Zzrc City")), Some(GeoPoint(Longitude(140.0), Latitude(-50.0)))),
    f(
      1,
      Some(Finding.Region("Zzrc Regio I (Porta Capena)")),
      Some(GeoPoint(Longitude(140.0), Latitude(-50.0)))
    ), // named, placed on the city
    f(2, Some(Finding.Region("Zzrc Athens")), Some(GeoPoint(Longitude(140.21), Latitude(-50.02)))),
    f(
      3,
      Some(Finding.Region("Zzrc Aiantis")),
      Some(GeoPoint(Longitude(140.2), Latitude(-50.0)))
    ), // on the tribe's own point
    f(
      4,
      Some(Finding.Region("near the grid site")),
      Some(GeoPoint(Longitude(143.52), Latitude(-46.51)))
    ), // 2 km from a grid centre
    f(5, Some(Finding.Region("Zzrc Achaia")), None), // a name, no point
    f(6, Some(Finding.Region("Zzrc Hellas")), Some(GeoPoint(Longitude(142.5), Latitude(-47.5))))
  ) // round numbers ON a grid centre

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected def seed(): Unit =
    val _ =
      PlaceFixtures.upsertPlaces(ds, places.map(p => Place.Input(p, List(Place.Name(Place.Name.Value(p.label.value))))))
    SourceFixtures.upsert(
      ds,
      Source(
        citekey = Citekey("zzrc_src"),
        title = api.library.Title("Region cleanup fixture"),
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
        findings = findings
      ),
      cfg.limits.chunkChars,
      cfg.places.pointToleranceKm
    )

  /** The fixture places finding `n` belongs to. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected def at(n: Int): Set[Place.Id] =
    PlaceFixtures
      .placesOf(ds, Finding.Id(s"zzrc_src#$n"))
      .map(_.id)
      .filter(id => id.value.startsWith("pleiades_99910") || id.value.startsWith("zzrc"))
      .toSet

  protected def assignmentsHold(): Unit =
    assertEquals(at(0), Set(City), "the Regiones are within 10 km of the city, and take nothing by distance")
    assertEquals(
      at(1),
      Set(City, Regio1),
      "its NAME resolves to Regio I: an explicit match; Regio II, as near, gets none"
    )
    assertEquals(at(2), Set(Athens), "the tribe's point is 2 km away: no evidence")
    assertEquals(
      at(3),
      Set(Aiantis),
      "a finding ON the tribe's point belongs to it — and, the point being a placeholder, to no site near it"
    )
    assertEquals(at(4), Set.empty[String], "a grid square's centre takes nothing by distance, even a site's")
    assertEquals(at(5), Set(Achaia), "a name with no point: the geocoder's choice, a grid-square region")
    assertEquals(at(6), Set.empty[String], "ON a grid centre with another name: a grid point decides nothing")
