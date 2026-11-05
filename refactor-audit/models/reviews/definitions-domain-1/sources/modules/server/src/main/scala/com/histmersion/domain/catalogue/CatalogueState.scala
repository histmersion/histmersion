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
    seedVersions: Map[CatalogueState.SeedVersionsKey, CatalogueState.SeedVersionsValue],
    attestations: Vector[Attestation],
    lastAttestationId: CatalogueState.LastAttestationId,
    events: Vector[HeldEvent] = Vector.empty,
    eventAliases: Map[CatalogueState.EventAliasesKey, CatalogueState.EventAliasesValue] = Map.empty,
    corrections: Vector[Correction] = Vector.empty,
    lastCorrectionId: CatalogueState.LastCorrectionId = 0L,
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
  object SeedVersionsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedVersionsValue = SeedVersionsValue.T
  object SeedVersionsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastAttestationId = LastAttestationId.T
  object LastAttestationId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventAliasesKey = EventAliasesKey.T
  object EventAliasesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventAliasesValue = EventAliasesValue.T
  object EventAliasesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastCorrectionId = LastCorrectionId.T
  object LastCorrectionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CatalogueRecordsPart1 = CatalogueRecordsPart1.T
  object CatalogueRecordsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPartsKey = ImportPartsKey.T
  object ImportPartsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPartsValueKey = ImportPartsValueKey.T
  object ImportPartsValueKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPartsValueValue = ImportPartsValueValue.T
  object ImportPartsValueValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Checked = Checked.T
  object Checked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceAliasesKey = PlaceAliasesKey.T
  object PlaceAliasesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceAliasesValue = PlaceAliasesValue.T
  object PlaceAliasesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceRemovals = PlaceRemovals.T
  object PlaceRemovals extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val empty: CatalogueState = CatalogueState(Vector.empty, Map.empty, Vector.empty, 0L)
