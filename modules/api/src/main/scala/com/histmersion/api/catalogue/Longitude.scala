package com.histmersion
package api.catalogue

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** Degrees east (negative: west) as the wire writes them. Unrefined: the server checks it where it becomes the model's
  * value.
  */
type Longitude = Longitude.T

object Longitude extends RefinedType[Double, Pure]
