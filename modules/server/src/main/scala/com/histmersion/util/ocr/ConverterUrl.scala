package com.histmersion.util.ocr

import io.github.iltotore.iron.*

/** Where the OCR converter answers (`converter.url`). */
type ConverterUrl = ConverterUrl.T
object ConverterUrl extends RefinedType[String, Pure]
