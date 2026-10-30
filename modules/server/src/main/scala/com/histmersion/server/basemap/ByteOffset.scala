package com.histmersion.server.basemap

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.GreaterEqual

type ByteOffset = ByteOffset.T
object ByteOffset extends RefinedType[Long, GreaterEqual[0L]]
