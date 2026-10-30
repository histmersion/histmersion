package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A named region: its name and `minLon,minLat,maxLon,maxLat`. */
final case class BasemapRegion(name: String, bbox: String) derives KindlingsCodecAsObject, KindlingsSchema

object BasemapRegion:
  given Schema[BasemapRegion] = summon[KindlingsSchema[BasemapRegion]].schema
