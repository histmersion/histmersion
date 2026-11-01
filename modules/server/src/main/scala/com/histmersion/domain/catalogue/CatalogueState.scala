package com.histmersion.domain.catalogue

import com.histmersion.api.Attestation
import com.histmersion.domain.catalogue.periods.StoredPeriod

/** The catalogue's rows held in memory, one record the `InMemory` services of its features share (each reads and
  * changes its own part, through lenses): the named periods and the bundled seeds' versions; the attestations and the
  * last id given one.
  */
final case class CatalogueState(
    periods: Vector[StoredPeriod],
    seedVersions: Map[String, Int],
    attestations: Vector[Attestation],
    lastAttestationId: Long
)

object CatalogueState:
  val empty: CatalogueState = CatalogueState(Vector.empty, Map.empty, Vector.empty, 0L)
