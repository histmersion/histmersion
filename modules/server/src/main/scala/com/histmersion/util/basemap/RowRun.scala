package com.histmersion.util.basemap

import com.histmersion.ByteOffset
import io.github.iltotore.iron.*

/** `rows` whole rows from `firstRow`, stored one after another at `offset` in the file: one range request. */
final case class RowRun(firstRow: RowRun.FirstRow, rows: RowRun.Rows, offset: ByteOffset, length: RowRun.Length)

object RowRun:
  type FirstRow = FirstRow.T
  object FirstRow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Rows = Rows.T
  object Rows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Length = Length.T
  object Length extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
