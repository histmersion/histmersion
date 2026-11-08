package com.histmersion
package util.basemap

import com.histmersion.ByteOffset
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** `rows` whole rows from `firstRow`, stored one after another at `offset` in the file: one range request. */
final case class RowRun(firstRow: RowRun.FirstRow, rows: RowRun.Rows, offset: ByteOffset, length: RowRun.Length)

object RowRun:
  type FirstRow = FirstRow.T
  object FirstRow extends Newtype[Int, Pure]
  type Rows = Rows.T
  object Rows extends Newtype[Int, Pure]
  type Length = Length.T
  object Length extends Newtype[Long, Pure]
