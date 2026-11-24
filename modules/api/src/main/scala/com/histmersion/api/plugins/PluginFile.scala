package com.histmersion
package api.plugins
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Sha256
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class PluginFile(file: PluginFile.File, sha256: Sha256) derives JsonCodec, KindlingsSchema, api.json.Schema

object PluginFile:
  export api.automation.ProviderKind.Plugin.FilePath as File
