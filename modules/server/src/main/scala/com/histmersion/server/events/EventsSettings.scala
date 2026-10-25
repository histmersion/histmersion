package com.histmersion.server.events

import com.histmersion.api.BBox
import com.histmersion.config.{EventsConfig, EventsLinksConfig, EventsNoaaConfig, EventsWikidataConfig}
import com.histmersion.domain.{CatalogMerge, EventMatcher, EventProviders}

extension (c: EventsNoaaConfig)
  def datasetList: List[String] = c.datasets.split(",").map(_.trim).filter(EventProviders.Noaa.datasets.contains).toList
extension (c: EventsWikidataConfig)
  def classList: List[(String, String)] = EventProviders.WikidataEvents.classes(c.classes)
extension (c: EventsConfig)
  def regionBox: Option[BBox] = BBox.parse(c.region)
  def tolerance: CatalogMerge.Tolerance = CatalogMerge.Tolerance(c.mergeKm, c.mergeYears)
extension (c: EventsLinksConfig)
  def weights: EventMatcher.Weights =
    EventMatcher.Weights(c.km, c.years, c.nameWeight, c.findingWeight, c.minScore, c.minNameChars, c.maxCandidates)
