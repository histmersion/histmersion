package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One effective setting of a plugin's block (the user's keys over the plugin's defaults). */
final case class PluginSetting(key: String, value: String) derives KindlingsCodecAsObject, KindlingsSchema
object PluginSetting:
  given Schema[PluginSetting] = summon[KindlingsSchema[PluginSetting]].schema
