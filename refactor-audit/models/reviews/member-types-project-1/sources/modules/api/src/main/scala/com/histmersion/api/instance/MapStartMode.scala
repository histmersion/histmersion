package com.histmersion.api.instance

import com.histmersion.api.catalogue.Place
import com.histmersion.api.given
enum MapStartMode(val wire: String, val label: String):
  case Last extends MapStartMode("last", "Where I last left it")
  case Place extends MapStartMode("place", "At a place I choose")

object MapStartMode:
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.instance.MapStartMode.ParseS): MapStartMode = values.find(_.wire == Option(s).fold("")(_.trim.toLowerCase)).getOrElse(Last)
