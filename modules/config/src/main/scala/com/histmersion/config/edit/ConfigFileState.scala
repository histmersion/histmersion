package com.histmersion.config.edit

import org.ekrich.config.{Config, ConfigOrigin}

import java.nio.file.Path

/** The config file as read: its text (`None`: not there yet), its version, and its settings as parsed with
  * its includes, unresolved — what a change is made against. */
final case class ConfigFileState(path: Path, text: Option[String], version: FileVersion, parsed: Config):
  /** Is `o` the file itself (not a file it includes)? */
  def here(o: ConfigOrigin): Boolean = ConfigFileState.sameFile(o, path)

  /** What the file itself writes at `key`, if it is a plain value there. */
  def ownValue(key: SettingKey): Option[SettingValue] =
    ConfigTextEdit.written(parsed, key, here) match
      case Written.Here(v) => SettingValue.fromConfig(v)
      case _               => None

object ConfigFileState:
  def sameFile(o: ConfigOrigin, path: Path): Boolean =
    Option(o.filename).exists(f => scala.util.Try(Path.of(f).toAbsolutePath.normalize).toOption.contains(path.toAbsolutePath.normalize))
