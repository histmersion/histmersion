package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The `ui` block. The page reads it through `/api/config`, where the server adds what is no setting of this block:
  * whether writes need a key, whether agents run, the plugins and the base map's language.
  */
final case class UiSettings(
    tiles: UiSettings.Tiles,
    tilesMaxZoom: UiSettings.TilesMaxZoom,
    markerRadius: UiSettings.MarkerRadius,
    viewportDebounceMs: UiSettings.ViewportDebounceMs,
    pageSize: UiSettings.PageSize,
    facetSuggestions: UiSettings.FacetSuggestions,
    treeDepth: UiSettings.TreeDepth,
    treeSamples: UiSettings.TreeSamples,
    maxBuckets: UiSettings.MaxBuckets,
    debounce: UiDebounceSettings,
    time: UiTimeSettings,
    narrowMaxWidthPx: UiSettings.NarrowMaxWidthPx,
    narrowMaxHeightPx: UiSettings.NarrowMaxHeightPx,
    tabletMaxWidthPx: UiSettings.TabletMaxWidthPx,
    events: UiEventsSettings,
    mapTiles: UiMapTilesSettings,
    countersPollSeconds: UiSettings.CountersPollSeconds = 30,
    jobPollMs: UiSettings.JobPollMs = 1000,
    tilesTone: UiSettings.TilesTone = "light",
    docsUrl: UiSettings.DocsUrl = "",
    baseMapSource: UiSettings.BaseMapSource = "auto",
    mapStart: UiMapStartSettings = UiMapStartSettings("last", 37.0, 30.0, 5, "Anatolia")
) derives ConfigReader

object UiSettings:
  type Tiles = Tiles.T
  object Tiles extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilesMaxZoom = TilesMaxZoom.T
  object TilesMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkerRadius = MarkerRadius.T
  object MarkerRadius extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViewportDebounceMs = ViewportDebounceMs.T
  object ViewportDebounceMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageSize = PageSize.T
  object PageSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetSuggestions = FacetSuggestions.T
  object FacetSuggestions extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeDepth = TreeDepth.T
  object TreeDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeSamples = TreeSamples.T
  object TreeSamples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxBuckets = MaxBuckets.T
  object MaxBuckets extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NarrowMaxWidthPx = NarrowMaxWidthPx.T
  object NarrowMaxWidthPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NarrowMaxHeightPx = NarrowMaxHeightPx.T
  object NarrowMaxHeightPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TabletMaxWidthPx = TabletMaxWidthPx.T
  object TabletMaxWidthPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountersPollSeconds = CountersPollSeconds.T
  object CountersPollSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobPollMs = JobPollMs.T
  object JobPollMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilesTone = TilesTone.T
  object TilesTone extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DocsUrl = DocsUrl.T
  object DocsUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BaseMapSource = BaseMapSource.T
  object BaseMapSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
