package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One named style against the stored extract: the vector data layers it draws that the download left out
  * (`basemap.vector.drop`).
  */
final case class BasemapStyleState(id: String, name: String, missing: List[String] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object BasemapStyleState:
  given Schema[BasemapStyleState] = summon[KindlingsSchema[BasemapStyleState]].schema
