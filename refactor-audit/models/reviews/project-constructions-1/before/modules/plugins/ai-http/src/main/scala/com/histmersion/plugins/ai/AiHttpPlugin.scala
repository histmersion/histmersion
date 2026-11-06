package com.histmersion.plugins.ai

import com.histmersion.plugin.{AiProvider, Plugin, Spi}

/** One of Histmersion's own HTTP AI providers as a plugin: on by default (its defaults resource), switched off by
  * `histmersion.plugins.<id>.enabled = false`; it registers ONE provider under `name`, built from the host's
  * `agents.providers.<name>` settings.
  */
trait AiHttpPlugin(id: String, name: String) extends Plugin:
  type Config = HostAi
  val info: Plugin.Info = Plugin.Info(id, AiHttpPlugin.Version, Spi.Version)

  /** This plugin's provider settings among the supplied ones. */
  def settings(providers: HostProviders): Option[ProviderSettings]

  /** The provider over `settings`. */
  def provider(settings: ProviderSettings): AiProvider

  def register(c: HostAi, ctx: Plugin.Context): Either[com.histmersion.plugins.ai.AiHttpPlugin.RegisterResultPart1, Unit] =
    settings(c.agents.providers)
      .toRight(s"no agents.providers.$name settings were supplied")
      .flatMap(s => ctx.aiProviders.add(name, provider(s)))

object AiHttpPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Version = "0.1.0"
