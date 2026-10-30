package com.histmersion.server.acquire.fetchers

import com.histmersion.plugin.*

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.plugin.PluginConfig.given
import com.histmersion.api.VocabularyCodecs.given

/** One built-in plugin with settings of its own, `C`, decoded from its block by `C`'s reader. Its defaults resource
  * (`META-INF/histmersion/plugins/<id>.conf`, in the server) names every key.
  */
final class BuiltinWith[C](id: String, contribute: (C, PluginContext) => Either[String, Unit])(using ConfigReader[C])
    extends Plugin:
  type Config = C
  val info: PluginInfo = PluginInfo(id, BuiltinFetchers.Version, Spi.Version)
  def register(config: C, ctx: PluginContext): Either[String, Unit] = contribute(config, ctx)
