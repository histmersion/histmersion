package com.histmersion.domain.settings

import com.histmersion.config.edit.{FileVersion, SettingReading}

import java.nio.file.Path

/** The settings as the settings file and the environment have them now: every setting (or those asked for), the file's
  * version a change is made against, what is wrong with the file, and whether the first-run setup is due.
  */
final case class SettingsSnapshot(
    file: Option[Path],
    exists: Boolean,
    version: FileVersion,
    readings: List[SettingReading],
    problems: List[String],
    setupDue: Boolean
)
