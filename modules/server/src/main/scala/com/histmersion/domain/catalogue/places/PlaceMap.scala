package com.histmersion
package domain.catalogue.places

import com.histmersion.api.catalogue.GeoTime
import com.histmersion.domain.DeepTime
import com.histmersion.util.database.Transactor
import com.histmersion.domain.catalogue.Place
import com.histmersion.lint.{tolerated, LintRule}

/** The map's area and site layers as one GeoJSON FeatureCollection for a time the map picked: each state drawn once,
  * the best source's shape among those that describe it, the largest first up to `limit`. A mechanism over PostGIS with
  * no in-memory twin (as the tiles).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait PlaceMap:
  def shapes(
      kind: Option[Place.Kind.Wire],
      time: GeoTime,
      limit: PageSize,
      modernFrom: DeepTime.SignedYear
  ): PlaceDetailRow.Geometry

object PlaceMap:
  def apply(db: Transactor): PlaceMap = Implementation(db)

  final private class Implementation(db: Transactor) extends PlaceMap:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def shapes(
        kind: Option[Place.Kind.Wire],
        time: GeoTime,
        limit: PageSize,
        modernFrom: DeepTime.SignedYear
    ): PlaceDetailRow.Geometry =
      PlaceDetailRow.Geometry(db.read(PlaceRepo.geoJsonAt(kind, time, PlaceRepo.Limit(limit.value), modernFrom)).value)
