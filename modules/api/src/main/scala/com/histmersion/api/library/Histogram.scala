package com.histmersion
package api.library

import io.github.iltotore.iron.constraint.any.Pure

object Histogram:
  type Unit = Unit.T
  object Unit extends Newtype[String, Pure]
