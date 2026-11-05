package com.histmersion.util.database

import com.histmersion.api.BBox
import com.histmersion.util.database.{Param, Sql}

/** The PostGIS predicate for a viewport over a geometry column. A box with `minLon > maxLon` crosses the antimeridian
  * and becomes two envelopes OR'ed together (`minLon…180` and `-180…maxLon`).
  */
object EnvelopeSql:
  type EnvelopesGeom = EnvelopesGeom.T
  object EnvelopesGeom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def envelopes(geom: com.histmersion.util.database.EnvelopeSql.EnvelopesGeom, b: BBox): Sql =
    def env(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
      Sql.of(
        s"$geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)",
        Param.D(minLon),
        Param.D(minLat),
        Param.D(maxLon),
        Param.D(maxLat)
      )
    if b.crossesAntimeridian then
      val both = List(env(b.minLon, b.minLat, 180.0, b.maxLat), env(-180.0, b.minLat, b.maxLon, b.maxLat))
      Sql(both.map(_.text).mkString(" OR "), both.flatMap(_.params)).wrap("(", ")")
    else env(b.minLon, b.minLat, b.maxLon, b.maxLat)
