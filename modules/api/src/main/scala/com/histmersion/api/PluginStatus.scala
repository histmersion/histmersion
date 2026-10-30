package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PluginStatus(
    id: String,
    version: String,
    spiVersion: Int,
    enabled: Boolean,
    state: String,
    provides: List[String] = Nil,
    settings: List[PluginSetting] = Nil,
    warnings: List[String] = Nil,
    error: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PluginStatus:
  given Schema[PluginStatus] = summon[KindlingsSchema[PluginStatus]].schema
