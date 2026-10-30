package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PluginDescription(
    id: String,
    version: String,
    spiVersion: Int,
    provides: List[String] = Nil,
    access: List[String] = Nil,
    error: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PluginDescription:
  given Schema[PluginDescription] = summon[KindlingsSchema[PluginDescription]].schema
