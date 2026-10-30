package com.histmersion.api

/** Where the map opens: where this browser last left it, or always at the configured place. */
enum MapStartMode(val wire: String, val label: String):
  case Last extends MapStartMode("last", "Where I last left it")
  case Place extends MapStartMode("place", "At a place I choose")

object MapStartMode:
  /** A configured value; anything unknown is [[Last]], the default. */
  def parse(s: String): MapStartMode = values.find(_.wire == Option(s).fold("")(_.trim.toLowerCase)).getOrElse(Last)
