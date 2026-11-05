package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The `ui` block. The page reads it through `/api/config`, where the server adds what is no setting of this block:
  * whether writes need a key, whether agents run, the plugins and the base map's language.
  */
final case class UiSettings(
    tiles: String,
    tilesMaxZoom: Int,
    markerRadius: Int,
    viewportDebounceMs: Int,
    pageSize: Int,
    facetSuggestions: Int,
    treeDepth: Int,
    treeSamples: Int,
    maxBuckets: Int,
    debounce: UiDebounceSettings,
    time: UiTimeSettings,
    narrowMaxWidthPx: Int,
    narrowMaxHeightPx: Int,
    tabletMaxWidthPx: Int,
    events: UiEventsSettings,
    mapTiles: UiMapTilesSettings,
    countersPollSeconds: Int = 30,
    jobPollMs: Int = 1000,
    tilesTone: String = "light",
    docsUrl: String = "",
    baseMapSource: String = "auto",
    mapStart: UiMapStartSettings = UiMapStartSettings("last", 37.0, 30.0, 5, "Anatolia")
) derives ConfigReader
