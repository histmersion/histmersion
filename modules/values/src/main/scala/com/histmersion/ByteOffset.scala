package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.GreaterEqual

type ByteOffset = ByteOffset.T
object ByteOffset extends Newtype[Long, GreaterEqual[0L]]
