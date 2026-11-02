package com.histmersion.api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given

final case class MapStartConfig(
    mode: String = MapStartMode.Last.wire,
    lat: Double = 37.0,
    lon: Double = 30.0,
    zoom: Double = 5,
    label: String = ""
) derives KindlingsCodecAsObject,
      KindlingsSchema
