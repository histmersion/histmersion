package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Reloading the config in place (docs/research-plugins.md §3.5): `histmersion config reload`, or — with `watch` — by
  * itself when the config file changes, `watchDebounceMs` after the last change. `drainSeconds` is how long a reload
  * waits for the previous generation's last users (an in-flight request, a job step) before it reports them as still
  * draining.
  */
/** The terrain base map (docs/research-terrain-basemap.md): where its two PMTiles extracts live (empty = the XDG cache
  * directory, [[UserPaths.cacheDir]]), the region fetched, the free disk a fetch must leave, how politely the range
  * requests go, the sources (the Protomaps vector build, Mapterhorn's elevation, Natural Earth's physical names,
  * ETOPO1's sea floor), and how far the map zooms in on its two vector styles (`mapMaxZoom`).
  */
final case class BasemapConfig(
    dir: String,
    bbox: String,
    minFreeGb: Double,
    requestGapMs: Int,
    maxRequestMb: Int,
    overfetch: Double,
    timeoutSeconds: Int,
    retries: Int,
    tileCacheSeconds: Int,
    vector: BasemapVectorConfig,
    terrain: BasemapTerrainConfig,
    labels: BasemapLabelsConfig,
    bathymetry: BasemapBathymetryConfig,
    mapMaxZoom: Int,
    /** The areas the setup offers: "<label>|<world or a box>|<about how many GB>". */
    setupAreas: List[String] = Nil
) derives ConfigReader
