package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** A region: south, west, north, east (east may pass 180 across the antimeridian). */
final case class GeoBounds(south: GeoBounds.South, west: GeoBounds.West, north: GeoBounds.North, east: GeoBounds.East):
  def contains(p: LatLon): com.histmersion.web.map.GeoBounds.ContainsResult = p.lat >= south && p.lat <= north && p.lon >= west && p.lon <= east
  def extend(o: GeoBounds): GeoBounds =
    GeoBounds(math.min(south, o.south), math.min(west, o.west), math.max(north, o.north), math.max(east, o.east))
  def valid: com.histmersion.web.map.GeoBounds.ValidResult = south <= north && west <= east && !south.isNaN && !west.isNaN

object GeoBounds:
  /** The bounds of a GeoJSON geometry's coordinates (`None` for none). Pure over the parsed object. */
  type ContainsResult = ContainsResult.T
  object ContainsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValidResult = ValidResult.T
  object ValidResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type South = South.T
  object South extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type West = West.T
  object West extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type North = North.T
  object North extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type East = East.T
  object East extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def ofGeometry(g: js.Dynamic): Option[GeoBounds] =
    // `var`s: one pass over every coordinate of the geometry
    var s = Double.MaxValue
    var w = Double.MaxValue
    var n = -Double.MaxValue
    var e = -Double.MaxValue
    def walk(c: js.Dynamic): Unit =
      if js.Array.isArray(c) then
        val a = c.asInstanceOf[js.Array[js.Dynamic]]
        if a.length >= 2 && js.typeOf(a(0)) == "number" then
          val lon = a(0).asInstanceOf[Double]
          val lat = a(1).asInstanceOf[Double]
          if lat < s then s = lat
          if lat > n then n = lat
          if lon < w then w = lon
          if lon > e then e = lon
        else a.foreach(walk)
    if !js.isUndefined(g) && g != null then
      if !js.isUndefined(g.geometries) then
        g.geometries.asInstanceOf[js.Array[js.Dynamic]].foreach(x => walk(x.coordinates))
      else walk(g.coordinates)
    Option.when(s <= n)(GeoBounds(s, w, n, e))
