package com.histmersion
package config.edit

import com.histmersion.util.problem.Problem
import com.histmersion.config.LoadedConfig
import org.ekrich.config.{Config, ConfigOrigin}
import io.github.iltotore.iron.constraint.any.Pure

/** The settings file a [[ConfigFileWriter]] reads and changes, wherever it is kept: the server's in the store of its
  * directory, the command line's on the disk of the machine it runs on.
  */
trait SettingsFile:
  /** Where the file is, as messages and the settings page name it. */
  def shown: SettingsFile.Shown

  /** The file as it is now: its text (`None`: not there yet), its version, and its settings as parsed with its
    * includes, unresolved.
    */
  def read(): Either[SettingError, ConfigFileState]

  /** `text` written beside the file and given to `check` ([[SettingsFile.Staged]]); when the check passes, the text the
    * file had (when it `existed`) is kept as `<file>.bak` and the new text moved over the file in one step, with the
    * file's permissions. A symbolic link at the file stays: the file it points to is changed. The `.bak`, when one was
    * kept.
    */
  def replace(text: ConfigFileState.Text, existed: SettingsFile.Existed)(
      check: SettingsFile.Staged => Either[SettingError, Unit]
  ): Either[SettingError, Option[WriteOutcome.Backup]]

object SettingsFile:
  type Existed = Existed.T
  object Existed extends Newtype[Boolean, Pure]
  type Path = Path.T
  object Path extends Newtype[String, Pure]
  type Shown = Shown.T
  object Shown extends Newtype[String, Pure]

  /** The new text, written beside the file: as parsed with its includes (`Left` when it does not read), which origins
    * are the text itself (not a file it includes), and what loading it as the settings file gives under an environment,
    * its messages naming the file it is to replace.
    */

  final case class Staged(
      parsed: Either[SettingError, Config],
      own: ConfigOrigin => Boolean,
      load: Config => Either[Problem, LoadedConfig]
  )
