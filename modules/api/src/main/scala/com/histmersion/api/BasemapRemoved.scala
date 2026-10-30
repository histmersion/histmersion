package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `DELETE /api/basemap?layer=`: what was removed. */
final case class BasemapRemoved(removed: List[String], bytes: Long) derives KindlingsCodecAsObject, KindlingsSchema

object BasemapRemoved:
  given Schema[BasemapRemoved] = summon[KindlingsSchema[BasemapRemoved]].schema
