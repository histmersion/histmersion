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
  type Undated = Undated.T
  object Undated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The markers — a [[TiledPoints]] of [[PointKinds.Events]], attached and detached by the map with its other overlays
    * ([[MapView]]).
    */
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
      tiles: (com.histmersion.api.catalogue.Year, com.histmersion.api.catalogue.Year),
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
      kind = kind.map(k => TilesApi.TileQuery.Kind(k.value)),
      tier = tier.map(t => TilesApi.TileQuery.Tier(t.value)),
      context = Some(true),
      undated = Option.when(undated.value)(true)
    )

  /** Load the events of `tiles` (a span, playback's widened to its bucket), drawn for `span`. */
  def load(tiles: (com.histmersion.api.catalogue.Year, com.histmersion.api.catalogue.Year), span: (com.histmersion.api.Years.Astronomical, com.histmersion.api.Years.Astronomical)): Unit =
    layer.load(
      ApiClient.tileTemplate(
        query(UiState.toFilter(), tiles, UiState.eventKind.now().map(com.histmersion.api.Api.EventQuery.Kind(_)), UiState.eventTier.now().map(com.histmersion.api.Api.EventQuery.Tier(_)), Undated(UiState.eventsUndated.now()))
      ),
      span
    )
