package com.histmersion
package plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** Degrees east (negative: west) on WGS84, as a plugin states them. Unrefined: the host checks the range (-180 to 180)
  * where it becomes the model's longitude and leaves out a position off the globe (with a warning), keeping the record.
  */
type Longitude = Longitude.T

object Longitude extends RefinedType[Double, Pure]
