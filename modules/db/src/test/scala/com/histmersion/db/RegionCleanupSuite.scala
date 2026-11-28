package com.histmersion
package db

import com.histmersion.api.catalogue.geocode.Attempt
import com.histmersion.api.catalogue.geocode.KindHint
import com.histmersion.api.catalogue.geocode.Approximation

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.{Geocode, Place}
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.domain.catalogue.places.GeocodeSettings
import com.histmersion.lint.{tolerated, LintRule}

/** Point-only seed regions (2026-10-09), against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise):
  *   - a region, polity or people known only by a point, and any atlas grid square's centre, takes a finding only by an
  *     explicit match — the finding's place name resolves to it, or the finding sits on its very point (not on a grid
  *     centre, which several places share) — never by distance (the 13 Regiones of Rome each took the same 59 findings;
  *     the Athenian tribe Aiantis 108); a site keeps its tolerance;
  *   - `places.location_precision` and its Scala twin agree; the geocoder ranks a grid point last.
  * Fixtures: [[RegionCleanupFixtures]].
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class RegionCleanupSuite extends RegionCleanupFixtures:

  test("Rome's Regiones and the tribe Aiantis take findings only by an explicit match — on write and on rebuild"):
    seed()
    assignmentsHold()
    val _ = PlaceFixtures.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)
    assignmentsHold()

  test("location_precision: the generated column and its Scala twin agree, Pleiades rows only"):
    seed()
    val stored = connect(ds)(
      sql"""SELECT id, location_precision FROM places WHERE id = ANY(${places.map(_.id)}::text[])"""
        .query[(String, Option[String])]
        .run()
        .toMap
    )
    places.foreach { p =>
      val c = """\[([-0-9.]+),([-0-9.]+)\]""".r.findFirstMatchIn(p.geometry.get.value).get
      val want = Place.Precision.of(p.id, Longitude(c.group(1).toDouble), Latitude(c.group(2).toDouble))
      assertEquals(
        stored(p.id.value)
          .flatMap(s => Place.Precision.parse(Place.Precision.Wire(s)))
          .getOrElse(Place.Precision.Stated),
        want,
        p.id
      )
    }
    assertEquals(stored(Thess), Some("grid-cell"))
    assertEquals(stored(Regio1), None)
    // the SQL function over a spread of points, both hemispheres
    val pts = for
      x <- List(-12.5, -2.5, -0.25, 0.0, 0.5, 22.5, 22.29543, 33.75, 44.5, 140.25);
      y <- List(-47.75, -2.5, 16.25, 32.5, 37.5, 39.44387)
    yield (x, y)
    pts.foreach { (x, y) =>
      val sqlSays = connect(ds)(sql"SELECT histmersion_grid_centre($x, $y)".query[Boolean].run().head)
      assertEquals(sqlSays, Place.Precision.gridCentre(Longitude(x), Latitude(y)), s"($x, $y)")
    }

  test("the geocoder ranks a grid square's centre below a precise point of the same name"):
    seed()
    val precise =
      Place(
        Place.Id("pleiades_999100010"),
        Place.Label("Zzrc Thessalia"),
        Place.KindValue(domain.catalogue.Place.Kind.Region),
        pt(Longitude(141.9), Latitude(-47.1)),
        properties = types("region")
      )
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(Place.Input(precise, List(Place.Name(Place.Name.Value(precise.label.value)))))
    )
    val r =
      connect(ds)(
        PlaceFixtures.resolve(
          Attempt.Name("Zzrc Thessalia"),
          KindHint.Unknown,
          None,
          GeocodeSettings.PlaceholderMin(cfg.places.geocodePlaceholderMin.value)
        )
      )
    assertEquals(r.map(_.placeId), Some("pleiades_999100010"))
    val alone =
      connect(ds)(
        PlaceFixtures.resolve(
          Attempt.Name("Zzrc Achaia"),
          KindHint.Unknown,
          None,
          GeocodeSettings.PlaceholderMin(cfg.places.geocodePlaceholderMin.value)
        )
      )
    assertEquals(alone.map(r => (r.placeId, r.approximate)), Some((Achaia, List(Approximation.GridSquare))))
    assertEquals(
      PlaceFixtures.get(ds, domain.catalogue.Place.Id.applyUnsafe(Thess)).map(_.locationPrecision),
      Some(Place.Precision.GridCell)
    )
    assertEquals(
      PlaceFixtures.get(ds, domain.catalogue.Place.Id.applyUnsafe(Regio1)).map(_.locationPrecision),
      Some(Place.Precision.Stated)
    )

  test("the assignment's placeholder minimum is the configured one"):
    assertEquals(PlaceFixtures.DefaultPlaceholderMin.value, cfg.places.geocodePlaceholderMin.value)
