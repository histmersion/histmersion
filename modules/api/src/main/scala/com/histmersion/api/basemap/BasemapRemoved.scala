package com.histmersion.api.basemap
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class BasemapRemoved(removed: List[String], bytes: Long) derives KindlingsCodecAsObject, KindlingsSchema

object BasemapRemoved:
  given Schema[BasemapRemoved] = summon[KindlingsSchema[BasemapRemoved]].schema
