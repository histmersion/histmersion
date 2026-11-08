package com.histmersion.api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.MapTilesUiConfig
import com.histmersion.api.plugins.PluginsReport
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

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
    countersPollSeconds: UiConfig.CountersPollSeconds = _root_.com.histmersion.api.instance.UiConfig.CountersPollSeconds(30),
    jobPollMs: UiConfig.JobPollMs = _root_.com.histmersion.api.instance.UiConfig.JobPollMs(1000),
    writeKeyRequired: UiConfig.WriteKeyRequired = _root_.com.histmersion.api.instance.UiConfig.WriteKeyRequired(false),
    agentsEnabled: UiConfig.AgentsEnabled = _root_.com.histmersion.api.instance.UiConfig.AgentsEnabled(false),
    tilesTone: UiConfig.TilesTone = _root_.com.histmersion.api.instance.UiConfig.TilesTone("light"),
    plugins: PluginsReport = PluginsReport(),
    docsUrl: UiConfig.DocsUrl = _root_.com.histmersion.api.instance.UiConfig.DocsUrl(""),
    baseMapSource: UiConfig.BaseMapSource = _root_.com.histmersion.api.basemap.BaseMapSource.Wire("auto"),
    mapLanguage: UiConfig.MapLanguage = _root_.com.histmersion.api.instance.UiConfig.MapLanguage("en"),
    mapStart: MapStartConfig = MapStartConfig(),
    setupDue: UiConfig.SetupDue = _root_.com.histmersion.api.instance.UiConfig.SetupDue(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object UiConfig:
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
  type WriteKeyRequired = WriteKeyRequired.T
  object WriteKeyRequired extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentsEnabled = AgentsEnabled.T
  object AgentsEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilesTone = TilesTone.T
  object TilesTone extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DocsUrl = DocsUrl.T
  object DocsUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  export com.histmersion.api.basemap.BaseMapSource.{Wire as BaseMapSource}
  type MapLanguage = MapLanguage.T
  object MapLanguage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetupDue = SetupDue.T
  object SetupDue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
