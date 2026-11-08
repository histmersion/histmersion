package com.histmersion
package api

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

object Geo:
  type Latitude = Latitude.T
  object Latitude extends RefinedType[Double, Pure]
  type Longitude = Longitude.T
  object Longitude extends RefinedType[Double, Pure]
