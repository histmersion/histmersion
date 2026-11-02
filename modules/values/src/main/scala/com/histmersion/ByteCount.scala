package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.GreaterEqual

/** A number of bytes: a file's size, a range's length, the space a directory takes or a disk has left. */
type ByteCount = ByteCount.T
object ByteCount extends RefinedType[Long, GreaterEqual[0L]]:
  val zero: ByteCount = applyUnsafe(0L)
