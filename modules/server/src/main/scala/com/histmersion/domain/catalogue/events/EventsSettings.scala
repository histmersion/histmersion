package com.histmersion.domain.catalogue.events

import com.histmersion.api.BBox
import com.histmersion.api.EventTaxonomy
import com.histmersion.config.{EventsConfig, EventsLinksConfig, EventsWikidataConfig}
import com.histmersion.domain.catalogue.evidence.EventMatcher
import com.histmersion.upstreams.wikidata.WikidataClasses

extension (c: EventsWikidataConfig)
  /** The configured classes and their kinds (canonical), the most specific first. */
  def classList: List[(String, String)] =
    WikidataClasses.parse(c.classes).map((q, k) => q -> EventTaxonomy.canonical(k))
extension (c: EventsConfig)
  def regionBox: Option[BBox] = BBox.parse(c.region)
  def tolerance: CatalogMerge.Tolerance = CatalogMerge.Tolerance(c.mergeKm, c.mergeYears)
extension (c: EventsLinksConfig)
  def weights: EventMatcher.Weights =
    EventMatcher.Weights(c.km, c.years, c.nameWeight, c.findingWeight, c.minScore, c.minNameChars, c.maxCandidates)
