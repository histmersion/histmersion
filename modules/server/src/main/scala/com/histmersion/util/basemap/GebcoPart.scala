package com.histmersion.util.basemap

/** One GEBCO GeoTIFF the region needs: where it is, its grid, the part of it the region covers and the range requests
  * that read that part.
  */
import com.histmersion.util.storage.Store
final case class GebcoPart(name: String, url: String, grid: GeoTiffGrid, window: GridWindow, runs: List[RowRun]):
  def bytes: Long = runs.map(_.length).sum

  /** The file `run` is kept in, in the fetch's work store. */
  def key(run: RowRun): Store.Key = Store.Key.applyUnsafe(s"gebco-$name-${run.firstRow}.bin")
