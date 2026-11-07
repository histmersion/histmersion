package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*

final class OnePasswordPlugin extends Plugin:
  type Config = OnePasswordConfig
  val info = Plugin.Info(_root_.com.histmersion.plugin.Plugin.Info.Id("1password"), _root_.com.histmersion.plugin.Plugin.Info.Version(Vaults.Version), _root_.com.histmersion.plugin.Plugin.Info.SpiVersion(Spi.Version))

  def register(c: OnePasswordConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    given files: com.histmersion.plugin.LocalFiles = ctx.files
    given processes: com.histmersion.plugin.Processes = ctx.processes
    for
      exe <- Subprocess
        .locate(Subprocess.Tool(c.cli.value), ctx.env(Plugin.Context.EnvName("PATH")))
        .left
        .map(e => Plugin.RegisterResultPart1(e.value))
      token <- Vaults.optional(c.serviceAccountToken, ctx).left.map(e => Plugin.RegisterResultPart1(e.value))
      _ <- ctx.secretVaults
        .add(
          Registry.AddId(info.id.value),
          OnePasswordVault(
            Secret.Vault.NameResult(info.id.value),
            exe,
            Option(c.account).map(_.value.trim).filter(_.nonEmpty).map(OnePasswordConfig.Account(_)),
            token,
            Vaults.timeout(c.timeoutSeconds.value)
          )
        )
        .left
        .map(e => Plugin.RegisterResultPart1(e.value))
    yield ()
