package com.histmersion
package plugins.ai

import com.histmersion.plugin.Problem

import com.histmersion.plugin.{AiProvider, Plugin, Spi}
import com.histmersion.plugin.Registry

/** One of Histmersion's own HTTP AI providers as a plugin: on by default (its defaults resource), switched off by
  * `histmersion.plugins.<id>.enabled = false`; it registers ONE provider under `name`, built from the host's
  * `agents.providers.<name>` settings.
  */
trait AiHttpPlugin(id: String, name: String) extends Plugin:
  type Config = HostAi
  val info: Plugin.Info =
    Plugin.Info(Plugin.Info.Id(id), Plugin.Info.Version(AiHttpPlugin.Version), Plugin.Info.SpiVersion(Spi.Version))

  /** This plugin's provider settings among the supplied ones. */
  def settings(providers: HostProviders): Option[ProviderSettings]

  /** The provider over `settings`. */
  def provider(settings: ProviderSettings): AiProvider

  def register(c: HostAi, ctx: Plugin.Context): Either[Problem, Unit] =
    settings(c.agents.providers)
      .toRight(Problem(s"no agents.providers.$name settings were supplied"))
      .flatMap(s => ctx.aiProviders.add(Registry.AddId(name), provider(s)))

object AiHttpPlugin:
  val Version = "0.1.0"
