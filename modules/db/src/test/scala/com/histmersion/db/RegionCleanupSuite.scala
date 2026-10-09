package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*

/** Point-only seed regions (2026-10-09), against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped
  * otherwise):
  *   - a region, polity or people known only by a point, and any atlas grid square's centre, takes a
  *     finding only by an explicit match — the finding's place name resolves to it, or the finding sits
  *     on its very point (not on a grid centre, which several places share) — never by distance (the 13 Regiones of Rome each took the same 59 findings;
  *     the Athenian tribe Aiantis 108); a site keeps its tolerance;
  *   - `places.location_precision` and its Scala twin agree; the geocoder ranks a grid point last.
  * Fixtures: [[RegionCleanupFixtures]]. */
class RegionCleanupSuite extends RegionCleanupFixtures:

  test("Rome's Regiones and the tribe Aiantis take findings only by an explicit match — on write and on rebuild"):
    seed()
    assignmentsHold()
    val _ = PlaceRepo.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)
    assignmentsHold()

  test("location_precision: the generated column and its Scala twin agree, Pleiades rows only"):
    seed()
    val stored = connect(ds)(sql"""SELECT id, location_precision FROM places WHERE id = ANY(${places.map(_.id)}::text[])"""
      .query[(String, Option[String])].run().toMap)
    places.foreach { p =>
      val c = """\[([-0-9.]+),([-0-9.]+)\]""".r.findFirstMatchIn(p.geometry.get).get
      val want = LocationPrecision.of(p.id, c.group(1).toDouble, c.group(2).toDouble)
      assertEquals(stored(p.id).flatMap(LocationPrecision.parse).getOrElse(LocationPrecision.Stated), want, p.id)
    }
    assertEquals(stored(Thess), Some("grid-cell"))
    assertEquals(stored(Regio1), None)
    // the SQL function over a spread of points, both hemispheres
    val pts = for x <- List(-12.5, -2.5, -0.25, 0.0, 0.5, 22.5, 22.29543, 33.75, 44.5, 140.25); y <- List(-47.75, -2.5, 16.25, 32.5, 37.5, 39.44387) yield (x, y)
    pts.foreach { (x, y) =>
      val sqlSays = connect(ds)(sql"SELECT histmersion_grid_centre($x, $y)".query[Boolean].run().head)
      assertEquals(sqlSays, LocationPrecision.gridCentre(x, y), s"($x, $y)")
    }

  test("the geocoder ranks a grid square's centre below a precise point of the same name"):
    seed()
    val precise = Place("pleiades_999100010", "Zzrc Thessalia", PlaceKind.Region, pt(141.9, -47.1), properties = types("region"))
    val _ = PlaceRepo.upsertPlaces(ds, List(PlaceInput(precise, List(PlaceName(precise.label)))))
    val r = Geocoder.resolve(ds, "Zzrc Thessalia", Geocode.KindHint.Unknown, None, cfg.places.geocodePlaceholderMin)
    assertEquals(r.map(_.placeId), Some("pleiades_999100010"))
    val alone = Geocoder.resolve(ds, "Zzrc Achaia", Geocode.KindHint.Unknown, None, cfg.places.geocodePlaceholderMin)
    assertEquals(alone.map(r => (r.placeId, r.approximate)), Some((Achaia, List(Geocode.Approximation.GridSquare))))
    assertEquals(PlaceRepo.get(ds, Thess).map(_.locationPrecision), Some(LocationPrecision.GridCell))
    assertEquals(PlaceRepo.get(ds, Regio1).map(_.locationPrecision), Some(LocationPrecision.Stated))

  test("the assignment's placeholder minimum is the configured one"):
    assertEquals(PlaceRepo.DefaultPlaceholderMin, cfg.places.geocodePlaceholderMin)

