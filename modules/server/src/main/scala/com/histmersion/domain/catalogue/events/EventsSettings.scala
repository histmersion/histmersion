package com.histmersion
package domain.catalogue.events

import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{EventSummary, EventTaxonomy}
import com.histmersion.config.{EventsConfig, EventsLinksConfig, EventsWikidataConfig}
import com.histmersion.domain.catalogue.evidence.EventMatcher
import com.histmersion.upstreams.wikidata.{WikidataClasses, WikidataItem}
import com.histmersion.lint.{LintRule, tolerated}

extension (c: EventsWikidataConfig)
  /** The configured classes and their kinds (canonical), the most specific first. */
  @tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def classList: List[(WikidataItem.Id, EventSummary.Kind)] =
    WikidataClasses
      .parse(WikidataClasses.WikidataText(c.classes.value))
      .map((q, k) => q -> EventTaxonomy.canonical(EventSummary.Kind(k.value)))
extension (c: EventsConfig)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def regionBox: Option[BBox] = BBox.parse(BBox.Text(c.region.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tolerance: CatalogMerge.Tolerance =
    CatalogMerge.Tolerance(CatalogMerge.Tolerance.Km(c.mergeKm.value), CatalogMerge.Tolerance.Years(c.mergeYears.value))
extension (c: EventsLinksConfig)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def weights: EventMatcher.Weights =
    EventMatcher.Weights(
      EventMatcher.Weights.Km(c.km.value),
      EventMatcher.Weights.Years(c.years.value),
      EventMatcher.Weights.Name(c.nameWeight.value),
      EventMatcher.Weights.Finding(c.findingWeight.value),
      EventMatcher.Weights.MinScore(c.minScore.value),
      EventMatcher.Weights.MinNameChars(c.minNameChars.value),
      EventMatcher.Weights.MaxCandidates(c.maxCandidates.value)
    )
