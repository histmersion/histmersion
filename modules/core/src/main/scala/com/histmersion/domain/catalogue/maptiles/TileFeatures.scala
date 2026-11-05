package com.histmersion.domain.catalogue.maptiles

/** A layer's tile features, from its cells and the records of its items ([[com.histmersion.api.catalogue.TileProps]]).
  * Pure.
  */
import hearth.kindlings.optics.syntax.*

object TileFeatures:
  import com.histmersion.api.catalogue.{EventGeo, SiteTile, TileProps as P}

  /** A text property; none when there is no value. */
  def text(k: String, v: String): Seq[(String, TileValue)] = Seq(k -> TileValue.Text(v))
  def text(k: String, v: Option[String]): Seq[(String, TileValue)] = v.toSeq.map(s => k -> TileValue.Text(s))

  /** A whole-number property; none when there is no value. */
  def whole(k: String, v: Option[Long]): Seq[(String, TileValue)] = v.toSeq.map(n => k -> TileValue.Whole(n))

  /** How many of a cell's items (or whether an item) have no known date: left out when none. */
  private def undated(c: TileCell): Seq[(String, TileValue)] =
    Option.when(c.undated > 0)(P.Undated -> TileValue.Whole(c.undated.toLong)).toSeq

  /** A cell of several items: its key, count, dates' range and box. */
  def cell(c: TileCell): TilePoint =
    TilePoint(
      c.x,
      c.y,
      text(P.Key, c.key) ++ Seq(P.Count -> TileValue.Whole(c.n.toLong)) ++ undated(c) ++
        c.midLo.toSeq.map(v => P.MidLo -> TileValue.Real(v)) ++ c.midHi.toSeq.map(v => P.MidHi -> TileValue.Real(v)) ++
        Seq(
          P.West -> TileValue.Real(c.west),
          P.South -> TileValue.Real(c.south),
          P.East -> TileValue.Real(c.east),
          P.North -> TileValue.Real(c.north)
        )
    )

  def site(c: TileCell, s: SiteTile): TilePoint =
    TilePoint(
      c.x,
      c.y,
      text(P.Key, c.key) ++ text(P.Id, s.id) ++ text(P.Label, s.label) ++ text(P.Kind, s.kind) ++
        text(P.SiteType, s.siteType) ++ text(P.Basis, s.basis) ++ whole(P.PeriodStart, s.periodStart) ++ whole(
          P.PeriodEnd,
          s.periodEnd
        ) ++
        undated(c)
    )

  def event(c: TileCell, e: EventGeo): TilePoint =
    TilePoint(
      c.x,
      c.y,
      text(P.Key, c.key) ++ text(P.Id, e.id.value) ++ text(P.Label, e.label) ++ text(P.Kind, e.kind) ++
        text(P.Scale, e.scale) ++ text(P.Historicity, e.historicity) ++ whole(P.NotBefore, e.timeSpan.notBefore) ++
        whole(P.BeginBy, e.timeSpan.beginBy) ++ whole(P.EndFrom, e.timeSpan.endFrom) ++ whole(
          P.NotAfter,
          e.timeSpan.notAfter
        ) ++
        text(P.Anchor, e.anchor) ++ text(P.PlaceId, e.placeId.map(_.value.toString)) ++ text(P.PlaceLabel, e.placeLabel) ++ text(
          P.ParentId,
          e.parentId
        ) ++
        text(P.Tier, e.tier) ++ text(P.Size, e.size) ++ text(P.Precision, e.precision) ++ text(P.Dating, e.dating) ++
        undated(c)
    )

  /** The features of a tile of zoom `zoom`: each cell of several as a cell, each item with its record (`record` by the
    * item's key; an item whose record is gone since its cell was read is left out), every one with the tile's zoom.
    */
  def of[A](
      zoom: Int,
      cells: Seq[TileCell],
      record: String => Option[A],
      feature: (TileCell, A) => TilePoint
  ): Seq[TilePoint] =
    val z = P.Zoom -> TileValue.Whole(zoom.toLong)
    cells
      .flatMap(c => if c.isItem then record(c.key).map(feature(c, _)) else Some(cell(c)))
      .map(p => p.modify(_.props).setTo(z +: p.props))
