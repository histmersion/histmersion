package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

/** Where the map opens (`ui.map-start`, [[MapStartMode]]): where this browser left it, or always at the centre and zoom
  * given; `label` names the place for a person.
  */
final case class MapStartConfig(
    mode: String = MapStartMode.Last.wire,
    lat: Double = 37.0,
    lon: Double = 30.0,
    zoom: Double = 5,
    label: String = ""
) derives KindlingsCodecAsObject,
      KindlingsSchema
