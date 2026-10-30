package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PluginFile(file: String, sha256: String) derives KindlingsCodecAsObject, KindlingsSchema
object PluginFile:
  given Schema[PluginFile] = summon[KindlingsSchema[PluginFile]].schema
