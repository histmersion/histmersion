package com.histmersion
package cli

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.config.AppConfig
import com.histmersion.config.edit.{ConfigFileState, FileVersion, SettingError, SettingsFile, WriteOutcome}
import org.ekrich.config.{Config, ConfigFactory}

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, StandardCopyOption}

/** The settings file at `path` on this machine's disk, as `histmersion config` changes it without a server. */
final class LocalSettingsFile(val path: Path) extends SettingsFile:

  def shown: SettingsFile.Shown = SettingsFile.Shown(path.toString)

  /** The file a symbolic link at `path` points to (the new text is written beside it), else `path`. */
  private def target: Path = if Files.isSymbolicLink(path) then path.toRealPath() else path

  def read(): Either[SettingError, ConfigFileState] =
    try
      if !Files.exists(path) then
        Right(ConfigFileState(None, FileVersion.Missing, ConfigFactory.empty, AppConfig.writtenIn(path)))
      else if !Files.isRegularFile(path) then
        Left(SettingError.NoFile(Problem.Unclassified(Legacy.Text(s"$path is not a file"))))
      else
        val text = ConfigFileState.Text(Files.readString(path, StandardCharsets.UTF_8))
        parse(path).map(c => ConfigFileState(Some(text), FileVersion.of(Some(text)), c, AppConfig.writtenIn(path)))
    catch case e: IOException => Left(SettingError.Io(Problem.Unclassified(Legacy.Text(e.getMessage))))

  def replace(text: ConfigFileState.Text, existed: SettingsFile.Existed)(
      check: SettingsFile.Staged => Either[SettingError, Unit]
  ): Either[SettingError, Option[WriteOutcome.Backup]] =
    val dir = target.toAbsolutePath.getParent
    try
      Files.createDirectories(dir)
      val temp = Files.createTempFile(dir, s".${path.getFileName}.", ".tmp")
      try
        Files.writeString(temp, text.value, StandardCharsets.UTF_8)
        val staged = SettingsFile.Staged(
          parse(temp),
          AppConfig.writtenIn(temp),
          env =>
            AppConfig
              .loadWithUserFile(env, Some(temp))
              .left
              .map(m => Problem.Unclassified(Legacy.Text(m.sentence.replace(temp.toString, path.toString))))
        )
        for
          _ <- check(staged)
          bak <- backup(existed.value)
          _ <- move(temp, existed.value)
        yield bak
      finally
        val _ = Files.deleteIfExists(temp)
    catch case e: IOException => Left(SettingError.Io(Problem.Unclassified(Legacy.Text(e.getMessage))))

  private def backup(existed: Boolean): Either[SettingError, Option[WriteOutcome.Backup]] =
    if !existed then Right(None)
    else
      val bak = path.resolveSibling(s"${path.getFileName}.bak")
      Files.copy(path, bak, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
      Right(Some(WriteOutcome.Backup(bak.toString)))

  /** The new file in place in one step, with the old one's permissions (a file holding a reference to a password may be
    * readable by its owner alone).
    */
  private def move(temp: Path, existed: Boolean): Either[SettingError, Unit] =
    if existed then
      Option(Files.getFileAttributeView(path, classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS)).foreach { v =>
        Files.setPosixFilePermissions(temp, v.readAttributes.permissions)
      }
    try Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    catch case _: AtomicMoveNotSupportedException => Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
    Right(())

  private def parse(file: Path): Either[SettingError, Config] =
    AppConfig
      .parseUserFile(file)
      .left
      .map(m =>
        SettingError.Unreadable(Problem.Unclassified(Legacy.Text(m.sentence.replace(file.toString, path.toString))))
      )
      .flatMap(_.toRight(SettingError.Unreadable(Problem.Unclassified(Legacy.Text(s"$path: no such file")))))
