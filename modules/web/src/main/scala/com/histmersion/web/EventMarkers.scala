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
  type TileX = TileX.T
  object TileX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileY = TileY.T
  object TileY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Undated = Undated.T
  object Undated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The markers — a [[TiledPoints]] of [[PointKinds.Events]], attached and detached by the map with its other overlays
    * ([[MapView]]).
    */
  type LoadTilesPart1 = _root_.com.histmersion.web.EventMarkers.TileX
  val LoadTilesPart1: _root_.com.histmersion.web.EventMarkers.TileX.type = _root_.com.histmersion.web.EventMarkers.TileX
  type LoadTilesPart2 = _root_.com.histmersion.web.EventMarkers.TileY
  val LoadTilesPart2: _root_.com.histmersion.web.EventMarkers.TileY.type = _root_.com.histmersion.web.EventMarkers.TileY

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
      tiles: (com.histmersion.web.EventMarkers.TileX, com.histmersion.web.EventMarkers.TileY),
      kind: Option[com.histmersion.api.Api.EventQuery.Kind],
      tier: Option[com.histmersion.api.Api.EventQuery.Tier],
      undated: com.histmersion.web.EventMarkers.Undated = com.histmersion.web.EventMarkers.Undated(false)
  ): TilesApi.TileQuery =
    TilesApi.TileQuery(
      TileLayer.Events.wire,
      com.histmersion.api.catalogue.TilesApi.TileQuery.Z(0),
      com.histmersion.api.catalogue.TilesApi.TileQuery.X(0),
      com.histmersion.api.catalogue.TilesApi.TileQuery.Y(0),
      filter.modify(_.period).setTo(Some(Period(tiles._1, tiles._2))).modify(_.limit).setTo(Filter().limit).modify(_.offset).setTo(0).modify(_.sort).setTo(Filter().sort),
      kind = kind,
      tier = tier,
      context = Some(true),
      undated = Option.when(undated)(true)
    )

  /** Load the events of `tiles` (a span, playback's widened to its bucket), drawn for `span`. */
  def load(tiles: (com.histmersion.web.EventMarkers.LoadTilesPart1, com.histmersion.web.EventMarkers.LoadTilesPart2), span: (com.histmersion.api.Years.Astronomical, com.histmersion.api.Years.Astronomical)): Unit =
    layer.load(
      ApiClient.tileTemplate(
        query(UiState.toFilter(), tiles, UiState.eventKind.now(), UiState.eventTier.now(), UiState.eventsUndated.now())
      ),
      span
    )
