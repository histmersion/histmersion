package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*

final class BitwardenPlugin extends Plugin:
  type Config = BitwardenConfig
  val info = Plugin.Info(_root_.com.histmersion.plugin.Plugin.Info.Id("bitwarden"), _root_.com.histmersion.plugin.Plugin.Info.Version(Vaults.Version), _root_.com.histmersion.plugin.Plugin.Info.SpiVersion(Spi.Version))

  def register(c: BitwardenConfig, ctx: Plugin.Context): Either[com.histmersion.plugins.vaults.BitwardenPlugin.Problem, Unit] =
    given files: com.histmersion.plugin.LocalFiles = ctx.files
    given processes: com.histmersion.plugin.Processes = ctx.processes
    for
      exe <- Subprocess.locate(c.cli, ctx.env(_root_.com.histmersion.plugin.Plugin.Context.EnvName("PATH")))
      session <- Vaults.optional(c.session, ctx)
      _ <- ctx.secretVaults.add(
        info.id,
        BitwardenVault(info.id, exe, session, c.field, Vaults.timeout(c.timeoutSeconds))
      )
    yield ()

object BitwardenPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
