package com.histmersion
package util.basemap

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive

type PixelCount = PixelCount.T
object PixelCount extends Newtype[Int, Positive]
