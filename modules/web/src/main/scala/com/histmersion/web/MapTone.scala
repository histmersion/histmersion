package com.histmersion
package web

import com.raquo.laminar.api.L.*
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** Whether the base map under the names is light or dark — what the map's label colours follow (`.map-tone-light` /
  * `.map-tone-dark`: dark text on a light halo, or the inverse).
  */
enum MapTone(val wire: MapTone.Wire) derives CanEqual:
  case Light extends MapTone(MapTone.Wire("light"))
  case Dark extends MapTone(MapTone.Wire("dark"))

object MapTone:
  /** Pure: `ui.tiles-tone`'s value; anything but "dark" is the light default (OSM's own tiles). */
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]


  def parse(s: Wire): MapTone = if s.value.trim.equalsIgnoreCase("dark") then Dark else Light
