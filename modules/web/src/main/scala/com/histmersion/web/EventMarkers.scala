package com.histmersion
package web

import hearth.kindlings.optics.syntax.*

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventGeo, Period, TileLayer, TilesApi}
import com.histmersion.api.library.Filter
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.api.Api
import com.histmersion.lint.{tolerated, LintRule}

/** The EVENTS layer (#1449): every event of the span shown — the corpus's and the catalogues' — read from the server's
  * tiles (`/api/tiles/events/…`, the filter's selection: its sources, the span, the kind and tier menus); the points
  * are [[PointKinds.Events]], the crowded tiles' cells bubbles.
  */
object EventMarkers:
  enum Undated derives CanEqual:
    case Undated, Dated
  object Undated:
    extension (undated: Undated) def undated: Boolean = undated == Undated

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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def query(
      filter: Filter,
      tiles: (Year, Year),
      kind: Option[Api.EventQuery.Kind],
      tier: Option[Api.EventQuery.Tier],
      undated: Undated = Undated.Dated
  ): TilesApi.TileQuery =
    TilesApi.TileQuery(
      TileLayer.Events.wire,
      TilesApi.TileQuery.Z(0),
      TilesApi.TileQuery.X(0),
      TilesApi.TileQuery.Y(0),
      filter
        .modify(_.period)
        .setTo(Some(Period(tiles._1, tiles._2)))
        .modify(_.limit)
        .setTo(Filter().limit)
        .modify(_.offset)
        .setTo(Filter.Offset(0))
        .modify(_.sort)
        .setTo(Filter().sort),
      kind = kind.map(k => TilesApi.TileQuery.Kind(k.value)),
      tier = tier.map(t => TilesApi.TileQuery.Tier(t.value)),
      context = Some(TilesApi.TileQuery.Context.Context),
      undated = Option.when(undated.undated)(TilesApi.TileQuery.Undated.Undated)
    )

  /** Load the events of `tiles` (a span, playback's widened to its bucket), drawn for `span`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def load(tiles: (Year, Year), span: (Year.Astronomical, Year.Astronomical)): Unit =
    layer.load(
      ApiClient.tileTemplate(
        query(
          UiState.toFilter(),
          tiles,
          UiState.eventKind.now().map(Api.EventQuery.Kind(_)),
          UiState.eventTier.now().map(Api.EventQuery.Tier(_)),
          (if UiState.eventsUndated.now() then Undated.Undated else Undated.Dated)
        )
      ),
      span
    )
