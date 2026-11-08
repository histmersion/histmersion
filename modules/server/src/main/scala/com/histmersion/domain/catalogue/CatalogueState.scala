package com.histmersion
package domain.catalogue

import com.histmersion.api.catalogue.{Attestation, CatalogRecord, Correction, EventLinkCandidate}
import com.histmersion.domain.catalogue.events.HeldEvent
import com.histmersion.domain.catalogue.periods.StoredPeriod
import com.histmersion.domain.catalogue.places.{HeldOverride, HeldPlace, HeldSite}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The catalogue's rows held in memory, one record the `InMemory` services of its features share (each reads and
  * changes its own part, through lenses): the named periods and the bundled seeds' versions; the attestations and the
  * last id given one; the events, the old ids of renamed ones, the corrections by hand and the last id given one, the
  * catalogues' records by event and the parts of each catalogue read; the events' proposed links and the events whose
  * links were computed; the Wikidata sites whose type is refined; the gazetteer's places, the old ids of renamed and
  * merged ones, the kinds and periods a person set and the ids a person removed.
  */
final case class CatalogueState(
    periods: Vector[StoredPeriod],
    seedVersions: Map[CatalogueState.SeedVersionsKey, CatalogueState.SeedVersionsValue],
    attestations: Vector[Attestation],
    lastAttestationId: CatalogueState.LastAttestationId,
    events: Vector[HeldEvent] = Vector.empty,
    eventAliases: Map[CatalogueState.EventAliasesKey, CatalogueState.EventAliasesValue] = Map.empty,
    corrections: Vector[Correction] = Vector.empty,
    lastCorrectionId: CatalogueState.LastCorrectionId = CatalogueState.LastCorrectionId(0L),
    catalogueRecords: Vector[(CatalogueState.CatalogueRecordsPart1, CatalogRecord)] = Vector.empty,
    importParts: Map[CatalogueState.ImportPartsKey, Map[CatalogueState.ImportPartsValueKey, CatalogueState.ImportPartsValueValue]] = Map.empty,
    candidates: Vector[EventLinkCandidate] = Vector.empty,
    checked: Set[CatalogueState.Checked] = Set.empty,
    sites: Vector[HeldSite] = Vector.empty,
    places: Vector[HeldPlace] = Vector.empty,
    placeAliases: Map[CatalogueState.PlaceAliasesKey, CatalogueState.PlaceAliasesValue] = Map.empty,
    placeOverrides: Vector[HeldOverride] = Vector.empty,
    placeRemovals: Set[CatalogueState.PlaceRemovals] = Set.empty
)

object CatalogueState:
  type SeedVersionsKey = SeedVersionsKey.T
  object SeedVersionsKey extends RefinedType[String, Pure]
  type SeedVersionsValue = SeedVersionsValue.T
  object SeedVersionsValue extends RefinedType[Int, Pure]
  type LastAttestationId = LastAttestationId.T
  object LastAttestationId extends RefinedType[Long, Pure]
  type EventAliasesKey = EventAliasesKey.T
  object EventAliasesKey extends RefinedType[String, Pure]
  type EventAliasesValue = EventAliasesValue.T
  object EventAliasesValue extends RefinedType[String, Pure]
  type LastCorrectionId = LastCorrectionId.T
  object LastCorrectionId extends RefinedType[Long, Pure]
  type CatalogueRecordsPart1 = CatalogueRecordsPart1.T
  object CatalogueRecordsPart1 extends RefinedType[String, Pure]
  type ImportPartsKey = ImportPartsKey.T
  object ImportPartsKey extends RefinedType[String, Pure]
  type ImportPartsValueKey = ImportPartsValueKey.T
  object ImportPartsValueKey extends RefinedType[String, Pure]
  type ImportPartsValueValue = ImportPartsValueValue.T
  object ImportPartsValueValue extends RefinedType[Int, Pure]
  type Checked = Checked.T
  object Checked extends RefinedType[String, Pure]
  type PlaceAliasesKey = PlaceAliasesKey.T
  object PlaceAliasesKey extends RefinedType[String, Pure]
  type PlaceAliasesValue = PlaceAliasesValue.T
  object PlaceAliasesValue extends RefinedType[String, Pure]
  type PlaceRemovals = PlaceRemovals.T
  object PlaceRemovals extends RefinedType[String, Pure]

  val empty: CatalogueState = CatalogueState(Vector.empty, Map.empty, Vector.empty, LastAttestationId(0L))
