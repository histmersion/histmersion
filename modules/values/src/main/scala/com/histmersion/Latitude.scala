package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Interval

type Latitude = Latitude.T

object Latitude extends Newtype[Double, Interval.Closed[-90.0, 90.0]]:
  extension (lat: T) def micro: Long = math.round(lat.value * 1e6)
