package com.histmersion.util.basemap

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive

type Degrees = Degrees.T
object Degrees extends RefinedType[Double, Pure]:

  type PerPixel = PerPixel.T
  object PerPixel extends RefinedType[Double, Positive]
