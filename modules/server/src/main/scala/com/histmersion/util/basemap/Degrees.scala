package com.histmersion
package util.basemap

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive

type Degrees = Degrees.T
object Degrees extends Newtype[Double, Pure]:

  type PerPixel = PerPixel.T
  object PerPixel extends Newtype[Double, Positive]
