package com.histmersion.plugins.ai

import com.histmersion.plugin.{AiProvider, Plugin, PluginContext, PluginInfo, Spi}

/** One of Histmersion's own HTTP AI providers as a plugin: on by default (its defaults resource), switched off by
  * `histmersion.plugins.<id>.enabled = false`; it registers ONE provider under `name`, built from the host's
  * `agents.providers.<name>` settings.
  */
trait AiHttpPlugin(id: String, name: String) extends Plugin:
  type Config = HostAi
  val info: PluginInfo = PluginInfo(id, AiHttpPlugin.Version, Spi.Version)

  /** This plugin's provider settings among the supplied ones. */
  def settings(providers: HostProviders): Option[ProviderSettings]

  /** The provider over `settings`. */
  def provider(settings: ProviderSettings): AiProvider

  def register(c: HostAi, ctx: PluginContext): Either[String, Unit] =
    settings(c.agents.providers)
      .toRight(s"no agents.providers.$name settings were supplied")
      .flatMap(s => ctx.aiProviders.add(name, provider(s)))

object AiHttpPlugin:
  val Version = "1.0.0"
