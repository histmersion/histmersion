package com.histmersion.server.acquire.fetchers

import com.histmersion.plugin.*

import com.histmersion.plugin.PluginConfig.given
import com.histmersion.api.VocabularyCodecs.given

/** One built-in plugin: its id, and what it registers. Its defaults resource (`META-INF/histmersion/plugins/<id>.conf`,
  * in the server) sets `enabled = true`.
  */
final class Builtin(id: String, contribute: PluginContext => Either[String, Unit]) extends Plugin:
  type Config = BuiltinConfig
  val info: PluginInfo = PluginInfo(id, BuiltinFetchers.Version, Spi.Version)
  def register(config: BuiltinConfig, ctx: PluginContext): Either[String, Unit] = contribute(ctx)
