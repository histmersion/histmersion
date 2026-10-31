package com.histmersion.util.storage

import com.histmersion.values.ByteCount

/** What a bounded walk of a store counted: the bytes and files below it, whether its directory is there, whether the
  * budget stopped the walk first (the counts are then a lower bound), and how many entries could not be read.
  */
final case class StoreMeasure(
    bytes: ByteCount,
    files: Long,
    exists: Boolean,
    partial: Boolean = false,
    unreadable: Int = 0
)

object StoreMeasure:
  val absent: StoreMeasure = StoreMeasure(ByteCount.zero, 0L, exists = false)
