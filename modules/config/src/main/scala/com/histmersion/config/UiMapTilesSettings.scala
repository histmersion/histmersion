package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `ui.map-tiles`: the map's point layers as vector tiles — the deepest tile cut (MapLibre's zoom), the most items a
  * shallower tile keeps alone, the side of the cells it counts the rest by (pixels of a 512-pixel tile), the server's
  * tile cache, and playback's time buckets (seconds of playback).
  */
final case class UiMapTilesSettings(
    maxZoom: UiMapTilesSettings.MaxZoom,
    maxPoints: UiMapTilesSettings.MaxPoints,
    cellPx: UiMapTilesSettings.CellPx,
    cacheMb: UiMapTilesSettings.CacheMb,
    playbackBucketSeconds: UiMapTilesSettings.PlaybackBucketSeconds,
    undatedKeptZoom: UiMapTilesSettings.UndatedKeptZoom
) derives ConfigReader

object UiMapTilesSettings:
  type MaxZoom = MaxZoom.T
  object MaxZoom extends RefinedType[Int, Pure]
  type MaxPoints = MaxPoints.T
  object MaxPoints extends RefinedType[Int, Pure]
  type CellPx = CellPx.T
  object CellPx extends RefinedType[Int, Pure]
  type CacheMb = CacheMb.T
  object CacheMb extends RefinedType[Int, Pure]
  type PlaybackBucketSeconds = PlaybackBucketSeconds.T
  object PlaybackBucketSeconds extends RefinedType[Double, Pure]
  type UndatedKeptZoom = UndatedKeptZoom.T
  object UndatedKeptZoom extends RefinedType[Int, Pure]
