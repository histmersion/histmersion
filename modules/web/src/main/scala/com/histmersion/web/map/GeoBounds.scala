package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A region: south, west, north, east (east may pass 180 across the antimeridian). */
final case class GeoBounds(south: GeoBounds.South, west: GeoBounds.West, north: GeoBounds.North, east: GeoBounds.East):
  def contains(p: LatLon): Boolean =
    p.lat.value >= south.value && p.lat.value <= north.value && p.lon.value >= west.value && p.lon.value <= east.value
  def extend(o: GeoBounds): GeoBounds =
    GeoBounds(
      GeoBounds.South(math.min(south.value, o.south.value)),
      GeoBounds.West(math.min(west.value, o.west.value)),
      GeoBounds.North(math.max(north.value, o.north.value)),
      GeoBounds.East(math.max(east.value, o.east.value))
    )
  def valid: Boolean = south.value <= north.value && west.value <= east.value && !south.value.isNaN && !west.value.isNaN

object GeoBounds:
  /** The bounds of a GeoJSON geometry's coordinates (`None` for none). Pure over the parsed object. */

  type South = South.T
  object South extends RefinedType[Double, Pure]
  type West = West.T
  object West extends RefinedType[Double, Pure]
  type North = North.T
  object North extends RefinedType[Double, Pure]
  type East = East.T
  object East extends RefinedType[Double, Pure]

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
    Option.when(s <= n)(GeoBounds(South(s), West(w), North(n), East(e)))
