package com.histmersion.domain.catalogue.places

import com.histmersion.api.catalogue.GeoTime
import com.histmersion.util.database.Transactor

/** The map's area and site layers as one GeoJSON FeatureCollection for a time the map picked: each state drawn once,
  * the best source's shape among those that describe it, the largest first up to `limit`. A mechanism over PostGIS with
  * no in-memory twin (as the tiles).
  */
trait PlaceMap:
  def shapes(kind: Option[String], time: GeoTime, limit: Int, modernFrom: Long): String

object PlaceMap:
  def apply(db: Transactor): PlaceMap = Implementation(db)

  final private class Implementation(db: Transactor) extends PlaceMap:
    def shapes(kind: Option[String], time: GeoTime, limit: Int, modernFrom: Long): String =
      db.read(PlaceRepo.geoJsonAt(kind, time, limit, modernFrom))
