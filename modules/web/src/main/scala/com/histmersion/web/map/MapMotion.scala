package com.histmersion.web.map

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js

/** The reader's "Map motion" setting (#4718), kept per device: follow the system's reduced-motion setting (the
  * default), always animate, or never.
  */
enum MapMotion(val wire: String, val label: String):
  case System extends MapMotion("system", "Follow the system")
  case Always extends MapMotion("always", "Always animate")
  case Never extends MapMotion("never", "Never animate")

object MapMotion:
  def fromWire(s: String): Option[MapMotion] = values.find(_.wire == s.trim.toLowerCase)

  /** Pure: does the map animate under choice `m` when the system asks for reduced motion (`systemReduced`)? */
  def animates(m: MapMotion, systemReduced: Boolean): Boolean = m match
    case System => !systemReduced
    case Always => true
    case Never  => false

  /** Pure: is the map still only because the system asks for it (the setting follows the system)? The menu says so
    * under the setting.
    */
  def heldBySystem(m: MapMotion, systemReduced: Boolean): Boolean = m == System && systemReduced

  /** Pure: MapLibre's `reduceMotion` option for a map made under `m` — `None` leaves MapLibre to read the system's
    * setting itself (it reads it on every camera move, so it follows a change at once).
    */
  def mapLibreReduce(m: MapMotion): Option[Boolean] = m match
    case System => None
    case Always => Some(false)
    case Never  => Some(true)

  /** Pure: the value of the root element's `data-map-motion`, which the stylesheet's map animations read. */
  def attribute(animates: Boolean): String = if animates then "animate" else "still"
