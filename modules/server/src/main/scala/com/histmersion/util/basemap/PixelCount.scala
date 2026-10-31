package com.histmersion.util.basemap

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive

type PixelCount = PixelCount.T
object PixelCount extends RefinedType[Int, Positive]
