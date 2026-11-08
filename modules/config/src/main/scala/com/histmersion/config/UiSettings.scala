package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
    countersPollSeconds: UiSettings.CountersPollSeconds = UiSettings.CountersPollSeconds(30),
    jobPollMs: UiSettings.JobPollMs = UiSettings.JobPollMs(1000),
    tilesTone: UiSettings.TilesTone = UiSettings.TilesTone("light"),
    docsUrl: UiSettings.DocsUrl = UiSettings.DocsUrl(""),
    baseMapSource: UiSettings.BaseMapSource = UiSettings.BaseMapSource("auto"),
    mapStart: UiMapStartSettings = UiMapStartSettings(UiMapStartSettings.Mode("last"), UiMapStartSettings.Lat(37.0), UiMapStartSettings.Lon(30.0), UiMapStartSettings.Zoom(5.0), UiMapStartSettings.Label("Anatolia"))
) derives ConfigReader

object UiSettings:
  type Tiles = Tiles.T
  object Tiles extends RefinedType[String, Pure]
  type TilesMaxZoom = TilesMaxZoom.T
  object TilesMaxZoom extends RefinedType[Int, Pure]
  type MarkerRadius = MarkerRadius.T
  object MarkerRadius extends RefinedType[Int, Pure]
  type ViewportDebounceMs = ViewportDebounceMs.T
  object ViewportDebounceMs extends RefinedType[Int, Pure]
  type PageSize = com.histmersion.PageSize
  val PageSize: com.histmersion.PageSize.type = com.histmersion.PageSize
  type FacetSuggestions = FacetSuggestions.T
  object FacetSuggestions extends RefinedType[Int, Pure]
  type TreeDepth = TreeDepth.T
  object TreeDepth extends RefinedType[Int, Pure]
  type TreeSamples = TreeSamples.T
  object TreeSamples extends RefinedType[Int, Pure]
  type MaxBuckets = MaxBuckets.T
  object MaxBuckets extends RefinedType[Int, Pure]
  type NarrowMaxWidthPx = NarrowMaxWidthPx.T
  object NarrowMaxWidthPx extends RefinedType[Int, Pure]
  type NarrowMaxHeightPx = NarrowMaxHeightPx.T
  object NarrowMaxHeightPx extends RefinedType[Int, Pure]
  type TabletMaxWidthPx = TabletMaxWidthPx.T
  object TabletMaxWidthPx extends RefinedType[Int, Pure]
  type CountersPollSeconds = CountersPollSeconds.T
  object CountersPollSeconds extends RefinedType[Int, Pure]
  type JobPollMs = JobPollMs.T
  object JobPollMs extends RefinedType[Int, Pure]
  type TilesTone = TilesTone.T
  object TilesTone extends RefinedType[String, Pure]
  type DocsUrl = DocsUrl.T
  object DocsUrl extends RefinedType[String, Pure]
  type BaseMapSource = BaseMapSource.T
  object BaseMapSource extends RefinedType[String, Pure]
