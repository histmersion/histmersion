package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** A region: south, west, north, east (east may pass 180 across the antimeridian). */
final case class GeoBounds(south: Double, west: Double, north: Double, east: Double):
  def contains(p: LatLon): Boolean = p.lat >= south && p.lat <= north && p.lon >= west && p.lon <= east
  def extend(o: GeoBounds): GeoBounds =
    GeoBounds(math.min(south, o.south), math.min(west, o.west), math.max(north, o.north), math.max(east, o.east))
  def valid: Boolean = south <= north && west <= east && !south.isNaN && !west.isNaN

object GeoBounds:
  /** The bounds of a GeoJSON geometry's coordinates (`None` for none). Pure over the parsed object. */
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
