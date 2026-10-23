package com.histmersion.web.map

/** The engine the app runs on: the one place outside the adapter that names it. */
object Engines:
  def default: MapEngine = maplibre.MapLibreEngine
