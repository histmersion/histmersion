package com.histmersion
package util.plugins
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.Plugin
import com.histmersion.pluginhost.PluginHost
import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.lint.{tolerated, LintRule}

/** A first-party plugin the host still supplies itself, with settings `C` decoded from its block by `C`'s reader: the
  * command-line AI runtimes, which read the host's own settings at each use. Its defaults resource
  * (`META-INF/histmersion/plugins/<id>.conf`, in the server) names every key and sets `enabled`.
  */
final class HostProvided[C](
    id: Plugin.Info.Id,
    contribute: (C, Plugin.Context) => Either[Problem, Unit]
)(using ConfigReader[C])
    extends Plugin:
  type Config = C
  val info: Plugin.Info = Plugin.Info(id, HostProvided.Release, PluginHost.SpiGeneration)
  def register(config: C, ctx: Plugin.Context): Either[Problem, Unit] = contribute(config, ctx)

object HostProvided:
  /** The ids of the plugins the host supplies itself: the command-line AI runtimes. The relink tool keeps them from a
    * user's plugin without loading the runtimes.
    */
  val Ids: List[util.plugins.Plugin.Id] =
    List(
      util.plugins.Plugin.Id("ai-claude-code"),
      util.plugins.Plugin.Id("ai-codex"),
      util.plugins.Plugin.Id("ai-opencode")
    )

  /** The version every host-supplied plugin reports. */
  val Release: Plugin.Info.Version = Plugin.Info.Version("0.1.0")

  /** A host-supplied plugin with no settings of its own beyond the host's `enabled`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(
      id: util.plugins.Plugin.Id,
      contribute: Plugin.Context => Either[Problem, Unit]
  ): HostProvided[NoSettings] =
    new HostProvided[NoSettings](Plugin.Info.Id(id.value), (_, ctx) => contribute(ctx))

  /** No settings of its own. */
  final case class NoSettings() derives ConfigReader
