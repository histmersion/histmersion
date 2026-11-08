package com.histmersion
package domain.catalogue.maptiles

/** A layer's tile features, from its cells and the records of its items ([[com.histmersion.api.catalogue.TileProps]]).
  * Pure.
  */
import hearth.kindlings.optics.syntax.*

object TileFeatures:
  import com.histmersion.api.catalogue.{EventGeo, SiteTile, TileProps as P}

  /** A text property; none when there is no value. */
  def text(k: String, v: String): Seq[(TilePoint.PropKey, TileValue)] = Seq(
    prop(k, TileValue.Text(TileValue.Text.Value(v)))
  )
  def text(k: String, v: Option[String]): Seq[(TilePoint.PropKey, TileValue)] =
    v.toSeq.map(s => prop(k, TileValue.Text(TileValue.Text.Value(s))))

  private def prop(k: String, v: TileValue): (TilePoint.PropKey, TileValue) = TilePoint.PropKey(k) -> v

  private def real(k: String, v: Double): (TilePoint.PropKey, TileValue) =
    prop(k, TileValue.Real(TileValue.Real.Value(v)))

  /** A whole-number property; none when there is no value. */
  def whole(k: String, v: Option[TileValue.Whole.Value]): Seq[(TilePoint.PropKey, TileValue)] =
    v.toSeq.map(n => prop(k, TileValue.Whole(n)))

  /** How many of a cell's items (or whether an item) have no known date: left out when none. */
  private def undated(c: TileCell): Seq[(TilePoint.PropKey, TileValue)] =
    Option
      .when(c.undated.value > 0)(prop(P.Undated, TileValue.Whole(TileValue.Whole.Value(c.undated.value.toLong))))
      .toSeq

  /** A cell of several items: its key, count, dates' range and box. */
  def cell(c: TileCell): TilePoint =
    TilePoint(
      c.x,
      c.y,
      text(P.Key, c.key.value) ++ Seq(
        prop(P.Count, TileValue.Whole(TileValue.Whole.Value(c.n.value.toLong)))
      ) ++ undated(c) ++
        c.midLo.toSeq.map(v => real(P.MidLo, v.value)) ++ c.midHi.toSeq.map(v => real(P.MidHi, v.value)) ++
        Seq(
          real(P.West, c.west.value),
          real(P.South, c.south.value),
          real(P.East, c.east.value),
          real(P.North, c.north.value)
        )
    )

  def site(c: TileCell, s: SiteTile): TilePoint =
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
        undated(c)
    )

  def event(c: TileCell, e: EventGeo): TilePoint =
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
        text(P.Tier, e.tier.value) ++ text(P.Size, e.size.map(_.value)) ++ text(P.Precision, e.precision.value) ++ text(
          P.Dating,
          e.dating.map(_.value)
        ) ++
        undated(c)
    )

  /** The features of a tile of zoom `zoom`: each cell of several as a cell, each item with its record (`record` by the
    * item's key; an item whose record is gone since its cell was read is left out), every one with the tile's zoom.
    */
  def of[A](
      zoom: TileCoord.Z,
      cells: Seq[TileCell],
      record: TileCell.Key => Option[A],
      feature: (TileCell, A) => TilePoint
  ): Seq[TilePoint] =
    val z = prop(P.Zoom, TileValue.Whole(TileValue.Whole.Value(zoom.value.toLong)))
    cells
      .flatMap(c => if c.isItem then record(c.key).map(feature(c, _)) else Some(cell(c)))
      .map(p => p.modify(_.props).setTo(z +: p.props))
