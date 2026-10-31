package com.histmersion.util.basemap

import java.nio.file.Path

/** One GEBCO GeoTIFF the region needs: where it is, its grid, the part of it the region covers and the range requests
  * that read that part.
  */
final case class GebcoPart(name: String, url: String, grid: GeoTiffGrid, window: GridWindow, runs: List[RowRun]):
  def bytes: Long = runs.map(_.length).sum
  def file(work: Path, run: RowRun): Path = work.resolve(s"gebco-$name-${run.firstRow}.bin")
