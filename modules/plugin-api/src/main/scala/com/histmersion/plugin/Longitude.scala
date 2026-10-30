package com.histmersion.plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Interval

/** Degrees east (negative: west) on WGS84, from -180 to 180. */
type Longitude = Longitude.T

object Longitude extends RefinedType[Double, Interval.Closed[-180.0, 180.0]]
