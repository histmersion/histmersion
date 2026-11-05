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
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnimatesResult = AnimatesResult.T
  object AnimatesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnimatesSystemReduced = AnimatesSystemReduced.T
  object AnimatesSystemReduced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeldBySystemResult = HeldBySystemResult.T
  object HeldBySystemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeldBySystemSystemReduced = HeldBySystemSystemReduced.T
  object HeldBySystemSystemReduced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapLibreReduceResult = MapLibreReduceResult.T
  object MapLibreReduceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttributeResult = AttributeResult.T
  object AttributeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttributeAnimates = AttributeAnimates.T
  object AttributeAnimates extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def fromWire(s: com.histmersion.web.map.MapMotion.FromWireS): Option[MapMotion] = values.find(_.wire == s.trim.toLowerCase)

  /** Pure: does the map animate under choice `m` when the system asks for reduced motion (`systemReduced`)? */
  def animates(m: MapMotion, systemReduced: com.histmersion.web.map.MapMotion.AnimatesSystemReduced): com.histmersion.web.map.MapMotion.AnimatesResult = m match
    case System => !systemReduced
    case Always => true
    case Never  => false

  /** Pure: is the map still only because the system asks for it (the setting follows the system)? The menu says so
    * under the setting.
    */
  def heldBySystem(m: MapMotion, systemReduced: com.histmersion.web.map.MapMotion.HeldBySystemSystemReduced): com.histmersion.web.map.MapMotion.HeldBySystemResult = m == System && systemReduced

  /** Pure: MapLibre's `reduceMotion` option for a map made under `m` — `None` leaves MapLibre to read the system's
    * setting itself (it reads it on every camera move, so it follows a change at once).
    */
  def mapLibreReduce(m: MapMotion): Option[com.histmersion.web.map.MapMotion.MapLibreReduceResult] = m match
    case System => None
    case Always => Some(false)
    case Never  => Some(true)

  /** Pure: the value of the root element's `data-map-motion`, which the stylesheet's map animations read. */
  def attribute(animates: com.histmersion.web.map.MapMotion.AttributeAnimates): com.histmersion.web.map.MapMotion.AttributeResult = if animates then "animate" else "still"
