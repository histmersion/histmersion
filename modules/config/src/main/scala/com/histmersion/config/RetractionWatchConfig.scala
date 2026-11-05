package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Retraction Watch: the CSV Crossref publishes, mirrored into the `retraction_watch` table. */
final case class RetractionWatchConfig(csvUrl: RetractionWatchConfig.CsvUrl) derives ConfigReader

object RetractionWatchConfig:
  type CsvUrl = CsvUrl.T
  object CsvUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
