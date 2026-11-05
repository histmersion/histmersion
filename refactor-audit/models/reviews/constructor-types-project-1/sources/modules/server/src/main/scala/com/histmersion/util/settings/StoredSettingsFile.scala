package com.histmersion.util.settings

import com.histmersion.config.AppConfig
import com.histmersion.config.edit.{ConfigFileState, FileVersion, SettingError, SettingsFile}
import com.histmersion.util.storage.{Store, TempName}
import org.ekrich.config.{Config, ConfigFactory}

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.Using

/** The settings file `key` of `store` (the settings file's directory), named `shown` in messages: the file the server
  * read its settings from. Its text and its includes are read as a load reads them, which needs the file on disk; a
  * store in memory has none to give, and every read and change of it is refused.
  */
final class StoredSettingsFile(store: Store, key: Store.Key, val shown: StoredSettingsFile.Shown) extends SettingsFile:

  def read(): Either[SettingError, ConfigFileState] =
    if !store.exists(key) then
      Right(
        ConfigFileState(
          None,
          FileVersion.Missing,
          ConfigFactory.empty,
          store.onDisk(key).fold(_ => _ => false, AppConfig.writtenIn(_))
        )
      )
    else
      store.get(key) match
        case None       => Left(SettingError.NoFile(s"$shown is not a file"))
        case Some(blob) =>
          try
            val text = blob.text()
            for
              file <- store.onDisk(key).left.map(io)
              parsed <- parsed(AppConfig.parseUserFile(file), file.toString)
            yield ConfigFileState(Some(text), FileVersion.of(Some(text)), parsed, AppConfig.writtenIn(file))
          catch case e: IOException => Left(SettingError.Io(e.getMessage))

  def replace(text: com.histmersion.util.settings.StoredSettingsFile.ReplaceText, existed: com.histmersion.util.settings.StoredSettingsFile.ReplaceExisted)(
      check: SettingsFile.Staged => Either[SettingError, Unit]
  ): Either[SettingError, Option[com.histmersion.util.settings.StoredSettingsFile.ReplaceResultPart2]] =
    try
      store.reserve(key, TempName.Unique, throughLink = true).left.map(io).flatMap { reservation =>
        Using.resource(reservation) { r =>
          Using.resource(r.output())(_.write(text.getBytes(UTF_8)))
          for
            temp <- r.onDisk.left.map(io)
            _ <- check(
              SettingsFile.Staged(
                parsed(AppConfig.parseUserFile(temp), temp.toString),
                AppConfig.writtenIn(temp),
                env => AppConfig.loadWithUserFile(env, Some(temp)).left.map(_.replace(temp.toString, shown))
              )
            )
            bak <- backup(existed)
            _ <- r.commit(keepPermissions = existed).left.map(io)
          yield bak
        }
      }
    catch case e: IOException => Left(SettingError.Io(e.getMessage))

  private def backup(existed: com.histmersion.util.settings.StoredSettingsFile.BackupExisted): Either[SettingError, Option[com.histmersion.util.settings.StoredSettingsFile.BackupResultPart2]] =
    if !existed then Right(None)
    else store.copy(key, key.withSuffix(".bak"), keepAttributes = true).left.map(io).map(_ => Some(s"$shown.bak"))

  /** A parse of the file at `where`, its messages naming the settings file. */
  private def parsed(parse: Either[com.histmersion.util.settings.StoredSettingsFile.ParsedParsePart1, Option[Config]], where: com.histmersion.util.settings.StoredSettingsFile.ParsedWhere): Either[SettingError, Config] =
    parse.left
      .map(m => SettingError.Unreadable(m.replace(where, shown)))
      .flatMap(_.toRight(SettingError.Unreadable(s"$shown: no such file")))

  private def io(e: Store.Error): SettingError = SettingError.Io(e.message)

object StoredSettingsFile:
  type Shown = Shown.T
  object Shown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ReplaceResultPart2 = ReplaceResultPart2.T
  object ReplaceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceText = ReplaceText.T
  object ReplaceText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceExisted = ReplaceExisted.T
  object ReplaceExisted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackupResultPart2 = BackupResultPart2.T
  object BackupResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackupExisted = BackupExisted.T
  object BackupExisted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParsedParsePart1 = ParsedParsePart1.T
  object ParsedParsePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParsedWhere = ParsedWhere.T
  object ParsedWhere extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
