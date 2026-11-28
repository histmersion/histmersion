package com.histmersion
package domain.catalogue.maptiles

import scala.annotation.tailrec
import com.histmersion.domain.catalogue.maptiles

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The items of one layer in one square of a tile's grid (the squares a crowded tile counts its items by: `gx`/`gy` on
  * the grid of the whole world at the tile's zoom), summed so that two such counts of the same square add up: how many
  * (`n`), how many of them also have no known date (`dup`: an undated site the span shows by its findings, which the
  * undated sites' own count holds too), the sums of their places in the tile's units (all, and of those `dup`), the
  * least and greatest middle of their dated spans, their bounding box in degrees, and the least key among them.
  */
final case class GridCell(
    gx: GridCell.Gx,
    gy: GridCell.Gy,
    n: GridCell.N,
    dup: GridCell.Dup,
    sumX: GridCell.SumX,
    sumY: GridCell.SumY,
    dupX: GridCell.DupX,
    dupY: GridCell.DupY,
    midLo: Option[GridCell.MidLo],
    midHi: Option[GridCell.MidHi],
    west: GridCell.West,
    south: GridCell.South,
    east: GridCell.East,
    north: GridCell.North,
    one: GridCell.One
)

object GridCell:
  type Gx = Gx.T
  object Gx extends Newtype[Long, Pure]
  type Gy = Gy.T
  object Gy extends Newtype[Long, Pure]
  type N = N.T
  object N extends Newtype[Int, Pure]
  type Dup = Dup.T
  object Dup extends Newtype[Int, Pure]
  type SumX = SumX.T
  object SumX extends Newtype[Double, Pure]
  type SumY = SumY.T
  object SumY extends Newtype[Double, Pure]
  type DupX = DupX.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object DupX extends Newtype[Double, Pure]
  type DupY = DupY.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object DupY extends Newtype[Double, Pure]
  type MidLo = MidLo.T
  object MidLo extends Newtype[Double, Pure]
  type MidHi = MidHi.T
  object MidHi extends Newtype[Double, Pure]
  type West = West.T
  object West extends Newtype[Double, Pure]
  type South = South.T
  object South extends Newtype[Double, Pure]
  type East = East.T
  object East extends Newtype[Double, Pure]
  type North = North.T
  object North extends Newtype[Double, Pure]
  type One = One.T
  object One extends Newtype[String, Pure]

  type Total = Total.T
  object Total extends Newtype[Long, Pure]

  def merge(z: TileCoord.Z, dated: List[GridCell], undated: List[GridCell]): List[TileCell] =
    val byUndated = undated.iterator.map(u => (u.gx, u.gy) -> u).toMap
    val datedSquares = dated.iterator.map(d => (d.gx, d.gy)).toSet
    @tailrec def loop(rest: List[GridCell], out: List[TileCell]): List[TileCell] = rest match
      case d :: tail => loop(tail, cellOf(z, Some(d), byUndated.get((d.gx, d.gy))).fold(out)(_ :: out))
      case Nil       => out
    val withDated = loop(dated, Nil)
    val undatedOnly = undated.filterNot(u => datedSquares((u.gx, u.gy))).flatMap(u => cellOf(z, None, Some(u)))
    (withDated.reverse ++ undatedOnly)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def total(dated: List[GridCell], undated: List[GridCell]): Total =
    @tailrec def loop(rest: List[GridCell], sum: Total): Total = rest match
      case c :: tail => loop(tail, Total(sum.value + c.n.value - c.dup.value))
      case Nil       => sum
    Total(loop(dated, Total(0L)).value + undated.iterator.map(_.n.value.toLong).sum)

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
