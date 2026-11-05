package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*

final class OnePasswordPlugin extends Plugin:
  type Config = OnePasswordConfig
  val info = Plugin.Info("1password", Vaults.Version, Spi.Version)

  def register(c: OnePasswordConfig, ctx: Plugin.Context): Either[String, Unit] =
    given files: com.histmersion.plugin.LocalFiles = ctx.files
    given processes: com.histmersion.plugin.Processes = ctx.processes
    for
      exe <- Subprocess.locate(c.cli, ctx.env("PATH"))
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
