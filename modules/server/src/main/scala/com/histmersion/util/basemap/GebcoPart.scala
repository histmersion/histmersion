package com.histmersion
package util.basemap

/** One GEBCO GeoTIFF the region needs: where it is, its grid, the part of it the region covers and the range requests
  * that read that part.
  */
import com.histmersion.util.storage.Store
import io.github.iltotore.iron.constraint.any.Pure
final case class GebcoPart(
    name: GebcoPart.Name,
    url: GebcoPart.Url,
    grid: GeoTiffGrid,
    window: GridWindow,
    runs: List[RowRun]
):
  def bytes: ByteCount = ByteCount.applyUnsafe(runs.map(_.length.value).sum)

  /** The file `run` is kept in, in the fetch's work store. */
  def key(run: RowRun): Store.Key = Store.Key.applyUnsafe(s"gebco-$name-${run.firstRow}.bin")

object GebcoPart:

  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Url = RangeHttp.Url
  val Url: RangeHttp.Url.type = RangeHttp.Url
