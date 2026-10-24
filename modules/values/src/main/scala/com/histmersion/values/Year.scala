package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A year of deep time, signed: 3300 BCE is `Year(-3300)`. There is no year 0 in the calendar, but the
  * corpus counts astronomically, so every integer is a year. */
type Year = Year.T

object Year extends RefinedType[Int, Pure]
