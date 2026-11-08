package com.histmersion
package plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.LocalFiles
import com.histmersion.plugin.Processes

final class BitwardenPlugin extends Plugin:
  type Config = BitwardenConfig
  val info = Plugin.Info(Plugin.Info.Id("bitwarden"), Plugin.Info.Version(Vaults.Version), Plugin.Info.SpiVersion(Spi.Version))

  def register(c: BitwardenConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    given files: LocalFiles = ctx.files
    given processes: Processes = ctx.processes
    for
      exe <- Subprocess.locate(Subprocess.Tool(c.cli.value), ctx.env(Plugin.Context.EnvName("PATH"))).left.map(e => Plugin.RegisterResultPart1(e.value))
      session <- Vaults.optional(c.session, ctx).left.map(e => Plugin.RegisterResultPart1(e.value))
      _ <- ctx.secretVaults
        .add(
          Registry.AddId(info.id.value),
          BitwardenVault(Secret.Vault.NameResult(info.id.value), exe, session, Vaults.Field(c.field.value), Vaults.timeout(c.timeoutSeconds.value))
        )
        .left
        .map(e => Plugin.RegisterResultPart1(e.value))
    yield ()
