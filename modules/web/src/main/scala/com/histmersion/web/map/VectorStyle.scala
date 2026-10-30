package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** The two vector styles drawn from the server's own extracts (docs/research-map-styles.md). */
enum VectorStyle(val id: String, val label: String):
  case Readable extends VectorStyle("readable", "Readable")
  case Physical extends VectorStyle("physical", "Physical")

object VectorStyle:
  def parse(s: String): Option[VectorStyle] = values.find(_.id == s.trim.toLowerCase)
