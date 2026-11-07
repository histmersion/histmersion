package com.histmersion.web

import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.library.GeoHit

/** What one clustered marker holds, for the bubble it may be drawn in: its kind, how many things it counts for (a
  * findings' point counts its findings), their dated mids and least certainty ([[Certainty.NoSay]] for a dot that is
  * not a finding), and what its bubble's list shows — the findings themselves, or the dot's row.
  */
final case class Member(
    kind: DotKind,
    n: Member.N,
    mids: List[Member.Mids],
    certainty: Certainty,
    findings: List[GeoHit] = Nil,
    entry: Option[DotEntry] = None,
    cell: Option[com.histmersion.web.map.GeoBounds] = None,
    undated: Member.Undated = com.histmersion.web.Member.Undated(0)
):
  /** A CELL of a server tile ([[TileCellPoint]]): `n` items counted there, none of them to list — their box (`cell`) is
    * where a click zooms.
    */
  def aggregate: Boolean = cell.isDefined

object Member:

  type N = N.T
  object N extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mids = Mids.T
  object Mids extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Undated = Undated.T
  object Undated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
