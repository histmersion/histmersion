package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** Retraction Watch: the CSV Crossref publishes, mirrored into the `retraction_watch` table. */
final case class RetractionWatchConfig(csvUrl: RetractionWatchConfig.CsvUrl) derives ConfigReader

object RetractionWatchConfig:
  type CsvUrl = CsvUrl.T
  object CsvUrl extends Newtype[String, Pure]
