package com.histmersion.api.instance

import com.histmersion.api.catalogue.Place
import com.histmersion.api.given
enum MapStartMode(val wire: String, val label: String):
  case Last extends MapStartMode("last", "Where I last left it")
  case Place extends MapStartMode("place", "At a place I choose")

object MapStartMode:
  def parse(s: String): MapStartMode = values.find(_.wire == Option(s).fold("")(_.trim.toLowerCase)).getOrElse(Last)
