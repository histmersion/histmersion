package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import java.nio.file.{Files, Path}
import scala.concurrent.duration.*

final class KeePassXcPlugin extends Plugin:
  type Config = KeePassXcConfig
  val info = Plugin.Info(_root_.com.histmersion.plugin.Plugin.Info.Id("keepassxc"), _root_.com.histmersion.plugin.Plugin.Info.Version(Vaults.Version), _root_.com.histmersion.plugin.Plugin.Info.SpiVersion(Spi.Version))

  def register(c: KeePassXcConfig, ctx: Plugin.Context): Either[com.histmersion.plugins.vaults.KeePassXcPlugin.Problem, Unit] =
    given files: com.histmersion.plugin.LocalFiles = ctx.files
    given processes: com.histmersion.plugin.Processes = ctx.processes
    val home = ctx.env(_root_.com.histmersion.plugin.Plugin.Context.EnvName("HOME"))
    for
      db <- Option(c.database)
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(Vaults.expand(_, home))
        .toRight("set database = \"<path to the .kdbx file>\"")
      _ <- Either.cond(files.isFile(Path.of(db)), (), s"database $db does not exist")
      key = Option(c.keyFile).map(_.trim).filter(_.nonEmpty).map(Vaults.expand(_, home))
      _ <- key.fold(Right(()))(k => Either.cond(files.isFile(Path.of(k)), (), s"key-file $k does not exist"))
      exe <- Subprocess.locate(c.cli, ctx.env("PATH"))
      yks <- YubiKey.parseAll(c.yubikey.fold(Nil)(_.entries))
      det <- if yks.isEmpty then Right(None) else detector(c.ykman, ctx)
      pw <- Vaults.optional(c.password, ctx)
      _ <- ctx.secretVaults.add(
        info.id,
        KeePassXcVault(
          info.id,
          exe,
          db,
          key,
          pw,
          c.attribute,
          Vaults.timeout(c.timeoutSeconds),
          yks,
          det,
          Vaults.timeout(c.yubikeyTimeoutSeconds),
          ctx.log
        )
      )
    yield ()

  /** `ykman`, when it can be found: a bare name missing from the PATH only means the keys are tried in order; a path
    * that was given and is wrong is an error.
    */
  private def detector(ykman: com.histmersion.plugins.vaults.KeePassXcConfig.Ykman, ctx: Plugin.Context): Either[com.histmersion.plugins.vaults.KeePassXcPlugin.Problem, Option[com.histmersion.plugins.vaults.KeePassXcPlugin.Detected]] =
    given files: com.histmersion.plugin.LocalFiles = ctx.files
    val t = Option(ykman).map((_).value.trim).getOrElse("")
    if t.isEmpty then
      ctx.log.info(_root_.com.histmersion.plugin.Plugin.Log.InfoMessage("YubiKey detection is off (ykman = \"\"): the configured YubiKeys are tried in order"))
      Right(None)
    else
      Subprocess.locate(_root_.com.histmersion.plugins.vaults.Subprocess.Tool(t), ctx.env(_root_.com.histmersion.plugin.Plugin.Context.EnvName("PATH"))) match
        case Right(p)                    => Right(Some(p))
        case Left(_) if !t.contains('/') =>
          ctx.log.info(
            _root_.com.histmersion.plugin.Plugin.Log.InfoMessage("the configured YubiKeys are tried in order, the one that opened the database last first " +
              "(optional: with ykman on the PATH, only the connected ones are tried)")
          )
          Right(None)
        case Left(e) => Left(s"ykman: $e")

object KeePassXcPlugin:
  type Detected = Detected.T
  object Detected extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
