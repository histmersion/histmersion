package com.histmersion
package util.settings

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.config.AppConfig
import com.histmersion.config.edit.{ConfigFileState, FileVersion, SettingError, SettingsFile, WriteOutcome}
import com.histmersion.util.storage.{Store, TempName}
import org.ekrich.config.{Config, ConfigFactory}

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.Using

/** The settings file `key` of `store` (the settings file's directory), named `shown` in messages: the file the server
  * read its settings from. Its text and its includes are read as a load reads them, which needs the file on disk; a
  * store in memory has none to give, and every read and change of it is refused.
  */
final class StoredSettingsFile(store: Store, key: Store.Key, val shown: SettingsFile.Shown) extends SettingsFile:

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
        case None       => Left(SettingError.NoFile(Problem.Unclassified(Legacy.Text(s"$shown is not a file"))))
        case Some(blob) =>
          try
            val text = ConfigFileState.Text(blob.text().value)
            for
              file <- store.onDisk(key).left.map(io)
              parsed <- parsed(AppConfig.parseUserFile(file), file.toString)
            yield ConfigFileState(Some(text), FileVersion.of(Some(text)), parsed, AppConfig.writtenIn(file))
          catch case e: IOException => Left(SettingError.Io(Problem.Unclassified(Legacy.Text(e.getMessage))))

  def replace(text: ConfigFileState.Text, existed: SettingsFile.Existed)(
      check: SettingsFile.Staged => Either[SettingError, Unit]
  ): Either[SettingError, Option[WriteOutcome.Backup]] =
    try
      store.reserve(key, TempName.Unique, throughLink = Store.ThroughLink(true)).left.map(io).flatMap { reservation =>
        Using.resource(reservation) { r =>
          Using.resource(r.output())(_.write(text.value.getBytes(UTF_8)))
          for
            temp <- r.onDisk.left.map(io)
            _ <- check(
              SettingsFile.Staged(
                parsed(AppConfig.parseUserFile(temp), temp.toString),
                AppConfig.writtenIn(temp),
                env =>
                  AppConfig
                    .loadWithUserFile(env, Some(temp))
                    .left
                    .map(
                      _.toNonEmpty
                        .map(p => Problem.Unclassified(Legacy.Text(p.sentence.replace(temp.toString, shown.value))))
                    )
              )
            )
            bak <- backup(existed)
            _ <- r.commit(keepPermissions = Store.KeepPermissions(existed.value)).left.map(io)
          yield bak
        }
      }
    catch case e: IOException => Left(SettingError.Io(Problem.Unclassified(Legacy.Text(e.getMessage))))

  private def backup(existed: SettingsFile.Existed): Either[SettingError, Option[WriteOutcome.Backup]] =
    if !existed.value then Right(None)
    else
      store
        .copy(key, key.withSuffix(TempName.Suffixed.Suffix(".bak")), keepAttributes = Store.KeepPermissions(true))
        .left
        .map(io)
        .map(_ => Some(WriteOutcome.Backup(s"$shown.bak")))

  /** A parse of the file at `where`, its messages naming the settings file. */
  private def parsed(parse: Either[Problem, Option[Config]], where: String): Either[SettingError, Config] =
    parse.left
      .map(m => SettingError.Unreadable(Problem.Unclassified(Legacy.Text(m.sentence.replace(where, shown.value)))))
      .flatMap(_.toRight(SettingError.Unreadable(Problem.Unclassified(Legacy.Text(s"$shown: no such file")))))

  private def io(e: Store.Error): SettingError = SettingError.Io(e.message)
