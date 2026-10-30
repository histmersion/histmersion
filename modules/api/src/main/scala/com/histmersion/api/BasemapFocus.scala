package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class BasemapFocus(
    name: String,
    file: String,
    bytes: Long,
    bbox: String,
    minZoom: Int,
    maxZoom: Int,
    tiles: Long,
    fetchedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object BasemapFocus:
  given Schema[BasemapFocus] = summon[KindlingsSchema[BasemapFocus]].schema
