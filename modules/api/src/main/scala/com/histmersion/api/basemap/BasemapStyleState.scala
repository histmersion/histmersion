package com.histmersion.api.basemap
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class BasemapStyleState(id: String, name: String, missing: List[String] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object BasemapStyleState:
  given Schema[BasemapStyleState] = summon[KindlingsSchema[BasemapStyleState]].schema
