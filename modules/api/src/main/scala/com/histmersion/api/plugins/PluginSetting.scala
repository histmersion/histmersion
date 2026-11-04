package com.histmersion.api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class PluginSetting(key: String, value: String) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
