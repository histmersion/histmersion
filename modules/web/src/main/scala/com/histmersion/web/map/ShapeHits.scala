package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.Geo
import com.histmersion.lint.{tolerated, LintRule}

/** Which shape is under a point: pure geometry over GeoJSON, so finding what the pointer is over never depends on what
  * the library has rendered yet.
  */
@tolerated(LintRule.Helper, "pending G63 (no free-standing helper objects)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object ShapeHits:
  /** Is (`lat`, `lon`) inside the polygon or multipolygon `g` (even–odd over every ring: holes are outside)? Points and
    * lines contain nothing. Pure.
    */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def contains(g: js.Dynamic, lat: Geo.Latitude, lon: Geo.Longitude): Boolean =
    if js.isUndefined(g) || g == null then false
    else
      val (la, lo) = (lat.value, lon.value)
      def ring(r: js.Array[js.Array[Double]]): Boolean =
        // `var`s: the crossing count's walk over the ring's edges
        var inside = false
        var j = r.length - 1
        var i = 0
        while i < r.length do
          val (xi, yi, xj, yj) = (r(i)(0), r(i)(1), r(j)(0), r(j)(1))
          if (yi > la) != (yj > la) && lo < (xj - xi) * (la - yi) / (yj - yi) + xi then inside = !inside
          j = i
          i += 1
        inside
      def polygon(p: js.Array[js.Array[js.Array[Double]]]): Boolean = p.count(ring) % 2 == 1
      g.selectDynamic("type").toString match
        case "Polygon"      => polygon(g.coordinates.asInstanceOf[js.Array[js.Array[js.Array[Double]]]])
        case "MultiPolygon" =>
          g.coordinates.asInstanceOf[js.Array[js.Array[js.Array[js.Array[Double]]]]].exists(polygon)
        case "GeometryCollection" => g.geometries.asInstanceOf[js.Array[js.Dynamic]].exists(contains(_, lat, lon))
        case _                    => false

  /** The point of a Point geometry (`None` for any other). Pure. */
  def point(g: js.Dynamic): Option[LatLon] =
    if js.isUndefined(g) || g == null || g.selectDynamic("type").toString != "Point" then None
    else
      Some(
        LatLon(
          LatLon.Lat(g.coordinates.selectDynamic("1").asInstanceOf[Double]),
          LatLon.Lon(g.coordinates.selectDynamic("0").asInstanceOf[Double])
        )
      )
