package com.histmersion.domain.catalogue.maptiles

import scala.annotation.tailrec

/** A crowded sites tile with the sites of no known date in it: the dated sites the span shows and the undated ones,
  * each counted by the squares of the tile's grid ([[GridCell]]), as ONE count a square. An undated site the span shows
  * by its findings is in both counts; it stays a dated one, and leaves the undated count. Pure.
  */
object UndatedCells:

  /** The cells of tile `z`/`x`/`y` (`perSide` squares a side) from the `dated` and the `undated` counts: a square of
    * one site is that site, any other its count, with how many of it have no known date.
    */
  type TotalLoopResult = TotalLoopResult.T
  object TotalLoopResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TotalLoopSum = TotalLoopSum.T
  object TotalLoopSum extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MergeZ = _root_.com.histmersion.domain.catalogue.maptiles.UndatedCells.CellOfZ
  val MergeZ: _root_.com.histmersion.domain.catalogue.maptiles.UndatedCells.CellOfZ.type = _root_.com.histmersion.domain.catalogue.maptiles.UndatedCells.CellOfZ
  type TotalResult = TotalResult.T
  object TotalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CellOfZ = CellOfZ.T
  object CellOfZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def merge(z: com.histmersion.domain.catalogue.maptiles.UndatedCells.MergeZ, dated: List[GridCell], undated: List[GridCell]): List[TileCell] =
    val byUndated = undated.iterator.map(u => (u.gx, u.gy) -> u).toMap
    val datedSquares = dated.iterator.map(d => (d.gx, d.gy)).toSet
    @tailrec def loop(rest: List[GridCell], out: List[TileCell]): List[TileCell] = rest match
      case d :: tail => loop(tail, cellOf(z, Some(d), byUndated.get((d.gx, d.gy))).fold(out)(_ :: out))
      case Nil       => out
    val withDated = loop(dated, Nil)
    val undatedOnly = undated.filterNot(u => datedSquares((u.gx, u.gy))).flatMap(u => cellOf(z, None, Some(u)))
    (withDated.reverse ++ undatedOnly)

  /** The total a tile would hold: every dated site and every undated one not counted among them. */
  def total(dated: List[GridCell], undated: List[GridCell]): com.histmersion.domain.catalogue.maptiles.UndatedCells.TotalResult =
    @tailrec def loop(rest: List[GridCell], sum: com.histmersion.domain.catalogue.maptiles.UndatedCells.TotalLoopSum): com.histmersion.domain.catalogue.maptiles.UndatedCells.TotalLoopResult = rest match
      case c :: tail => loop(tail, sum + c.n - c.dup)
      case Nil       => sum
    loop(dated, 0L) + undated.iterator.map(_.n.toLong).sum

  /** One square's cell: `dated` and `undated` of the same square, either missing. None when it holds nothing. */
  private[maptiles] def cellOf(z: com.histmersion.domain.catalogue.maptiles.UndatedCells.CellOfZ, dated: Option[GridCell], undated: Option[GridCell]): Option[TileCell] =
    val d = dated.fold(0)(_.n)
    val dup = dated.fold(0)(_.dup)
    val u = (undated.fold(0)(_.n) - dup).max(0)
    val n = d + u
    Option.when(n > 0) {
      val sumX =
        dated.fold(0.0)(_.sumX) + undated.fold(0.0)(_.sumX) - (if undated.isDefined then dated.fold(0.0)(_.dupX)
                                                               else 0.0)
      val sumY =
        dated.fold(0.0)(_.sumY) + undated.fold(0.0)(_.sumY) - (if undated.isDefined then dated.fold(0.0)(_.dupY)
                                                               else 0.0)
      val both = dated.toList ++ undated.toList
      val key =
        if n > 1 then s"c$z/${both.head.gx}/${both.head.gy}"
        else if u == 0 then dated.fold("")(_.one)
        else undated.fold("")(_.one)
      TileCell(
        key,
        math.floor(sumX / n).toInt,
        math.floor(sumY / n).toInt,
        n,
        dated.flatMap(_.midLo),
        dated.flatMap(_.midHi),
        both.map(_.west).min,
        both.map(_.south).min,
        both.map(_.east).max,
        both.map(_.north).max,
        undated = u
      )
    }
