package com.histmersion.server.basemap

import io.github.iltotore.iron.*

/** `rows` whole rows from `firstRow`, stored one after another at `offset` in the file: one range request. */
final case class RowRun(firstRow: Int, rows: Int, offset: ByteOffset, length: Long)
