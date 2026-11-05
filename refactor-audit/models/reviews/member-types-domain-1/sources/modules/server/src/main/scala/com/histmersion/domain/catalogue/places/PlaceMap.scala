package com.histmersion.domain.catalogue.places

import com.histmersion.api.catalogue.GeoTime
import com.histmersion.util.database.Transactor

/** The map's area and site layers as one GeoJSON FeatureCollection for a time the map picked: each state drawn once,
  * the best source's shape among those that describe it, the largest first up to `limit`. A mechanism over PostGIS with
  * no in-memory twin (as the tiles).
  */
trait PlaceMap:
  def shapes(kind: Option[com.histmersion.domain.catalogue.places.PlaceMap.ShapesKind], time: GeoTime, limit: com.histmersion.domain.catalogue.places.PlaceMap.ShapesLimit, modernFrom: com.histmersion.domain.catalogue.places.PlaceMap.ShapesModernFrom): com.histmersion.domain.catalogue.places.PlaceMap.ShapesResult

object PlaceMap:
  type ShapesResult = ShapesResult.T
  object ShapesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShapesKind = ShapesKind.T
  object ShapesKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShapesLimit = ShapesLimit.T
  object ShapesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShapesModernFrom = ShapesModernFrom.T
  object ShapesModernFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): PlaceMap = Implementation(db)

  final private class Implementation(db: Transactor) extends PlaceMap:
    def shapes(kind: Option[com.histmersion.domain.catalogue.places.PlaceMap.Implementation.ShapesKind], time: GeoTime, limit: com.histmersion.domain.catalogue.places.PlaceMap.Implementation.ShapesLimit, modernFrom: com.histmersion.domain.catalogue.places.PlaceMap.Implementation.ShapesModernFrom): com.histmersion.domain.catalogue.places.PlaceMap.Implementation.ShapesResult =
      db.read(PlaceRepo.geoJsonAt(kind, time, limit, modernFrom))

  object Implementation:
    type ShapesResult = ShapesResult.T
    object ShapesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShapesKind = ShapesKind.T
    object ShapesKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShapesLimit = ShapesLimit.T
    object ShapesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShapesModernFrom = ShapesModernFrom.T
    object ShapesModernFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
