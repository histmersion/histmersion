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
  def register(config: C, ctx: Plugin.Context): Either[com.histmersion.util.plugins.HostProvided.RegisterResultPart1, Unit] = contribute(config, ctx)

object HostProvided:
  /** The ids of the plugins the host supplies itself: the command-line AI runtimes. The relink tool keeps them from a
    * user's plugin without loading the runtimes.
    */
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContributeResultPart1 = ContributeResultPart1.T
  object ContributeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ids = Ids.T
  object Ids extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyId = ApplyId.T
  object ApplyId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyContributeResultPart1 = ApplyContributeResultPart1.T
  object ApplyContributeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Ids: List[com.histmersion.util.plugins.HostProvided.Ids] = List("ai-claude-code", "ai-codex", "ai-opencode")

  /** The version every host-supplied plugin reports. */
  val Release = "0.1.0"

  /** A host-supplied plugin with no settings of its own beyond the host's `enabled`. */
  def apply(id: com.histmersion.util.plugins.HostProvided.ApplyId, contribute: Plugin.Context => Either[com.histmersion.util.plugins.HostProvided.ApplyContributeResultPart1, Unit]): HostProvided[NoSettings] =
    new HostProvided[NoSettings](id, (_, ctx) => contribute(ctx))

  /** No settings of its own. */
  final case class NoSettings() derives ConfigReader
