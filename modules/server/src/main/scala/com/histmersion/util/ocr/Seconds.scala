package com.histmersion
package util.ocr

import io.github.iltotore.iron.*

/** A number of seconds a conversion may take, in all or per page (a negative one counts as none). */
type Seconds = Seconds.T
object Seconds extends RefinedType[Int, Pure]
