package com.histmersion.util.settings

import com.histmersion.config.edit.{FileVersion, SettingReading}

/** The settings as the settings file and the environment have them now: where the file is (as messages name it), every
  * setting (or those asked for), the file's version a change is made against, what is wrong with the file, and whether
  * the first-run setup is due.
  */
final case class SettingsSnapshot(
    file: Option[String],
    exists: Boolean,
    version: FileVersion,
    readings: List[SettingReading],
    problems: List[String],
    setupDue: Boolean
)
