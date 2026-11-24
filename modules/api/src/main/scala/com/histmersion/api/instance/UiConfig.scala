package com.histmersion
package api.instance
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.MapTilesUiConfig
import com.histmersion.api.plugins.PluginsReport
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

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
    keyRequired: UiConfig.KeyRequired = UiConfig.KeyRequired(false),
    agentsEnabled: UiConfig.AgentsEnabled = UiConfig.AgentsEnabled(false),
    tilesTone: UiConfig.TilesTone = UiConfig.TilesTone("light"),
    plugins: PluginsReport = PluginsReport(),
    docsUrl: UiConfig.DocsUrl = UiConfig.DocsUrl(""),
    baseMapSource: UiConfig.BaseMapSource = api.basemap.BaseMapSource.Wire("auto"),
    mapLanguage: UiConfig.MapLanguage = UiConfig.MapLanguage("en"),
    mapStart: MapStartConfig = MapStartConfig(),
    setupDue: UiConfig.SetupDue = UiConfig.SetupDue(false),
    buildId: BuildId = BuildId("")
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object UiConfig:
  type Tiles = Tiles.T
  object Tiles extends Newtype[String, Pure]
  type TilesMaxZoom = TilesMaxZoom.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TilesMaxZoom extends Newtype[Int, Pure]
  type MarkerRadius = MarkerRadius.T
  object MarkerRadius extends Newtype[Int, Pure]
  type ViewportDebounceMs = ViewportDebounceMs.T
  object ViewportDebounceMs extends Newtype[Int, Pure]
  type PageSize = PageSize.T
  object PageSize extends Newtype[Int, Pure]
  type FacetSuggestions = FacetSuggestions.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object FacetSuggestions extends Newtype[Int, Pure]
  type TreeDepth = TreeDepth.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TreeDepth extends Newtype[Int, Pure]
  type TreeSamples = TreeSamples.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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

  /** Whether the page must send the API key: it was let in with the key, not as a trusted network's. */
  type KeyRequired = KeyRequired.T
  object KeyRequired extends Newtype[Boolean, Pure]
  type AgentsEnabled = AgentsEnabled.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object AgentsEnabled extends Newtype[Boolean, Pure]
  type TilesTone = TilesTone.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TilesTone extends Newtype[String, Pure]
  type DocsUrl = DocsUrl.T
  object DocsUrl extends Newtype[String, Pure]:
    extension (url: T)
      def page(path: String): String =
        if url.value.isEmpty || url.value.endsWith("/") then s"${url.value}$path" else s"${url.value}/$path"
  export api.basemap.BaseMapSource.Wire as BaseMapSource
  type MapLanguage = MapLanguage.T
  object MapLanguage extends Newtype[String, Pure]
  type SetupDue = SetupDue.T
  object SetupDue extends Newtype[Boolean, Pure]
