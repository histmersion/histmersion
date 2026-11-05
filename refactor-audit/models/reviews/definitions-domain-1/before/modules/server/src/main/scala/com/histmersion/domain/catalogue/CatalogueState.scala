package com.histmersion.domain.catalogue

import com.histmersion.api.catalogue.{Attestation, CatalogRecord, Correction, EventLinkCandidate}
import com.histmersion.domain.catalogue.events.HeldEvent
import com.histmersion.domain.catalogue.periods.StoredPeriod
import com.histmersion.domain.catalogue.places.{HeldOverride, HeldPlace, HeldSite}

/** The catalogue's rows held in memory, one record the `InMemory` services of its features share (each reads and
  * changes its own part, through lenses): the named periods and the bundled seeds' versions; the attestations and the
  * last id given one; the events, the old ids of renamed ones, the corrections by hand and the last id given one, the
  * catalogues' records by event and the parts of each catalogue read; the events' proposed links and the events whose
  * links were computed; the Wikidata sites whose type is refined; the gazetteer's places, the old ids of renamed and
  * merged ones, the kinds and periods a person set and the ids a person removed.
  */
final case class CatalogueState(
    periods: Vector[StoredPeriod],
    seedVersions: Map[String, Int],
    attestations: Vector[Attestation],
    lastAttestationId: Long,
    events: Vector[HeldEvent] = Vector.empty,
    eventAliases: Map[String, String] = Map.empty,
    corrections: Vector[Correction] = Vector.empty,
    lastCorrectionId: Long = 0L,
    catalogueRecords: Vector[(String, CatalogRecord)] = Vector.empty,
    importParts: Map[String, Map[String, Int]] = Map.empty,
    candidates: Vector[EventLinkCandidate] = Vector.empty,
    checked: Set[String] = Set.empty,
    sites: Vector[HeldSite] = Vector.empty,
    places: Vector[HeldPlace] = Vector.empty,
    placeAliases: Map[String, String] = Map.empty,
    placeOverrides: Vector[HeldOverride] = Vector.empty,
    placeRemovals: Set[String] = Set.empty
)

object CatalogueState:
  val empty: CatalogueState = CatalogueState(Vector.empty, Map.empty, Vector.empty, 0L)
