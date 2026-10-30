package com.histmersion.server.basemap

import io.github.iltotore.iron.*

/** The rows `first` … `last` of a grid and the columns `firstCol` … `lastCol` (inclusive). */
final case class GridWindow(first: Int, last: Int, firstCol: Int, lastCol: Int):
  def rows: Int = last - first + 1
