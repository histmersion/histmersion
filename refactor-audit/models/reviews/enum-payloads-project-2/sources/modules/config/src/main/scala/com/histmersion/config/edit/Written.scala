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
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
