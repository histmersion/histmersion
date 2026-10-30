package com.histmersion.server.basemap

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive

type DegreesPerPixel = DegreesPerPixel.T
object DegreesPerPixel extends RefinedType[Double, Positive]
