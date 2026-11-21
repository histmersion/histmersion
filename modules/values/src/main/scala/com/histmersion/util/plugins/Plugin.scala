package com.histmersion
package util.plugins

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

object Plugin:

  type Id = Id.T

  object Id extends Newtype[String, Match["[a-z0-9][a-z0-9-]*"]]
