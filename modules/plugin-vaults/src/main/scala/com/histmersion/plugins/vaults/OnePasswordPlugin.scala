package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*

final class OnePasswordPlugin extends Plugin:
  type Config = OnePasswordConfig
  val info = Plugin.Info(_root_.com.histmersion.plugin.Plugin.Info.Id("1password"), _root_.com.histmersion.plugin.Plugin.Info.Version(Vaults.Version), _root_.com.histmersion.plugin.Plugin.Info.SpiVersion(Spi.Version))

  def register(c: OnePasswordConfig, ctx: Plugin.Context): Either[com.histmersion.plugins.vaults.OnePasswordPlugin.Problem, Unit] =
    given files: com.histmersion.plugin.LocalFiles = ctx.files
    given processes: com.histmersion.plugin.Processes = ctx.processes
    for
      exe <- Subprocess.locate(c.cli, ctx.env(_root_.com.histmersion.plugin.Plugin.Context.EnvName("PATH")))
      token <- Vaults.optional(c.serviceAccountToken, ctx)
      _ <- ctx.secretVaults.add(
        info.id,
        OnePasswordVault(
          info.id,
          exe,
          Option(c.account).map(_.trim).filter(_.nonEmpty),
          token,
          Vaults.timeout(c.timeoutSeconds)
        )
      )
    yield ()

object OnePasswordPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
