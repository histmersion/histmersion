package com.histmersion
package util.basemap

/** One GEBCO GeoTIFF the region needs: where it is, its grid, the part of it the region covers and the range requests
  * that read that part.
  */
import com.histmersion.util.storage.Store
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
final case class GebcoPart(
    name: GebcoPart.Name,
    url: GebcoPart.Url,
    grid: GeoTiffGrid,
    window: GridWindow,
    runs: List[RowRun]
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def bytes: ByteCount = ByteCount.applyUnsafe(runs.map(_.length.value).sum)

  /** The file `run` is kept in, in the fetch's work store. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def key(run: RowRun): Store.Key = Store.Key.applyUnsafe(s"gebco-$name-${run.firstRow}.bin")

object GebcoPart:

  type Name = Name.T
  object Name extends Newtype[String, Pure]
  export RangeHttp.Url
