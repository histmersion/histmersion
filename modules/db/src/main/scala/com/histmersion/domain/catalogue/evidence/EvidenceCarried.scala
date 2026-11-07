package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.given
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{BundleEntry, CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*

/** What a bundle carries of the evidence: the attestations (after the bodies they point into, this collection's own
  * moved first where a merge kept another body), the sources that possibly discuss an event and when they were last
  * computed.
  */
object EvidenceCarried:

  private val Catalogue = DataOwner("catalogue")

  private def rows(rank: Int)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, ImportRank(rank), write)
  private def afterBlobs(rank: Int)(write: BundleImport.Writer => Unit) =
    RowsIn(ImportStage.AfterBlobs, ImportRank(rank), write)

  val attestations: CarriedKind = CarriedKind.records[com.histmersion.api.exchange.Bundle.Attestation](
    Catalogue,
    Bundle.Kind.Attestation,
    TableName("attestations"),
    List(afterBlobs(101)(EvidenceBundleRepo.attestationFixes), afterBlobs(103)(EvidenceBundleRepo.attestationsIn))
  )(scope => EvidenceBundleRepo.attestations(scope).toList)(
    a => CarriedKind.RecordsClock(a.updatedAt.value),
    (es, ls) => es.modify(_.attestations).setTo(ls)
  )

  val eventCandidates: CarriedKind = CarriedKind.owned[com.histmersion.api.exchange.Bundle.EventCandidate](
    Catalogue,
    BundleEntry(com.histmersion.api.exchange.Bundle.Files.EventCandidates),
    List(rows(61)(EvidenceBundleRepo.candidatesIn))
  )(scope => EvidenceBundleRepo.eventCandidates(scope).toList)(
    c => Some(CarriedKind.OwnedClock(c.computedAt.value)),
    (es, ls) => es.modify(_.eventCandidates).setTo(ls)
  )

  val eventChecks: CarriedKind = CarriedKind.owned[com.histmersion.api.exchange.Bundle.EventCheck](
    Catalogue,
    BundleEntry(com.histmersion.api.exchange.Bundle.Files.EventChecks),
    List(rows(62)(EvidenceBundleRepo.checksIn))
  )(scope => EvidenceBundleRepo.eventChecks(scope).toList)(
    c => Some(CarriedKind.OwnedClock(c.checkedAt.value)),
    (es, ls) => es.modify(_.eventChecks).setTo(ls)
  )

  /** Every kind of the evidence. */
  val all: List[CarriedKind] = List(attestations, eventCandidates, eventChecks)
