package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}

/** Retraction Watch: the CSV Crossref publishes, mirrored into the `retraction_watch` table. */
@movesTo(FeaturePlace.LibraryAcquisition)
final case class RetractionWatchConfig(csvUrl: RetractionWatchConfig.CsvUrl) derives ConfigReader

object RetractionWatchConfig:
  type CsvUrl = CsvUrl.T
  object CsvUrl extends Newtype[String, Pure]
