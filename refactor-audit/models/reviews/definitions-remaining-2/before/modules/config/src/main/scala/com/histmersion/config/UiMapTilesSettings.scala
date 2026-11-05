package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `ui.map-tiles`: the map's point layers as vector tiles — the deepest tile cut (MapLibre's zoom), the most items a
  * shallower tile keeps alone, the side of the cells it counts the rest by (pixels of a 512-pixel tile), the server's
  * tile cache, and playback's time buckets (seconds of playback).
  */
final case class UiMapTilesSettings(
    maxZoom: Int,
    maxPoints: Int,
    cellPx: Int,
    cacheMb: Int,
    playbackBucketSeconds: Double,
    undatedKeptZoom: Int
) derives ConfigReader
