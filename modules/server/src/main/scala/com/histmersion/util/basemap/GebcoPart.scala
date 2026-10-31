package com.histmersion.util.basemap

import com.histmersion.util.storage.StoreKey

/** One GEBCO GeoTIFF the region needs: where it is, its grid, the part of it the region covers and the range requests
  * that read that part.
  */
final case class GebcoPart(name: String, url: String, grid: GeoTiffGrid, window: GridWindow, runs: List[RowRun]):
  def bytes: Long = runs.map(_.length).sum

  /** The file `run` is kept in, in the fetch's work store. */
  def key(run: RowRun): StoreKey = StoreKey.applyUnsafe(s"gebco-$name-${run.firstRow}.bin")
