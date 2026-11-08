package com.histmersion
package domain.catalogue

import com.histmersion.domain.catalogue.events.{EventMergeRules, EventsCarried}
import com.histmersion.domain.catalogue.evidence.{AttestationMergeRules, EvidenceCarried}
import com.histmersion.domain.catalogue.periods.{PeriodMergeRules, PeriodsCarried}
import com.histmersion.domain.catalogue.places.{PlaceMergeRules, PlacesCarried}
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}

/** What the catalogue carries in a bundle and how a merge treats its records: its features' kinds and rules. */
object CatalogueExchange:

  /** The places, periods, events and evidence a bundle carries. */
  val carried: List[CarriedKind] = PlacesCarried.all ++ PeriodsCarried.all ++ EventsCarried.all ++ EvidenceCarried.all

  /** How a merge treats places, periods, events and attestations. */
  val mergeRules: List[MergeRules] = List(PlaceMergeRules, PeriodMergeRules, EventMergeRules, AttestationMergeRules)
