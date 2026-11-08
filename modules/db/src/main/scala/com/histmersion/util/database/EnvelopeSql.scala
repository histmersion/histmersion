package com.histmersion
package util.database

import com.histmersion.api.BBox
import com.histmersion.util.database.{Param, Sql}

/** The PostGIS predicate for a viewport over a geometry column. A box with `minLon > maxLon` crosses the antimeridian
  * and becomes two envelopes OR'ed together (`minLon…180` and `-180…maxLon`).
  */
object EnvelopeSql:
  def envelopes(geom: String, b: BBox): Sql =
    def env(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
      Sql.of(
        Sql.OfText(s"$geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)"),
        Param.D(Param.D.V(minLon)),
        Param.D(Param.D.V(minLat)),
        Param.D(Param.D.V(maxLon)),
        Param.D(Param.D.V(maxLat))
      )
    if b.crossesAntimeridian then
      val both = List(
        env(b.minLon.value, b.minLat.value, 180.0, b.maxLat.value),
        env(-180.0, b.minLat.value, b.maxLon.value, b.maxLat.value)
      )
      Sql(Sql.Text(both.map(_.text.value).mkString(" OR ")), both.flatMap(_.params)).wrap("(", ")")
    else env(b.minLon.value, b.minLat.value, b.maxLon.value, b.maxLat.value)
