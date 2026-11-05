package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The findings step's guards against the failure kinds a smaller extractor showed (#1424): an answer whose places
  * mostly do not resolve in the gazetteer — fewer than `minPlacedShare` (0..1) of the findings that name a place — is
  * asked again; a year below 100 is taken for a century stored as a year when the finding mentions a century, or when
  * the source's own period starts at least `centuryGapYears` later; and a claim containing one of `pointerPhrases`
  * (comma-separated, matched as whole words, ignoring case) points at a table instead of standing alone. Each is sent
  * back while attempts remain and kept with a warning on the last one ([[EnrichSource.FindingGuards]]).
  */
final case class AgentEnrichGuardsConfig(minPlacedShare: AgentEnrichGuardsConfig.MinPlacedShare, centuryGapYears: AgentEnrichGuardsConfig.CenturyGapYears, pointerPhrases: AgentEnrichGuardsConfig.PointerPhrases)
    derives ConfigReader:
  def phrases: List[String] = PeriodsConfig.split(pointerPhrases).map(_.toLowerCase(java.util.Locale.ROOT)).distinct

object AgentEnrichGuardsConfig:
  type MinPlacedShare = MinPlacedShare.T
  object MinPlacedShare extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CenturyGapYears = CenturyGapYears.T
  object CenturyGapYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointerPhrases = PointerPhrases.T
  object PointerPhrases extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
