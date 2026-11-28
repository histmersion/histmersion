package com.histmersion
package domain.catalogue

import com.histmersion.api.catalogue.{Attestation, CatalogRecord, Correction, EventLinkCandidate}
import com.histmersion.domain.catalogue.events.HeldEvent
import com.histmersion.domain.catalogue.periods.StoredPeriod
import com.histmersion.domain.catalogue.places.{HeldOverride, HeldPlace, HeldSite}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.catalogue.Place

/** The catalogue's rows held in memory, one record the `InMemory` services of its features share (each reads and
  * changes its own part, through lenses): the named periods and the bundled seeds' versions; the attestations and the
  * last id given one; the events, the old ids of renamed ones, the corrections by hand and the last id given one, the
  * catalogues' records by event and the parts of each catalogue read; the events' proposed links and the events whose
  * links were computed; the Wikidata sites whose type is refined; the gazetteer's places, the old ids of renamed and
  * merged ones, the kinds and periods a person set and the ids a person removed.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class CatalogueState(
    periods: Vector[StoredPeriod],
    seedVersions: Map[CatalogueState.SeedVersionsKey, CatalogueState.SeedVersionsValue],
    attestations: Vector[Attestation],
    lastAttestationId: CatalogueState.LastAttestationId,
    events: Vector[HeldEvent] = Vector.empty,
    eventAliases: Map[CatalogueState.EventAliasesKey, CatalogueState.EventAliasesValue] = Map.empty,
    corrections: Vector[Correction] = Vector.empty,
    lastCorrectionId: CatalogueState.LastCorrectionId = CatalogueState.LastCorrectionId(0L),
    catalogueRecords: Vector[(Event.Id, CatalogRecord)] = Vector.empty,
    importParts: Map[
      CatalogueState.ImportPartsKey,
      Map[CatalogueState.ImportPartsValueKey, CatalogueState.ImportPartsValueValue]
    ] = Map.empty,
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
  object SeedVersionsKey extends Newtype[String, Pure]
  type SeedVersionsValue = SeedVersionsValue.T
  object SeedVersionsValue extends Newtype[Int, Pure]
  type LastAttestationId = LastAttestationId.T
  object LastAttestationId extends Newtype[Long, Pure]
  type EventAliasesKey = EventAliasesKey.T
  object EventAliasesKey extends Newtype[String, Pure]
  type EventAliasesValue = EventAliasesValue.T
  object EventAliasesValue extends Newtype[String, Pure]
  type LastCorrectionId = LastCorrectionId.T
  object LastCorrectionId extends Newtype[Long, Pure]
  type ImportPartsKey = ImportPartsKey.T
  object ImportPartsKey extends Newtype[String, Pure]
  type ImportPartsValueKey = ImportPartsValueKey.T
  object ImportPartsValueKey extends Newtype[String, Pure]
  type ImportPartsValueValue = ImportPartsValueValue.T
  object ImportPartsValueValue extends Newtype[Int, Pure]
  type Checked = Checked.T
  object Checked extends Newtype[String, Pure]
  export Place.Id as PlaceAliasesKey
  type PlaceAliasesValue = PlaceAliasesValue.T
  object PlaceAliasesValue extends Newtype[String, Pure]
  type PlaceRemovals = PlaceRemovals.T
  object PlaceRemovals extends Newtype[String, Pure]

  val empty: CatalogueState = CatalogueState(Vector.empty, Map.empty, Vector.empty, LastAttestationId(0L))
