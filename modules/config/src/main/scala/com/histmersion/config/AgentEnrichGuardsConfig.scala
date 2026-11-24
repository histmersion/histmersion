package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** The findings step's guards against the failure kinds a smaller extractor showed (#1424): an answer whose places
  * mostly do not resolve in the gazetteer — fewer than `minPlacedShare` (0..1) of the findings that name a place — is
  * asked again; a year below 100 is taken for a century stored as a year when the finding mentions a century, or when
  * the source's own period starts at least `centuryGapYears` later; and a claim containing one of `pointerPhrases`
  * (comma-separated, matched as whole words, ignoring case) points at a table instead of standing alone. Each is sent
  * back while attempts remain and kept with a warning on the last one ([[EnrichSource.FindingGuards]]).
  */
@movesTo(FeaturePlace.LibraryWorkflows)
final case class AgentEnrichGuardsConfig(
    minPlacedShare: AgentEnrichGuardsConfig.MinPlacedShare,
    centuryGapYears: AgentEnrichGuardsConfig.CenturyGapYears,
    pointerPhrases: AgentEnrichGuardsConfig.PointerPhrases
) derives ConfigReader:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def phrases: List[AgentEnrichGuardsConfig.PointerPhrase] =
    Option(pointerPhrases).toList
      .flatMap(_.value.split(",").toList)
      .map(_.trim)
      .filter(_.nonEmpty)
      .distinct
      .map(_.toLowerCase(java.util.Locale.ROOT))
      .distinct
      .map(AgentEnrichGuardsConfig.PointerPhrase(_))

object AgentEnrichGuardsConfig:
  type PointerPhrase = PointerPhrase.T
  object PointerPhrase extends Newtype[String, Pure]

  type MinPlacedShare = MinPlacedShare.T
  object MinPlacedShare extends Newtype[Double, Pure]
  type CenturyGapYears = CenturyGapYears.T
  object CenturyGapYears extends Newtype[Int, Pure]
  type PointerPhrases = PointerPhrases.T
  object PointerPhrases extends Newtype[String, Pure]
