package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class BasemapOnDemand(
    enabled: Boolean,
    maxZoom: Int,
    dir: String,
    tiles: Long,
    bytes: Long,
    maxBytes: Long,
    offlineUntil: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object BasemapOnDemand:
  given Schema[BasemapOnDemand] = summon[KindlingsSchema[BasemapOnDemand]].schema
