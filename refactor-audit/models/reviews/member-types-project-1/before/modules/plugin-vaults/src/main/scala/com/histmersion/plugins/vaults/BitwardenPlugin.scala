package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*

final class BitwardenPlugin extends Plugin:
  type Config = BitwardenConfig
  val info = Plugin.Info("bitwarden", Vaults.Version, Spi.Version)

  def register(c: BitwardenConfig, ctx: Plugin.Context): Either[String, Unit] =
    given files: com.histmersion.plugin.LocalFiles = ctx.files
    given processes: com.histmersion.plugin.Processes = ctx.processes
    for
      exe <- Subprocess.locate(c.cli, ctx.env("PATH"))
      session <- Vaults.optional(c.session, ctx)
      _ <- ctx.secretVaults.add(
        info.id,
        BitwardenVault(info.id, exe, session, c.field, Vaults.timeout(c.timeoutSeconds))
      )
    yield ()
