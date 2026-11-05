package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js

/** Whether the base map under the names is light or dark — what the map's label colours follow (`.map-tone-light` /
  * `.map-tone-dark`: dark text on a light halo, or the inverse).
  */
enum MapTone(val wire: String):
  case Light extends MapTone("light")
  case Dark extends MapTone("dark")

object MapTone:
  /** Pure: `ui.tiles-tone`'s value; anything but "dark" is the light default (OSM's own tiles). */
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.web.MapTone.ParseS): MapTone = if s.trim.equalsIgnoreCase("dark") then Dark else Light
