package com.histmersion
package domain.catalogue.maptiles

import scala.annotation.tailrec
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** A crowded sites tile with the sites of no known date in it: the dated sites the span shows and the undated ones,
  * each counted by the squares of the tile's grid ([[GridCell]]), as ONE count a square. An undated site the span shows
  * by its findings is in both counts; it stays a dated one, and leaves the undated count. Pure.
  */
object UndatedCells:
  type Total = Total.T
  object Total extends Newtype[Long, Pure]

  /** The cells of tile `z`/`x`/`y` (`perSide` squares a side) from the `dated` and the `undated` counts: a square of
    * one site is that site, any other its count, with how many of it have no known date.
    */

  export TileCoord.Z as MergeZ

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def merge(z: UndatedCells.MergeZ, dated: List[GridCell], undated: List[GridCell]): List[TileCell] =
    val byUndated = undated.iterator.map(u => (u.gx, u.gy) -> u).toMap
    val datedSquares = dated.iterator.map(d => (d.gx, d.gy)).toSet
    @tailrec def loop(rest: List[GridCell], out: List[TileCell]): List[TileCell] = rest match
      case d :: tail => loop(tail, cellOf(z, Some(d), byUndated.get((d.gx, d.gy))).fold(out)(_ :: out))
      case Nil       => out
    val withDated = loop(dated, Nil)
    val undatedOnly = undated.filterNot(u => datedSquares((u.gx, u.gy))).flatMap(u => cellOf(z, None, Some(u)))
    (withDated.reverse ++ undatedOnly)

  /** The total a tile would hold: every dated site and every undated one not counted among them. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def total(dated: List[GridCell], undated: List[GridCell]): Total =
    @tailrec def loop(rest: List[GridCell], sum: Total): Total = rest match
      case c :: tail => loop(tail, Total(sum.value + c.n.value - c.dup.value))
      case Nil       => sum
    Total(loop(dated, Total(0L)).value + undated.iterator.map(_.n.value.toLong).sum)

  /** One square's cell: `dated` and `undated` of the same square, either missing. None when it holds nothing. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[maptiles] def cellOf(z: TileCoord.Z, dated: Option[GridCell], undated: Option[GridCell]): Option[TileCell] =
    val d = dated.fold(0)(_.n.value)
    val dup = dated.fold(0)(_.dup.value)
    val u = (undated.fold(0)(_.n.value) - dup).max(0)
    val n = d + u
    Option.when(n > 0) {
      val sumX =
        dated.fold(0.0)(_.sumX.value) + undated.fold(0.0)(_.sumX.value) - (if undated.isDefined then
                                                                             dated.fold(0.0)(_.dupX.value)
                                                                           else 0.0)
      val sumY =
        dated.fold(0.0)(_.sumY.value) + undated.fold(0.0)(_.sumY.value) - (if undated.isDefined then
                                                                             dated.fold(0.0)(_.dupY.value)
                                                                           else 0.0)
      val both = dated.toList ++ undated.toList
      val key = TileCell.Key(
        if n > 1 then s"c$z/${both.head.gx}/${both.head.gy}"
        else if u == 0 then dated.fold("")(_.one.value)
        else undated.fold("")(_.one.value)
      )
      TileCell(
        key,
        TileCell.X(math.floor(sumX / n).toInt),
        TileCell.Y(math.floor(sumY / n).toInt),
        TileCell.N(n),
        dated.flatMap(_.midLo).map(m => TileCell.MidLo(m.value)),
        dated.flatMap(_.midHi).map(m => TileCell.MidHi(m.value)),
        TileCell.West(both.map(b => (b.west.value: Double)).min),
        TileCell.South(both.map(b => (b.south.value: Double)).min),
        TileCell.East(both.map(b => (b.east.value: Double)).max),
        TileCell.North(both.map(b => (b.north.value: Double)).max),
        undated = TileCell.Undated(u)
      )
    }
