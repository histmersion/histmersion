package com.histmersion
package util.basemap

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** The rows `first` … `last` of a grid and the columns `firstCol` … `lastCol` (inclusive). */
final case class GridWindow(first: GridWindow.First, last: GridWindow.Last, firstCol: GridWindow.FirstCol, lastCol: GridWindow.LastCol):
  def rows: RowCount = RowCount(last.value - first.value + 1)

object GridWindow:

  type First = First.T
  object First extends Newtype[Int, Pure]
  type Last = Last.T
  object Last extends Newtype[Int, Pure]
  type FirstCol = FirstCol.T
  object FirstCol extends Newtype[Int, Pure]
  type LastCol = LastCol.T
  object LastCol extends Newtype[Int, Pure]
