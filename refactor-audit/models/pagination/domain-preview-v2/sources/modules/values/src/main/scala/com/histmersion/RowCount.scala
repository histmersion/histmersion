package com.histmersion

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type RowCount = RowCount.T
object RowCount extends RefinedType[Int, Pure]
