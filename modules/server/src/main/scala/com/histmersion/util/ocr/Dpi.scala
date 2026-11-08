package com.histmersion
package util.ocr

import io.github.iltotore.iron.*

/** The resolution the converter rasterises figures at. */
type Dpi = Dpi.T
object Dpi extends Newtype[Int, Pure]
