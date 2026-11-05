package com.histmersion.util.settings

import com.histmersion.config.edit.{FileVersion, SettingReading}

/** The settings as the settings file and the environment have them now: where the file is (as messages name it), every
  * setting (or those asked for), the file's version a change is made against, what is wrong with the file, and whether
  * the first-run setup is due.
  */
final case class SettingsSnapshot(
    file: Option[SettingsSnapshot.File],
    exists: SettingsSnapshot.Exists,
    version: FileVersion,
    readings: List[SettingReading],
    problems: List[SettingsSnapshot.Problems],
    setupDue: SettingsSnapshot.SetupDue
)

object SettingsSnapshot:
  type File = File.T
  object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Exists = Exists.T
  object Exists extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problems = Problems.T
  object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetupDue = SetupDue.T
  object SetupDue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
