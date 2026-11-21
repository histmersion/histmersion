package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Interval

type Longitude = Longitude.T

object Longitude extends Newtype[Double, Interval.Closed[-180.0, 180.0]]:
  extension (lon: T) def micro: Long = math.round(lon.value * 1e6)
