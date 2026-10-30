package com.histmersion.config.edit

import java.nio.file.Path

/** What a write did to the config file.
  *
  * @param changed   the settings it changed
  * @param unchanged the settings the file already said so (nothing was written for them)
  * @param backup    the previous file, kept beside it (`histmersion.conf.bak`); none when nothing was written
  * @param created   the file was not there before
  * @param restart   the changed settings a running server takes on only after a restart, with why
  * @param warnings  what works but should be known: a `value:` credential, an environment variable that
  *                  overrides the new value */
final case class WriteOutcome(file: Path, changed: List[SettingKey], unchanged: List[SettingKey], backup: Option[Path],
    version: FileVersion, created: Boolean, restart: List[(SettingKey, String)], warnings: List[String]):
  def wrote: Boolean = changed.nonEmpty
