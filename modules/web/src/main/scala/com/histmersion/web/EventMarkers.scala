package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventGeo, Period, TileLayer, TilesApi}
import com.histmersion.api.library.Filter

/** The EVENTS layer (#1449): every event of the span shown — the corpus's and the catalogues' — read from the server's
  * tiles (`/api/tiles/events/…`, the filter's selection: its sources, the span, the kind and tier menus); the points
  * are [[PointKinds.Events]], the crowded tiles' cells bubbles.
  */
object EventMarkers:

  /** The markers — a [[TiledPoints]] of [[PointKinds.Events]], attached and detached by the map with its other overlays
    * ([[MapView]]).
    */
  type QueryTilesPart1 = QueryTilesPart1.T
  object QueryTilesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryTilesPart2 = QueryTilesPart2.T
  object QueryTilesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryKind = QueryKind.T
  object QueryKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryTier = QueryTier.T
  object QueryTier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryUndated = QueryUndated.T
  object QueryUndated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadTilesPart1 = LoadTilesPart1.T
  object LoadTilesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadTilesPart2 = LoadTilesPart2.T
  object LoadTilesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadSpanPart1 = LoadSpanPart1.T
  object LoadSpanPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadSpanPart2 = LoadSpanPart2.T
  object LoadSpanPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val layer: TiledPoints[EventGeo] = TiledPoints(
    TileLayer.Events,
    PointLayer(PointKinds.Events, () => MapView.dots, () => MapView.spanMid()),
    DotKind.Event,
    TileDecode.event,
    () => MapView.tilesConfig.maxZoom
  )

  /** Pure: the tiles' query for the events of `tiles` (a span) under `filter`, by kind and tier — the filter's paging
    * and its sort left out, which every tile of a span shares.
    */
  def query(
      filter: Filter,
      tiles: (com.histmersion.web.EventMarkers.QueryTilesPart1, com.histmersion.web.EventMarkers.QueryTilesPart2),
      kind: Option[com.histmersion.web.EventMarkers.QueryKind],
      tier: Option[com.histmersion.web.EventMarkers.QueryTier],
      undated: com.histmersion.web.EventMarkers.QueryUndated = false
  ): TilesApi.TileQuery =
    TilesApi.TileQuery(
      TileLayer.Events.wire,
      0,
      0,
      0,
      filter.modify(_.period).setTo(Some(Period(tiles._1, tiles._2))).modify(_.limit).setTo(Filter().limit).modify(_.offset).setTo(0).modify(_.sort).setTo(Filter().sort),
      kind = kind,
      tier = tier,
      context = Some(true),
      undated = Option.when(undated)(true)
    )

  /** Load the events of `tiles` (a span, playback's widened to its bucket), drawn for `span`. */
  def load(tiles: (com.histmersion.web.EventMarkers.LoadTilesPart1, com.histmersion.web.EventMarkers.LoadTilesPart2), span: (com.histmersion.web.EventMarkers.LoadSpanPart1, com.histmersion.web.EventMarkers.LoadSpanPart2)): Unit =
    layer.load(
      ApiClient.tileTemplate(
        query(UiState.toFilter(), tiles, UiState.eventKind.now(), UiState.eventTier.now(), UiState.eventsUndated.now())
      ),
      span
    )
