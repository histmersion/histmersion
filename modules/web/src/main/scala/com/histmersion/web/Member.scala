package com.histmersion.web

import com.histmersion.api.*
import org.scalajs.dom

import scala.scalajs.js

/** What one clustered marker holds, for the bubble it may be drawn in: its kind, how many things it counts for (a
  * findings' point counts its findings), their dated mids and least certainty ([[Certainty.NoSay]] for a dot that is
  * not a finding), and what its bubble's list shows — the findings themselves, or the dot's row.
  */
final case class Member(
    kind: DotKind,
    n: Int,
    mids: List[Double],
    certainty: Certainty,
    findings: List[GeoHit] = Nil,
    entry: Option[DotEntry] = None,
    cell: Option[com.histmersion.web.map.GeoBounds] = None,
    undated: Int = 0
):
  /** A CELL of a server tile ([[TileCellPoint]]): `n` items counted there, none of them to list — their box (`cell`) is
    * where a click zooms.
    */
  def aggregate: Boolean = cell.isDefined
