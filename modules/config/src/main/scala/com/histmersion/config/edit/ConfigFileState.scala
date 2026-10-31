package com.histmersion.config.edit

import org.ekrich.config.{Config, ConfigOrigin}

/** The config file as read: its text (`None`: not there yet), its version, and its settings as parsed with its
  * includes, unresolved — what a change is made against. `own` says which origins are the file itself (not a file it
  * includes).
  */
final case class ConfigFileState(
    text: Option[String],
    version: FileVersion,
    parsed: Config,
    own: ConfigOrigin => Boolean
):
  /** Is `o` the file itself (not a file it includes)? */
  def here(o: ConfigOrigin): Boolean = own(o)

  /** What the file itself writes at `key`, if it is a plain value there. */
  def ownValue(key: SettingKey): Option[SettingValue] =
    ConfigTextEdit.written(parsed, key, here) match
      case Written.Here(v) => SettingValue.fromConfig(v)
      case _               => None
