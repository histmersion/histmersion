package com.histmersion
package plugins.vaults

import com.histmersion.plugin.Problem
import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import java.nio.file.{Files, Path}
import scala.concurrent.duration.*
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.LocalFiles
import com.histmersion.plugin.Processes

final class KeePassXcPlugin extends Plugin:
  type Config = KeePassXcConfig
  val info =
    Plugin.Info(Plugin.Info.Id("keepassxc"), Plugin.Info.Version(Vaults.Version), Plugin.Info.SpiVersion(Spi.Version))

  def register(c: KeePassXcConfig, ctx: Plugin.Context): Either[Problem, Unit] =
    given files: LocalFiles = ctx.files
    given processes: Processes = ctx.processes
    def failed(why: String) = Problem(why)
    val home = ctx.env(EnvVar.Name("HOME"))
    for
      db <- Option(c.database.value)
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(d => Vaults.expand(Vaults.Path(d), home))
        .toRight(failed("set database = \"<path to the .kdbx file>\""))
      _ <- Either.cond(files.isFile(Path.of(db.value)), (), failed(s"database ${db.value} does not exist"))
      key = Option(c.keyFile.value).map(_.trim).filter(_.nonEmpty).map(k => Vaults.expand(Vaults.Path(k), home))
      _ <- key.fold[Either[Problem, Unit]](Right(()))(k =>
        Either.cond(files.isFile(Path.of(k.value)), (), failed(s"key-file ${k.value} does not exist"))
      )
      exe <- Subprocess
        .locate(Subprocess.Tool(c.cli.value), ctx.env(EnvVar.Name("PATH")))
        .left
        .map(e => failed(e.value))
      yks <- YubiKey
        .parseAll(c.yubikey.fold[List[YubiKey.Setting.Entries]](Nil)(_.entries))
        .left
        .map(e => failed(e.value))
      det <- if yks.isEmpty then Right(None) else detector(c.ykman, ctx)
      pw <- Vaults.optional(c.password, ctx).left.map(e => failed(e.value))
      _ <- ctx.secretVaults
        .add(
          Registry.AddId(info.id.value),
          KeePassXcVault(
            Secret.Vault.Name(info.id.value),
            exe,
            db,
            key,
            pw,
            Vaults.Field(c.attribute.value),
            Vaults.timeout(c.timeoutSeconds.value),
            yks,
            det,
            Vaults.timeout(c.yubikeyTimeoutSeconds.value),
            ctx.log
          )
        )
        .left
        .map(e => failed(e.value))
    yield ()

  /** `ykman`, when it can be found: a bare name missing from the PATH only means the keys are tried in order; a path
    * that was given and is wrong is an error.
    */
  private def detector(
      ykman: KeePassXcConfig.Ykman,
      ctx: Plugin.Context
  ): Either[Problem, Option[Vaults.Path]] =
    given files: LocalFiles = ctx.files
    val t = Option(ykman).map((_).value.trim).getOrElse("")
    if t.isEmpty then
      ctx.log.info(
        Plugin.Log.Line("YubiKey detection is off (ykman = \"\"): the configured YubiKeys are tried in order")
      )
      Right(None)
    else
      Subprocess.locate(Subprocess.Tool(t), ctx.env(EnvVar.Name("PATH"))) match
        case Right(p)                    => Right(Some(p))
        case Left(_) if !t.contains('/') =>
          ctx.log.info(
            Plugin.Log.Line(
              "the configured YubiKeys are tried in order, the one that opened the database last first " +
                "(optional: with ykman on the PATH, only the connected ones are tried)"
            )
          )
          Right(None)
        case Left(e) => Left(Problem(s"ykman: ${e.value}"))
