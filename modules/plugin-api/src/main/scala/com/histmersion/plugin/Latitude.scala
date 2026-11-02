package com.histmersion.plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** Degrees north (negative: south) on WGS84, as a plugin states them. Unrefined: the host checks the range (-90 to 90)
  * where it becomes the model's latitude and leaves out a position off the globe (with a warning), keeping the record.
  */
type Latitude = Latitude.T

object Latitude extends RefinedType[Double, Pure]
