package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Interval

/** Degrees east (negative: west) on WGS84. */
type Longitude = Longitude.T

object Longitude extends RefinedType[Double, Interval.Closed[-180.0, 180.0]]:
  extension (lon: T) def micro: Long = math.round(lon.value * 1e6)
