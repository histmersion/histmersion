package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** First-run data (#720). With `auto`, a starting server imports the bundled gazetteer (`<dataDir>/places`) and period
  * presets (`<dataDir>/periods`) into whichever of those tables is EMPTY, as a background job; `histmersion init` runs
  * the same on demand, and `--fetch` adds the network sources in `fetchSources` (comma-separated: gazetteer importers,
  * `land`, `periodo`).
  */
final case class InitConfig(auto: InitConfig.Auto, dataDir: InitConfig.DataDir, fetchSources: InitConfig.FetchSources) derives ConfigReader:
  def sources: List[InitConfig.FetchSource] = Option(fetchSources.value).toList.flatMap(_.split(",").toList).map(_.trim).filter(_.nonEmpty).distinct.map(InitConfig.FetchSource(_))

object InitConfig:
  type FetchSource = FetchSource.T
  object FetchSource extends Newtype[String, Pure]

  type Auto = Auto.T
  object Auto extends Newtype[Boolean, Pure]
  type DataDir = DataDir.T
  object DataDir extends Newtype[String, Pure]
  type FetchSources = FetchSources.T
  object FetchSources extends Newtype[String, Pure]
