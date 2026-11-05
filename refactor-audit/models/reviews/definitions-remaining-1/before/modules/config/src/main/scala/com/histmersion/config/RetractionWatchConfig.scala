package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Retraction Watch: the CSV Crossref publishes, mirrored into the `retraction_watch` table. */
final case class RetractionWatchConfig(csvUrl: String) derives ConfigReader
