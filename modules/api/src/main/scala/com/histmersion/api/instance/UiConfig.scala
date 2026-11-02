package com.histmersion.api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.MapTilesUiConfig
import com.histmersion.api.plugins.PluginsReport
import com.histmersion.api.given

final case class UiConfig(
    tiles: String,
    tilesMaxZoom: Int,
    markerRadius: Int,
    viewportDebounceMs: Int,
    pageSize: Int,
    facetSuggestions: Int,
    treeDepth: Int,
    treeSamples: Int,
    maxBuckets: Int,
    debounce: DebounceConfig,
    time: TimeConfig,
    narrowMaxWidthPx: Int,
    narrowMaxHeightPx: Int,
    tabletMaxWidthPx: Int,
    events: EventsUiConfig,
    mapTiles: MapTilesUiConfig = MapTilesUiConfig(),
    countersPollSeconds: Int = 30,
    jobPollMs: Int = 1000,
    writeKeyRequired: Boolean = false,
    agentsEnabled: Boolean = false,
    tilesTone: String = "light",
    plugins: PluginsReport = PluginsReport(),
    docsUrl: String = "",
    baseMapSource: String = "auto",
    mapLanguage: String = "en",
    mapStart: MapStartConfig = MapStartConfig(),
    setupDue: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object UiConfig:
  given Schema[UiConfig] = summon[KindlingsSchema[UiConfig]].schema
