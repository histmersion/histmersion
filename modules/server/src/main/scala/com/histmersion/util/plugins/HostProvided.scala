package com.histmersion.util.plugins

import com.histmersion.plugin.Plugin
import com.histmersion.pluginhost.PluginHost
import hearth.kindlings.sconfigderivation.ConfigReader

/** A first-party plugin the host still supplies itself, with settings `C` decoded from its block by `C`'s reader: the
  * command-line AI runtimes, which read the host's own settings at each use. Its defaults resource
  * (`META-INF/histmersion/plugins/<id>.conf`, in the server) names every key and sets `enabled`.
  */
final class HostProvided[C](id: HostProvided.Id, contribute: (C, Plugin.Context) => Either[HostProvided.ContributeResultPart1, Unit])(using ConfigReader[C])
    extends Plugin:
  type Config = C
  val info: Plugin.Info = Plugin.Info(id, HostProvided.Release, PluginHost.SpiGeneration)
  def register(config: C, ctx: Plugin.Context): Either[com.histmersion.util.plugins.HostProvided.Problem, Unit] = contribute(config, ctx)

object HostProvided:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The ids of the plugins the host supplies itself: the command-line AI runtimes. The relink tool keeps them from a
    * user's plugin without loading the runtimes.
    */
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContributeResultPart1 = com.histmersion.util.plugins.HostProvided.Problem
  val ContributeResultPart1: _root_.com.histmersion.util.plugins.HostProvided.Problem.type = _root_.com.histmersion.util.plugins.HostProvided.Problem

  type Ids = IdsValue
  type IdsValue = IdsValue.T
  object IdsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Ids: List[com.histmersion.util.plugins.HostProvided.Ids] = List("ai-claude-code", "ai-codex", "ai-opencode")

  /** The version every host-supplied plugin reports. */
  val Release = "0.1.0"

  /** A host-supplied plugin with no settings of its own beyond the host's `enabled`. */
  def apply(id: com.histmersion.util.plugins.Plugin.Id, contribute: Plugin.Context => Either[com.histmersion.util.plugins.HostProvided.Problem, Unit]): HostProvided[NoSettings] =
    new HostProvided[NoSettings](id, (_, ctx) => contribute(ctx))

  /** No settings of its own. */
  final case class NoSettings() derives ConfigReader
