package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** The two vector styles drawn from the server's own extracts (docs/research-map-styles.md). */
enum VectorStyle(val id: VectorStyle.Id, val label: VectorStyle.Label):
  case Readable extends VectorStyle(com.histmersion.web.map.VectorStyle.Id("readable"), com.histmersion.web.map.VectorStyle.Label("Readable"))
  case Physical extends VectorStyle(com.histmersion.web.map.VectorStyle.Id("physical"), com.histmersion.web.map.VectorStyle.Label("Physical"))

object VectorStyle:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def parse(s: com.histmersion.web.map.VectorStyle.Id): Option[VectorStyle] = values.find(_.id == s.trim.toLowerCase)
