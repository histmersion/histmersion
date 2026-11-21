package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.GreaterEqual

type ByteCount = ByteCount.T
object ByteCount extends Newtype[Long, GreaterEqual[0L]]:
  val zero: ByteCount = applyUnsafe(0L)

  extension (b: ByteCount) def wholeMegabytes: Long = b.value >> 20
