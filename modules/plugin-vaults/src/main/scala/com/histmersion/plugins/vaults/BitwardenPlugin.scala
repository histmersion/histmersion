package com.histmersion
package plugins.vaults
import com.histmersion.util.problem.{Problem, Problems}

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.LocalFiles
import com.histmersion.plugin.Processes
import com.histmersion.lint.{LintRule, tolerated}

final class BitwardenPlugin extends Plugin:
  type Config = BitwardenConfig
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val info =
    Plugin.Info(Plugin.Info.Id("bitwarden"), Plugin.Info.Version(Vaults.Version), Plugin.Info.SpiVersion(Spi.Version))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def register(c: BitwardenConfig, ctx: Plugin.Context): Either[Problems, Unit] =
    given files: LocalFiles = ctx.files
    given processes: Processes = ctx.processes
    for
      exe <- Subprocess
        .locate(Subprocess.Tool(c.cli.value), ctx.env(EnvVar.Name("PATH")))
      session <- Vaults.optional(c.session, ctx)
      _ <- ctx.secretVaults
        .add(
          Registry.AddId(info.id.value),
          BitwardenVault(
            Secret.Vault.Name(info.id.value),
            exe,
            session,
            Vaults.Field(c.field.value),
            Vaults.timeout(c.timeoutSeconds.value)
          )
        )
    yield ()
