package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** First-run data (#720). With `auto`, a starting server imports the bundled gazetteer (`<dataDir>/places`) and period
  * presets (`<dataDir>/periods`) into whichever of those tables is EMPTY, as a background job; `histmersion init` runs
  * the same on demand, and `--fetch` adds the network sources in `fetchSources` (comma-separated: gazetteer importers,
  * `land`, `periodo`).
  */
final case class InitConfig(auto: InitConfig.Auto, dataDir: InitConfig.DataDir, fetchSources: InitConfig.FetchSources) derives ConfigReader:
  def sources: List[com.histmersion.config.InitConfig.FetchSource] = Option(fetchSources.value).toList.flatMap(_.split(",").toList).map(_.trim).filter(_.nonEmpty).distinct.map(InitConfig.FetchSource(_))

object InitConfig:
  type FetchSource = FetchSource.T
  object FetchSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Auto = Auto.T
  object Auto extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DataDir = DataDir.T
  object DataDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchSources = FetchSources.T
  object FetchSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
