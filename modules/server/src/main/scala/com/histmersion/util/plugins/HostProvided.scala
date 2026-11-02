package com.histmersion.util.plugins

import com.histmersion.plugin.{Plugin, PluginContext, PluginInfo}
import com.histmersion.pluginhost.PluginHost
import hearth.kindlings.sconfigderivation.ConfigReader

/** A first-party plugin the host still supplies itself, with settings `C` decoded from its block by `C`'s reader: the
  * command-line AI runtimes, which read the host's own settings at each use. Its defaults resource
  * (`META-INF/histmersion/plugins/<id>.conf`, in the server) names every key and sets `enabled`.
  */
final class HostProvided[C](id: String, contribute: (C, PluginContext) => Either[String, Unit])(using ConfigReader[C])
    extends Plugin:
  type Config = C
  val info: PluginInfo = PluginInfo(id, HostProvided.Release, PluginHost.SpiGeneration)
  def register(config: C, ctx: PluginContext): Either[String, Unit] = contribute(config, ctx)

object HostProvided:
  /** The ids of the plugins the host supplies itself: the command-line AI runtimes. The relink tool keeps them from a
    * user's plugin without loading the runtimes.
    */
  val Ids: List[String] = List("ai-claude-code", "ai-codex", "ai-opencode")

  /** The version every host-supplied plugin reports. */
  val Release = "0.1.0"

  /** A host-supplied plugin with no settings of its own beyond the host's `enabled`. */
  def apply(id: String, contribute: PluginContext => Either[String, Unit]): HostProvided[NoSettings] =
    new HostProvided[NoSettings](id, (_, ctx) => contribute(ctx))

  /** No settings of its own. */
  final case class NoSettings() derives ConfigReader
