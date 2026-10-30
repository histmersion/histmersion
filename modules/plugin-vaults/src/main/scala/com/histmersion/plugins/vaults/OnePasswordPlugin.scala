package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given

import scala.concurrent.duration.*

final class OnePasswordPlugin extends Plugin:
  type Config = OnePasswordConfig
  val info = PluginInfo("1password", Vaults.Version, Spi.Version)

  def register(c: OnePasswordConfig, ctx: PluginContext): Either[String, Unit] =
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
