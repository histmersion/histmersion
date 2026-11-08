package com.histmersion
package plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.LocalFiles
import com.histmersion.plugin.Processes

final class OnePasswordPlugin extends Plugin:
  type Config = OnePasswordConfig
  val info =
    Plugin.Info(Plugin.Info.Id("1password"), Plugin.Info.Version(Vaults.Version), Plugin.Info.SpiVersion(Spi.Version))

  def register(c: OnePasswordConfig, ctx: Plugin.Context): Either[Problem, Unit] =
    given files: LocalFiles = ctx.files
    given processes: Processes = ctx.processes
    for
      exe <- Subprocess
        .locate(Subprocess.Tool(c.cli.value), ctx.env(EnvVar.Name("PATH")))
        .left
        .map(e => Problem(e.value))
      token <- Vaults.optional(c.serviceAccountToken, ctx).left.map(e => Problem(e.value))
      _ <- ctx.secretVaults
        .add(
          Registry.AddId(info.id.value),
          OnePasswordVault(
            Secret.Vault.Name(info.id.value),
            exe,
            Option(c.account).map(_.value.trim).filter(_.nonEmpty).map(OnePasswordConfig.Account(_)),
            token,
            Vaults.timeout(c.timeoutSeconds.value)
          )
        )
        .left
        .map(e => Problem(e.value))
    yield ()
