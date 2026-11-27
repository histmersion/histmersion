package com.histmersion
package util.settings

import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.{External, Problem}
import com.histmersion.config.AppConfig
import com.histmersion.config.edit.{ConfigFileState, FileVersion, SettingError, SettingsFile, WriteOutcome}
import com.histmersion.util.storage.{Store, TempName}
import org.ekrich.config.{Config, ConfigFactory}

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.Using
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The settings file `key` of `store` (the settings file's directory), named `shown` in messages: the file the server
  * read its settings from. Its text and its includes are read as a load reads them, which needs the file on disk; a
  * store in memory has none to give, and every read and change of it is refused.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
@atBoundary(BoundaryKind.Config, "reads and replaces the server's settings file in its store")
final class StoredSettingsFile(store: Store, key: Store.Key, val shown: SettingsFile.Shown) extends SettingsFile:

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
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
        case None       => Left(SettingError.NoFile(Problem.Io.Wrong(Problem.Io.Path.of(shown), Problem.Io.Kind.File)))
        case Some(blob) =>
          try
            val text = ConfigFileState.Text(blob.text().value)
            for
              file <- store.onDisk(key).left.map(io)
              parsed <- parsed(AppConfig.parseUserFile(file), file.toString)
            yield ConfigFileState(Some(text), FileVersion.of(Some(text)), parsed, AppConfig.writtenIn(file))
          catch case e: IOException => Left(SettingError.Io(failed(Problem.Io.Access.Read, e)))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
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
                    .map(_.toNonEmpty.map(p => readAt(temp.toString, p)))
              )
            )
            bak <- backup(existed)
            _ <- r.commit(keepPermissions = Store.KeepPermissions(existed.value)).left.map(io)
          yield bak
        }
      }
    catch case e: IOException => Left(SettingError.Io(failed(Problem.Io.Access.Write, e)))

  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
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
      .map(m => SettingError.Unreadable(readAt(where, m)))
      .flatMap(_.toRight(SettingError.Unreadable(Problem.Io.Wrong(Problem.Io.Path.of(shown), Problem.Io.Kind.Present))))

  /** `problem`, found reading the settings at `where`: kept whole, shown naming the settings file. */
  private def readAt(where: String, problem: Problem): Problem =
    Problem.Config.ReadAt(Problem.Io.Path.of(shown), Problem.Io.Path(where), NonEmptyVector.of(problem))

  private def io(e: Store.Error): SettingError = SettingError.Io(e.message)

  /** `op` on the settings file failed: `e`, as the file system said. */
  private def failed(op: Problem.Io.Access, e: IOException): Problem =
    Problem.Io.File(op, Problem.Io.Path.of(shown), External.Reported.of(e))
