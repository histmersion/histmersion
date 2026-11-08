package com.histmersion
package util.plugins

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

object Plugin:

  /** A plugin's id: its settings key (`histmersion.plugins.<id>`), kebab-case. */
  type Id = Id.T

  object Id extends RefinedType[String, Match["[a-z0-9][a-z0-9-]*"]]
