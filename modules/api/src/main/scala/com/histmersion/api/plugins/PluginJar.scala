package com.histmersion
package api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Sha256

final case class PluginJar(file: PluginJar.File, sha256: Sha256, plugins: List[PluginDescription] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object PluginJar:
  export com.histmersion.api.automation.ProviderKind.Plugin.FilePath as File
