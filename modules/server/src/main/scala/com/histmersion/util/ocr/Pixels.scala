package com.histmersion.util.ocr

import io.github.iltotore.iron.*

/** A length in pixels: the longest side an extracted image keeps. */
type Pixels = Pixels.T
object Pixels extends RefinedType[Int, Pure]
