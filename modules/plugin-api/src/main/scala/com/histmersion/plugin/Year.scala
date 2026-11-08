package com.histmersion
package plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A year of deep time, signed: 1600 BCE is `Year(-1600)`. Counted astronomically, so every integer is a year (there is
  * a year 0).
  */
type Year = Year.T

object Year extends Newtype[Int, Pure]
