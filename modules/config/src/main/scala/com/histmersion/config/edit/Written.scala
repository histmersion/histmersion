package com.histmersion.config.edit

import org.ekrich.config.ConfigValue

import scala.annotation.tailrec

/** Where a value of the config file, as read with its includes, was written: in the file itself, or in a file it
  * includes.
  */
enum Written:
  case Nowhere
  case Here(value: ConfigValue)
  case Elsewhere(file: Written.Elsewhere.File)

  /** Behind a `${…}` substitution, `+=`, or an object merged with one. */
  case Substituted(at: SettingKey)

object Written:
  object Elsewhere:
    type File = com.histmersion.config.edit.SettingsFile.Path
    val File: _root_.com.histmersion.config.edit.SettingsFile.Path.type = _root_.com.histmersion.config.edit.SettingsFile.Path
