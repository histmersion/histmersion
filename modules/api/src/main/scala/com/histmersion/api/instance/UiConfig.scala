package com.histmersion
package api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.MapTilesUiConfig
import com.histmersion.api.plugins.PluginsReport
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class UiConfig(
    tiles: UiConfig.Tiles,
    tilesMaxZoom: UiConfig.TilesMaxZoom,
    markerRadius: UiConfig.MarkerRadius,
    viewportDebounceMs: UiConfig.ViewportDebounceMs,
    pageSize: UiConfig.PageSize,
    facetSuggestions: UiConfig.FacetSuggestions,
    treeDepth: UiConfig.TreeDepth,
    treeSamples: UiConfig.TreeSamples,
    maxBuckets: UiConfig.MaxBuckets,
    debounce: DebounceConfig,
    time: TimeConfig,
    narrowMaxWidthPx: UiConfig.NarrowMaxWidthPx,
    narrowMaxHeightPx: UiConfig.NarrowMaxHeightPx,
    tabletMaxWidthPx: UiConfig.TabletMaxWidthPx,
    events: EventsUiConfig,
    mapTiles: MapTilesUiConfig = MapTilesUiConfig(),
    countersPollSeconds: UiConfig.CountersPollSeconds = UiConfig.CountersPollSeconds(30),
    jobPollMs: UiConfig.JobPollMs = UiConfig.JobPollMs(1000),
    writeKeyRequired: UiConfig.WriteKeyRequired = UiConfig.WriteKeyRequired(false),
    agentsEnabled: UiConfig.AgentsEnabled = UiConfig.AgentsEnabled(false),
    tilesTone: UiConfig.TilesTone = UiConfig.TilesTone("light"),
    plugins: PluginsReport = PluginsReport(),
    docsUrl: UiConfig.DocsUrl = UiConfig.DocsUrl(""),
    baseMapSource: UiConfig.BaseMapSource = com.histmersion.api.basemap.BaseMapSource.Wire("auto"),
    mapLanguage: UiConfig.MapLanguage = UiConfig.MapLanguage("en"),
    mapStart: MapStartConfig = MapStartConfig(),
    setupDue: UiConfig.SetupDue = UiConfig.SetupDue(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object UiConfig:
  type Tiles = Tiles.T
  object Tiles extends RefinedType[String, Pure]
  type TilesMaxZoom = TilesMaxZoom.T
  object TilesMaxZoom extends RefinedType[Int, Pure]
  type MarkerRadius = MarkerRadius.T
  object MarkerRadius extends RefinedType[Int, Pure]
  type ViewportDebounceMs = ViewportDebounceMs.T
  object ViewportDebounceMs extends RefinedType[Int, Pure]
  type PageSize = PageSize.T
  object PageSize extends RefinedType[Int, Pure]
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
  type WriteKeyRequired = WriteKeyRequired.T
  object WriteKeyRequired extends RefinedType[Boolean, Pure]
  type AgentsEnabled = AgentsEnabled.T
  object AgentsEnabled extends RefinedType[Boolean, Pure]
  type TilesTone = TilesTone.T
  object TilesTone extends RefinedType[String, Pure]
  type DocsUrl = DocsUrl.T
  object DocsUrl extends RefinedType[String, Pure]
  export com.histmersion.api.basemap.BaseMapSource.{Wire as BaseMapSource}
  type MapLanguage = MapLanguage.T
  object MapLanguage extends RefinedType[String, Pure]
  type SetupDue = SetupDue.T
  object SetupDue extends RefinedType[Boolean, Pure]
