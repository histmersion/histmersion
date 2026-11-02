package com.histmersion.api.basemap
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class BasemapRegion(name: String, bbox: String) derives KindlingsCodecAsObject, KindlingsSchema

object BasemapRegion:
  given Schema[BasemapRegion] = summon[KindlingsSchema[BasemapRegion]].schema
