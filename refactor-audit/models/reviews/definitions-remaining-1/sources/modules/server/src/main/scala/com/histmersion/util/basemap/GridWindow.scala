package com.histmersion.util.basemap

import io.github.iltotore.iron.*

/** The rows `first` … `last` of a grid and the columns `firstCol` … `lastCol` (inclusive). */
final case class GridWindow(first: GridWindow.First, last: GridWindow.Last, firstCol: GridWindow.FirstCol, lastCol: GridWindow.LastCol):
  def rows: Int = last - first + 1

object GridWindow:
  type First = First.T
  object First extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Last = Last.T
  object Last extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FirstCol = FirstCol.T
  object FirstCol extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastCol = LastCol.T
  object LastCol extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
