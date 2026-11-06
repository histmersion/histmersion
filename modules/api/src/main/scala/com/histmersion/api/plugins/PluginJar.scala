package com.histmersion.api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class PluginJar(file: PluginJar.File, sha256: com.histmersion.api.Sha256, plugins: List[PluginDescription] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

