package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import java.nio.file.{Files, Path}
import scala.concurrent.duration.*

final class KeePassXcPlugin extends Plugin:
  type Config = KeePassXcConfig
  val info = Plugin.Info("keepassxc", Vaults.Version, Spi.Version)

  def register(c: KeePassXcConfig, ctx: Plugin.Context): Either[com.histmersion.plugins.vaults.KeePassXcPlugin.RegisterResultPart1, Unit] =
    given files: com.histmersion.plugin.LocalFiles = ctx.files
    given processes: com.histmersion.plugin.Processes = ctx.processes
    val home = ctx.env("HOME")
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
  private def detector(ykman: com.histmersion.plugins.vaults.KeePassXcPlugin.DetectorYkman, ctx: Plugin.Context): Either[com.histmersion.plugins.vaults.KeePassXcPlugin.DetectorResultPart1, Option[com.histmersion.plugins.vaults.KeePassXcPlugin.DetectorResultPart2]] =
    given files: com.histmersion.plugin.LocalFiles = ctx.files
    val t = Option(ykman).map(_.trim).getOrElse("")
    if t.isEmpty then
      ctx.log.info("YubiKey detection is off (ykman = \"\"): the configured YubiKeys are tried in order")
      Right(None)
    else
      Subprocess.locate(t, ctx.env("PATH")) match
        case Right(p)                    => Right(Some(p))
        case Left(_) if !t.contains('/') =>
          ctx.log.info(
            "the configured YubiKeys are tried in order, the one that opened the database last first " +
              "(optional: with ykman on the PATH, only the connected ones are tried)"
          )
          Right(None)
        case Left(e) => Left(s"ykman: $e")

object KeePassXcPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DetectorResultPart1 = DetectorResultPart1.T
  object DetectorResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DetectorResultPart2 = DetectorResultPart2.T
  object DetectorResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DetectorYkman = DetectorYkman.T
  object DetectorYkman extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
