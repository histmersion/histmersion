package com.histmersion
package util.basemap

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The rows `first` … `last` of a grid and the columns `firstCol` … `lastCol` (inclusive). */
final case class GridWindow(
    first: GridWindow.First,
    last: GridWindow.Last,
    firstCol: GridWindow.FirstCol,
    lastCol: GridWindow.LastCol
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rows: RowCount = RowCount(last.value - first.value + 1)

object GridWindow:

  type First = First.T
  object First extends Newtype[Int, Pure]
  type Last = Last.T
  object Last extends Newtype[Int, Pure]
  type FirstCol = FirstCol.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object FirstCol extends Newtype[Int, Pure]
  type LastCol = LastCol.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object LastCol extends Newtype[Int, Pure]
