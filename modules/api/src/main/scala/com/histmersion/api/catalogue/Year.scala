package com.histmersion
package api.catalogue

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A year of deep time as the wire writes it: a signed integer, BCE negative (3300 BCE is `-3300`). Unrefined: the server checks it where it becomes the model's
  * value.
  */
type Year = Year.T

object Year extends Newtype[Long, Pure]
