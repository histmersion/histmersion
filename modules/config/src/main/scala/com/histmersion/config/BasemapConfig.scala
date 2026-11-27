package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

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
@movesTo(FeaturePlace.UtilBasemap)
final case class BasemapConfig(
    dir: BasemapConfig.Dir,
    bbox: BasemapConfig.Bbox,
    minFreeGb: BasemapConfig.MinFreeGb,
    requestGapMs: BasemapConfig.RequestGapMs,
    maxRequestMb: BasemapConfig.MaxRequestMb,
    overfetch: BasemapConfig.Overfetch,
    timeoutSeconds: BasemapConfig.TimeoutSeconds,
    retries: BasemapConfig.Retries,
    tileCacheSeconds: BasemapConfig.TileCacheSeconds,
    vector: BasemapVectorConfig,
    terrain: BasemapTerrainConfig,
    labels: BasemapLabelsConfig,
    bathymetry: BasemapBathymetryConfig,
    mapMaxZoom: BasemapConfig.MapMaxZoom,
    /** The areas the setup offers: "<label>|<world or a box>|<about how many GB>". */
    setupAreas: List[BasemapConfig.SetupAreas] = Nil
) derives ConfigReader

object BasemapConfig:
  type Dir = Dir.T
  object Dir extends Newtype[String, Pure]
  type Bbox = Bbox.T
  object Bbox extends Newtype[String, Pure]
  type MinFreeGb = MinFreeGb.T
  object MinFreeGb extends Newtype[Double, Pure]
  type RequestGapMs = RequestGapMs.T
  object RequestGapMs extends Newtype[Int, Pure]
  type MaxRequestMb = MaxRequestMb.T
  object MaxRequestMb extends Newtype[Int, Pure]
  type Overfetch = Overfetch.T
  object Overfetch extends Newtype[Double, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends Newtype[Int, Pure]
  type Retries = Retries.T
  object Retries extends Newtype[Int, Pure]
  type TileCacheSeconds = TileCacheSeconds.T
  object TileCacheSeconds extends Newtype[Int, Pure]
  type MapMaxZoom = MapMaxZoom.T
  object MapMaxZoom extends Newtype[Int, Pure]
  type SetupAreas = SetupAreas.T
  object SetupAreas extends Newtype[String, Pure]
