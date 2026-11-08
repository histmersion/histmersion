package com.histmersion
package domain.catalogue.events

import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{EventSummary, EventTaxonomy}
import com.histmersion.config.{EventsConfig, EventsLinksConfig, EventsWikidataConfig}
import com.histmersion.domain.catalogue.evidence.EventMatcher
import com.histmersion.upstreams.wikidata.{WikidataClasses, WikidataItem}

extension (c: EventsWikidataConfig)
  /** The configured classes and their kinds (canonical), the most specific first. */
  def classList: List[(WikidataItem.Id, EventSummary.Kind)] =
    WikidataClasses
      .parse(WikidataClasses.WikidataText(c.classes.value))
      .map((q, k) => q -> EventTaxonomy.canonical(EventSummary.Kind(k.value)))
extension (c: EventsConfig)
  def regionBox: Option[BBox] = BBox.parse(BBox.Text(c.region.value))
  def tolerance: CatalogMerge.Tolerance =
    CatalogMerge.Tolerance(CatalogMerge.Tolerance.Km(c.mergeKm.value), CatalogMerge.Tolerance.Years(c.mergeYears.value))
extension (c: EventsLinksConfig)
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
