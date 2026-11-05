package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** Which shape is under a point: pure geometry over GeoJSON, so finding what the pointer is over never depends on what
  * the library has rendered yet.
  */
object ShapeHits:
  /** Is (`lat`, `lon`) inside the polygon or multipolygon `g` (even–odd over every ring: holes are outside)? Points and
    * lines contain nothing. Pure.
    */
  type ContainsRingResult = ContainsRingResult.T
  object ContainsRingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContainsPolygonResult = ContainsPolygonResult.T
  object ContainsPolygonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ContainsResult = ContainsResult.T
  object ContainsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContainsLat = ContainsLat.T
  object ContainsLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContainsLon = ContainsLon.T
  object ContainsLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def contains(g: js.Dynamic, lat: com.histmersion.web.map.ShapeHits.ContainsLat, lon: com.histmersion.web.map.ShapeHits.ContainsLon): com.histmersion.web.map.ShapeHits.ContainsResult =
    if js.isUndefined(g) || g == null then false
    else
      def ring(r: js.Array[js.Array[Double]]): com.histmersion.web.map.ShapeHits.ContainsRingResult =
        // `var`s: the crossing count's walk over the ring's edges
        var inside = false
        var j = r.length - 1
        var i = 0
        while i < r.length do
          val (xi, yi, xj, yj) = (r(i)(0), r(i)(1), r(j)(0), r(j)(1))
          if (yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi then inside = !inside
          j = i
          i += 1
        inside
      def polygon(p: js.Array[js.Array[js.Array[Double]]]): com.histmersion.web.map.ShapeHits.ContainsPolygonResult = p.count(ring) % 2 == 1
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
          g.coordinates.selectDynamic("1").asInstanceOf[Double],
          g.coordinates.selectDynamic("0").asInstanceOf[Double]
        )
      )
