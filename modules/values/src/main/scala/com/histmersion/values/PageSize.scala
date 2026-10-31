package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive

/** How many rows one page of a list holds. The default and the cap are the list's own. */
type PageSize = PageSize.T

object PageSize extends RefinedType[Int, Positive]
