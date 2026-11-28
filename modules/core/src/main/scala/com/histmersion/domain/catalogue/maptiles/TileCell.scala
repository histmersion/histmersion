package com.histmersion
package domain.catalogue.maptiles

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.catalogue.TileProps as P
import com.histmersion.api.catalogue.SiteTile
import com.histmersion.api.catalogue.EventGeo

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** What a tile's query answers before the tile is written: ONE item (`key` the item's, `n` 1) or a CELL of `n` items
  * (`key` the cell's), at `x`/`y` in tile units (a cell's: its items' mean), the least and greatest middle of their
  * dated spans (`None`: none of them is dated at both ends), their bounding box in degrees, and how many of them have
  * no known date (`undated`: drawn only when a viewer asks for them).
  */
final case class TileCell(
    key: TileCell.Key,
    x: TileCell.X,
    y: TileCell.Y,
    n: TileCell.N,
    midLo: Option[TileCell.MidLo],
    midHi: Option[TileCell.MidHi],
    west: TileCell.West,
    south: TileCell.South,
    east: TileCell.East,
    north: TileCell.North,
    undated: TileCell.Undated = TileCell.Undated(0)
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isItem: Boolean = n.value == 1

object TileCell:

  type Key = Key.T
  object Key extends Newtype[String, Pure]
  export TilePoint.{X, Y}
  type N = N.T
  object N extends Newtype[Int, Pure]
  export GridCell.MidLo
  export GridCell.MidHi
  type West = West.T
  object West extends Newtype[Double, Pure]
  type South = South.T
  object South extends Newtype[Double, Pure]
  type East = East.T
  object East extends Newtype[Double, Pure]
  type North = North.T
  object North extends Newtype[Double, Pure]
  type Undated = Undated.T
  object Undated extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def text(k: String, v: String): Seq[(TilePoint.PropKey, TileValue)] = Seq(
    prop(k, TileValue.Text(TileValue.Text.Value(v)))
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def text(k: String, v: Option[String]): Seq[(TilePoint.PropKey, TileValue)] =
    v.toSeq.map(s => prop(k, TileValue.Text(TileValue.Text.Value(s))))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def prop(k: String, v: TileValue): (TilePoint.PropKey, TileValue) = TilePoint.PropKey(k) -> v

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def real(k: String, v: Double): (TilePoint.PropKey, TileValue) =
    prop(k, TileValue.Real(TileValue.Real.Value(v)))

  def whole(k: String, v: Option[TileValue.Whole.Value]): Seq[(TilePoint.PropKey, TileValue)] =
    v.toSeq.map(n => prop(k, TileValue.Whole(n)))

  extension (c: TileCell)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def undatedProps: Seq[(TilePoint.PropKey, TileValue)] =
      Option
        .when(c.undated.value > 0)(prop(P.Undated, TileValue.Whole(TileValue.Whole.Value(c.undated.value.toLong))))
        .toSeq

  extension (c: TileCell)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
    def cell: TilePoint =
      TilePoint(
        c.x,
        c.y,
        text(P.Key, c.key.value) ++ Seq(
          prop(P.Count, TileValue.Whole(TileValue.Whole.Value(c.n.value.toLong)))
        ) ++ c.undatedProps ++
          c.midLo.toSeq.map(v => real(P.MidLo, v.value)) ++ c.midHi.toSeq.map(v => real(P.MidHi, v.value)) ++
          Seq(
            real(P.West, c.west.value),
            real(P.South, c.south.value),
            real(P.East, c.east.value),
            real(P.North, c.north.value)
          )
      )

  extension (c: TileCell)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
    def site(s: SiteTile): TilePoint =
      TilePoint(
        c.x,
        c.y,
        text(P.Key, c.key.value) ++ text(P.Id, s.id.value) ++ text(P.Label, s.label.value) ++ text(
          P.Kind,
          s.kind.value
        ) ++
          text(P.SiteType, s.siteType.map(_.value)) ++ text(P.Basis, s.basis.value) ++ whole(
            P.PeriodStart,
            s.periodStart.map(y => TileValue.Whole.Value(y.value))
          ) ++ whole(
            P.PeriodEnd,
            s.periodEnd.map(y => TileValue.Whole.Value(y.value))
          ) ++
          c.undatedProps
      )

  extension (c: TileCell)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
    def event(e: EventGeo): TilePoint =
      TilePoint(
        c.x,
        c.y,
        text(P.Key, c.key.value) ++ text(P.Id, e.id.value) ++ text(P.Label, e.label.value) ++ text(
          P.Kind,
          e.kind.value
        ) ++
          text(P.Scale, e.scale.value) ++ text(P.Historicity, e.historicity.value) ++ whole(
            P.NotBefore,
            e.timeSpan.notBefore.map(y => TileValue.Whole.Value(y.value))
          ) ++
          whole(P.BeginBy, e.timeSpan.beginBy.map(y => TileValue.Whole.Value(y.value))) ++ whole(
            P.EndFrom,
            e.timeSpan.endFrom.map(y => TileValue.Whole.Value(y.value))
          ) ++ whole(
            P.NotAfter,
            e.timeSpan.notAfter.map(y => TileValue.Whole.Value(y.value))
          ) ++
          text(P.Anchor, e.anchor.value) ++ text(P.PlaceId, e.placeId.map(_.value.toString)) ++ text(
            P.PlaceLabel,
            e.placeLabel.map(_.value)
          ) ++ text(
            P.ParentId,
            e.parentId.map(_.value)
          ) ++
          text(P.Tier, e.tier.value) ++ text(P.Size, e.size.map(_.value)) ++ text(
            P.Precision,
            e.precision.value
          ) ++ text(
            P.Dating,
            e.dating.map(_.value)
          ) ++
          c.undatedProps
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of[A](
      zoom: TileCoord.Z,
      cells: Seq[TileCell],
      record: Key => Option[A],
      feature: (TileCell, A) => TilePoint
  ): Seq[TilePoint] =
    val z = prop(P.Zoom, TileValue.Whole(TileValue.Whole.Value(zoom.value.toLong)))
    cells
      .flatMap(c => if c.isItem then record(c.key).map(feature(c, _)) else Some(c.cell))
      .map(p => p.modify(_.props).setTo(z +: p.props))
