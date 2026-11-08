package com.histmersion
package api.catalogue

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** Degrees north (negative: south) as the wire writes them. Unrefined: the server checks it where it becomes the model's
  * value.
  */
type Latitude = Latitude.T

object Latitude extends RefinedType[Double, Pure]
