package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxPoints = MaxPoints.T
  object MaxPoints extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CellPx = CellPx.T
  object CellPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CacheMb = CacheMb.T
  object CacheMb extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaybackBucketSeconds = PlaybackBucketSeconds.T
  object PlaybackBucketSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UndatedKeptZoom = UndatedKeptZoom.T
  object UndatedKeptZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
