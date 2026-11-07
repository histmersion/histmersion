package com.histmersion.plugins.ai

import com.histmersion.plugin.{AiProvider, Plugin, Spi}

/** One of Histmersion's own HTTP AI providers as a plugin: on by default (its defaults resource), switched off by
  * `histmersion.plugins.<id>.enabled = false`; it registers ONE provider under `name`, built from the host's
  * `agents.providers.<name>` settings.
  */
trait AiHttpPlugin(id: String, name: String) extends Plugin:
  type Config = HostAi
  val info: Plugin.Info = Plugin.Info(_root_.com.histmersion.plugin.Plugin.Info.Id(id), _root_.com.histmersion.plugin.Plugin.Info.Version(AiHttpPlugin.Version), _root_.com.histmersion.plugin.Plugin.Info.SpiVersion(Spi.Version))

  /** This plugin's provider settings among the supplied ones. */
  def settings(providers: HostProviders): Option[ProviderSettings]

  /** The provider over `settings`. */
  def provider(settings: ProviderSettings): AiProvider

  def register(c: HostAi, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    settings(c.agents.providers)
      .toRight(Plugin.RegisterResultPart1(s"no agents.providers.$name settings were supplied"))
      .flatMap(s => ctx.aiProviders.add(_root_.com.histmersion.plugin.Registry.AddId(name), provider(s)).left.map(e => Plugin.RegisterResultPart1(e.value)))

object AiHttpPlugin:
  val Version = "0.1.0"
