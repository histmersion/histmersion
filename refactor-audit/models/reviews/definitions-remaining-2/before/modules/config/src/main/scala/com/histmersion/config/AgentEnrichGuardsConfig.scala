package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The findings step's guards against the failure kinds a smaller extractor showed (#1424): an answer whose places
  * mostly do not resolve in the gazetteer — fewer than `minPlacedShare` (0..1) of the findings that name a place — is
  * asked again; a year below 100 is taken for a century stored as a year when the finding mentions a century, or when
  * the source's own period starts at least `centuryGapYears` later; and a claim containing one of `pointerPhrases`
  * (comma-separated, matched as whole words, ignoring case) points at a table instead of standing alone. Each is sent
  * back while attempts remain and kept with a warning on the last one ([[EnrichSource.FindingGuards]]).
  */
final case class AgentEnrichGuardsConfig(minPlacedShare: Double, centuryGapYears: Int, pointerPhrases: String)
    derives ConfigReader:
  def phrases: List[String] = PeriodsConfig.split(pointerPhrases).map(_.toLowerCase(java.util.Locale.ROOT)).distinct
