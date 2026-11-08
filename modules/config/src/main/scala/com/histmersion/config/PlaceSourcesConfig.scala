package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** The gazetteer's external sources (#1064): where the pinned GeoNames / Wikidata extracts and the LPF drop folder are
  * (empty = under the data directory, [[UserPaths.dataDir]]), the manifest that pins the extracts (relative to
  * [[InitConfig.dataDir]]), records per transaction, the largest LPF FeatureCollection read whole, how long a download
  * of a missing extract may take, and the window of years before a known end that stands for an UNKNOWN start of a
  * GeoNames or Wikidata place ([[PlaceDating.EndOnly]]), and the kinds whose point-only GeoNames/Wikidata records are
  * not kept as places ([[Gazetteer.dropsPointArea]]) with the batch and time budget of their removal.
  */
final case class PlaceSourcesConfig(
    dir: PlaceSourcesConfig.Dir,
    lpfDir: PlaceSourcesConfig.LpfDir,
    manifest: PlaceSourcesConfig.Manifest,
    batch: PlaceSourcesConfig.Batch,
    maxLpfMb: PlaceSourcesConfig.MaxLpfMb,
    downloadTimeoutSeconds: PlaceSourcesConfig.DownloadTimeoutSeconds,
    openStartWindowYears: PlaceSourcesConfig.OpenStartWindowYears,
    pointAreaKinds: PlaceSourcesConfig.PointAreaKinds,
    pointAreaFixBatch: PlaceSourcesConfig.PointAreaFixBatch,
    pointAreaFixSeconds: PlaceSourcesConfig.PointAreaFixSeconds
) derives ConfigReader:
  /** `point-area-kinds` as a set ([[Gazetteer.dropsPointArea]]); empty keeps every record. */
  def pointAreaKindSet: Set[PlaceSourcesConfig.PointAreaKind] =
    Option(pointAreaKinds.value).toList.flatMap(_.split(",").toList).map(_.trim).filter(_.nonEmpty).distinct.map(k => PlaceSourcesConfig.PointAreaKind(k.toLowerCase(java.util.Locale.ROOT))).toSet

object PlaceSourcesConfig:
  type PointAreaKind = PointAreaKind.T
  object PointAreaKind extends Newtype[String, Pure]

  type Dir = Dir.T
  object Dir extends Newtype[String, Pure]
  type LpfDir = LpfDir.T
  object LpfDir extends Newtype[String, Pure]
  type Manifest = Manifest.T
  object Manifest extends Newtype[String, Pure]
  type Batch = Batch.T
  object Batch extends Newtype[Int, Pure]
  type MaxLpfMb = MaxLpfMb.T
  object MaxLpfMb extends Newtype[Int, Pure]
  type DownloadTimeoutSeconds = DownloadTimeoutSeconds.T
  object DownloadTimeoutSeconds extends Newtype[Int, Pure]
  type OpenStartWindowYears = OpenStartWindowYears.T
  object OpenStartWindowYears extends Newtype[Int, Pure]
  type PointAreaKinds = PointAreaKinds.T
  object PointAreaKinds extends Newtype[String, Pure]
  type PointAreaFixBatch = PointAreaFixBatch.T
  object PointAreaFixBatch extends Newtype[Int, Pure]
  type PointAreaFixSeconds = PointAreaFixSeconds.T
  object PointAreaFixSeconds extends Newtype[Int, Pure]
