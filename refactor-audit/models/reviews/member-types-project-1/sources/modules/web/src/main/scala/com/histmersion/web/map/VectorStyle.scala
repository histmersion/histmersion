package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** The two vector styles drawn from the server's own extracts (docs/research-map-styles.md). */
enum VectorStyle(val id: String, val label: String):
  case Readable extends VectorStyle("readable", "Readable")
  case Physical extends VectorStyle("physical", "Physical")

object VectorStyle:
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.web.map.VectorStyle.ParseS): Option[VectorStyle] = values.find(_.id == s.trim.toLowerCase)
