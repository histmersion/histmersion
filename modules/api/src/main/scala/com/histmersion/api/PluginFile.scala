package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A file a plugin set was linked with besides its jars: the POM beside a plugin jar (`x-1.0.pom`), or a dependency jar
  * of the plugins directory's `lib/` (`lib/y-2.0.jar`); with its SHA-256.
  */
final case class PluginFile(file: String, sha256: String) derives KindlingsCodecAsObject, KindlingsSchema
object PluginFile:
  given Schema[PluginFile] = summon[KindlingsSchema[PluginFile]].schema
