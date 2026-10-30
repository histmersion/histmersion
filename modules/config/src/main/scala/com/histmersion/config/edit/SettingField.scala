package com.histmersion.config.edit

/** One setting the config file may hold, as reference.conf documents it.
  *
  * @param default what applies when neither the file nor the environment sets it (`None`: nothing, or not
  *                a plain value)
  * @param envVar  the environment variable that overrides it, if reference.conf names one
  * @param restart why a running server takes it on only when restarted, if it does */
final case class SettingField(key: SettingKey, kind: SettingKind, default: Option[SettingValue], doc: String,
    envVar: Option[String], restart: Option[String], secret: SecretRule):
  /** The top-level block it is in (`backups` of `histmersion.backups.keep`). */
  def section: String = key.segments.lift(1).getOrElse("")
