package com.histmersion.util.database

import com.histmersion.api.BBox
import com.histmersion.util.database.{Param, Sql}

/** The PostGIS predicate for a viewport over a geometry column. A box with `minLon > maxLon` crosses the antimeridian
  * and becomes two envelopes OR'ed together (`minLon…180` and `-180…maxLon`).
  */
object EnvelopeSql:
  type EnvelopesEnvMinLon = EnvelopesEnvMinLon.T
  object EnvelopesEnvMinLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvelopesEnvMinLat = EnvelopesEnvMinLat.T
  object EnvelopesEnvMinLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvelopesEnvMaxLon = EnvelopesEnvMaxLon.T
  object EnvelopesEnvMaxLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvelopesEnvMaxLat = EnvelopesEnvMaxLat.T
  object EnvelopesEnvMaxLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def envelopes(geom: String, b: BBox): Sql =
    def env(minLon: com.histmersion.util.database.EnvelopeSql.EnvelopesEnvMinLon, minLat: com.histmersion.util.database.EnvelopeSql.EnvelopesEnvMinLat, maxLon: com.histmersion.util.database.EnvelopeSql.EnvelopesEnvMaxLon, maxLat: com.histmersion.util.database.EnvelopeSql.EnvelopesEnvMaxLat) =
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
