package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Where the map opens (`ui.map-start`): `mode` "last" (where the browser left it; here the first time) or "place"
  * (always here).
  */
final case class UiMapStartSettings(mode: String, lat: Double, lon: Double, zoom: Double, label: String)
    derives ConfigReader
