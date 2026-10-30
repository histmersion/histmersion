package com.histmersion.plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Interval

/** Degrees north (negative: south) on WGS84, from -90 to 90. */
type Latitude = Latitude.T

object Latitude extends RefinedType[Double, Interval.Closed[-90.0, 90.0]]
