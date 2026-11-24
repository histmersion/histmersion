package com.histmersion
package config.edit

import org.ekrich.config.ConfigValue

import scala.annotation.tailrec
import com.histmersion.lint.{FeaturePlace, movesTo}

/** Where a value of the config file, as read with its includes, was written: in the file itself, or in a file it
  * includes.
  */
@movesTo(FeaturePlace.UtilSettings)
enum Written:
  case Nowhere
  case Here(value: ConfigValue)
  case Elsewhere(file: Written.Elsewhere.File)

  /** Behind a `${…}` substitution, `+=`, or an object merged with one. */
  case Substituted(at: SettingKey)

object Written:
  object Elsewhere:
    export SettingsFile.Path as File
