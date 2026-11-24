package com.histmersion
package util.database

import com.histmersion.api.BBox
import com.augustnagro.magnum.{sql, Frag}
import com.histmersion.lint.{LintRule, tolerated}

/** The PostGIS predicate for a viewport over a geometry column. A box with `minLon > maxLon` crosses the antimeridian
  * and becomes two envelopes OR'ed together (`minLon…180` and `-180…maxLon`).
  */
object EnvelopeSql:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def envelopes(geom: Frag, b: BBox): Frag =
    def env(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
      sql"$geom && ST_MakeEnvelope($minLon, $minLat, $maxLon, $maxLat, 4326)"
    if b.crossesAntimeridian then
      val both = List(
        env(b.minLon.value, b.minLat.value, 180.0, b.maxLat.value),
        env(-180.0, b.minLat.value, b.maxLon.value, b.maxLat.value)
      )
      sql"(${both.joined(sql" OR ")})"
    else env(b.minLon.value, b.minLat.value, b.maxLon.value, b.maxLat.value)
