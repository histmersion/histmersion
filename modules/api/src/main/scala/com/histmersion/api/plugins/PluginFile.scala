package com.histmersion.api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class PluginFile(file: PluginFile.File, sha256: com.histmersion.api.Sha256) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object PluginFile:
  type File = com.histmersion.api.automation.ProviderKind.Plugin.FilePath
  val File: _root_.com.histmersion.api.automation.ProviderKind.Plugin.FilePath.type = _root_.com.histmersion.api.automation.ProviderKind.Plugin.FilePath
