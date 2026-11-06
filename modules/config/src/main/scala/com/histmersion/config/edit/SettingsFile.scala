package com.histmersion.config.edit

import com.histmersion.config.LoadedConfig
import org.ekrich.config.{Config, ConfigOrigin}

/** The settings file a [[ConfigFileWriter]] reads and changes, wherever it is kept: the server's in the store of its
  * directory, the command line's on the disk of the machine it runs on.
  */
trait SettingsFile:
  /** Where the file is, as messages and the settings page name it. */
  def shown: com.histmersion.config.edit.SettingsFile.Shown

  /** The file as it is now: its text (`None`: not there yet), its version, and its settings as parsed with its
    * includes, unresolved.
    */
  def read(): Either[SettingError, ConfigFileState]

  /** `text` written beside the file and given to `check` ([[SettingsFile.Staged]]); when the check passes, the text the
    * file had (when it `existed`) is kept as `<file>.bak` and the new text moved over the file in one step, with the
    * file's permissions. A symbolic link at the file stays: the file it points to is changed. The `.bak`, when one was
    * kept.
    */
  def replace(text: com.histmersion.config.edit.ConfigFileState.Text, existed: com.histmersion.config.edit.SettingsFile.Existed)(
      check: SettingsFile.Staged => Either[SettingError, Unit]
  ): Either[SettingError, Option[com.histmersion.config.edit.ConfigFileState.Text]]

object SettingsFile:
  type Existed = Existed.T
  object Existed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Shown = Shown.T
  object Shown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The new text, written beside the file: as parsed with its includes (`Left` when it does not read), which origins
    * are the text itself (not a file it includes), and what loading it as the settings file gives under an environment,
    * its messages naming the file it is to replace.
    */

  final case class Staged(
      parsed: Either[SettingError, Config],
      own: ConfigOrigin => Staged.OwnResult,
      load: Config => Either[Staged.LoadResultPart1, LoadedConfig]
  )

  object Staged:
    type OwnResult = OwnResult.T
    object OwnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LoadResultPart1 = com.histmersion.config.edit.SettingsFile.Problem
    val LoadResultPart1: _root_.com.histmersion.config.edit.SettingsFile.Problem.type = _root_.com.histmersion.config.edit.SettingsFile.Problem
