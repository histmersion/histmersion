package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One jar of a plugin set: its file name in the plugins directory, its SHA-256 and the plugins in it. */
final case class PluginJar(file: String, sha256: String, plugins: List[PluginDescription] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object PluginJar:
  given Schema[PluginJar] = summon[KindlingsSchema[PluginJar]].schema
