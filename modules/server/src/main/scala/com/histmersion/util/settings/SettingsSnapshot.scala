package com.histmersion
package util.settings

import com.histmersion.config.{PgBytes, PgParameter}
import com.histmersion.config.edit.{FileVersion, SettingReading}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.TwoCases

/** The settings as the settings file and the environment have them now: where the file is (as messages name it), every
  * setting (or those asked for), the file's version a change is made against, what is wrong with the file, whether the
  * first-run setup is due, and what each bundled-Postgres size set to `auto` comes to on this machine.
  */
final case class SettingsSnapshot(
    file: Option[SettingsSnapshot.File],
    exists: SettingsSnapshot.Exists,
    version: FileVersion,
    readings: List[SettingReading],
    problems: List[SettingsSnapshot.Problems],
    setupDue: SettingsSnapshot.SetupDue,
    automatic: List[(PgParameter, PgBytes)] = Nil
)

object SettingsSnapshot:
  type File = File.T
  object File extends Newtype[String, Pure]
  enum Exists derives CanEqual:
    case Exists, Missing
  object Exists extends TwoCases[Exists]:
    extension (exists: Exists) def exists: Boolean = exists == Exists
  type Problems = Problems.T
  object Problems extends Newtype[String, Pure]
  enum SetupDue derives CanEqual:
    case Due, Done
  object SetupDue extends TwoCases[SetupDue]:
    extension (setupDue: SetupDue) def setupDue: Boolean = setupDue == Due
