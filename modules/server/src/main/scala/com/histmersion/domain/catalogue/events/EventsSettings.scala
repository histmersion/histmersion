package com.histmersion
package domain.catalogue.events

import com.histmersion.domain.catalogue.evidence.NearFinding

import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{EventSummary, EventTaxonomy}
import com.histmersion.config.{EventsConfig, EventsLinksConfig, EventsWikidataConfig}
import com.histmersion.upstreams.wikidata.{WikidataClasses, WikidataItem}
import com.histmersion.lint.{tolerated, LintRule}

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
  def tolerance: Placement.Tolerance =
    Placement.Tolerance(Placement.Tolerance.Km(c.mergeKm.value), Placement.Tolerance.Years(c.mergeYears.value))
extension (c: EventsLinksConfig)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def weights: NearFinding.Weights =
    NearFinding.Weights(
      NearFinding.Weights.Km(c.km.value),
      NearFinding.Weights.Years(c.years.value),
      NearFinding.Weights.Name(c.nameWeight.value),
      NearFinding.Weights.Finding(c.findingWeight.value),
      NearFinding.Weights.MinScore(c.minScore.value),
      NearFinding.Weights.MinNameChars(c.minNameChars.value),
      NearFinding.Weights.MaxCandidates(c.maxCandidates.value)
    )
