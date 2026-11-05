package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The gazetteer's external sources (#1064): where the pinned GeoNames / Wikidata extracts and the LPF drop folder are
  * (empty = under the data directory, [[UserPaths.dataDir]]), the manifest that pins the extracts (relative to
  * [[InitConfig.dataDir]]), records per transaction, the largest LPF FeatureCollection read whole, how long a download
  * of a missing extract may take, and the window of years before a known end that stands for an UNKNOWN start of a
  * GeoNames or Wikidata place ([[PlaceDating.EndOnly]]), and the kinds whose point-only GeoNames/Wikidata records are
  * not kept as places ([[Gazetteer.dropsPointArea]]) with the batch and time budget of their removal.
  */
final case class PlaceSourcesConfig(
    dir: String,
    lpfDir: String,
    manifest: String,
    batch: Int,
    maxLpfMb: Int,
    downloadTimeoutSeconds: Int,
    openStartWindowYears: Int,
    pointAreaKinds: String,
    pointAreaFixBatch: Int,
    pointAreaFixSeconds: Int
) derives ConfigReader:
  /** `point-area-kinds` as a set ([[Gazetteer.dropsPointArea]]); empty keeps every record. */
  def pointAreaKindSet: Set[String] =
    PeriodsConfig.split(pointAreaKinds).map(_.toLowerCase(java.util.Locale.ROOT)).toSet
