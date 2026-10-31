package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.GreaterEqual

/** A position in a file or a byte range, counted from its first byte. */
type ByteOffset = ByteOffset.T
object ByteOffset extends RefinedType[Long, GreaterEqual[0L]]
