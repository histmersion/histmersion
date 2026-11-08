package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
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
  object Tiles extends Newtype[String, Pure]
  type TilesMaxZoom = TilesMaxZoom.T
  object TilesMaxZoom extends Newtype[Int, Pure]
  type MarkerRadius = MarkerRadius.T
  object MarkerRadius extends Newtype[Int, Pure]
  type ViewportDebounceMs = ViewportDebounceMs.T
  object ViewportDebounceMs extends Newtype[Int, Pure]
  type PageSize = com.histmersion.PageSize
  val PageSize: com.histmersion.PageSize.type = com.histmersion.PageSize
  type FacetSuggestions = FacetSuggestions.T
  object FacetSuggestions extends Newtype[Int, Pure]
  type TreeDepth = TreeDepth.T
  object TreeDepth extends Newtype[Int, Pure]
  type TreeSamples = TreeSamples.T
  object TreeSamples extends Newtype[Int, Pure]
  type MaxBuckets = MaxBuckets.T
  object MaxBuckets extends Newtype[Int, Pure]
  type NarrowMaxWidthPx = NarrowMaxWidthPx.T
  object NarrowMaxWidthPx extends Newtype[Int, Pure]
  type NarrowMaxHeightPx = NarrowMaxHeightPx.T
  object NarrowMaxHeightPx extends Newtype[Int, Pure]
  type TabletMaxWidthPx = TabletMaxWidthPx.T
  object TabletMaxWidthPx extends Newtype[Int, Pure]
  type CountersPollSeconds = CountersPollSeconds.T
  object CountersPollSeconds extends Newtype[Int, Pure]
  type JobPollMs = JobPollMs.T
  object JobPollMs extends Newtype[Int, Pure]
  type TilesTone = TilesTone.T
  object TilesTone extends Newtype[String, Pure]
  type DocsUrl = DocsUrl.T
  object DocsUrl extends Newtype[String, Pure]
  type BaseMapSource = BaseMapSource.T
  object BaseMapSource extends Newtype[String, Pure]
