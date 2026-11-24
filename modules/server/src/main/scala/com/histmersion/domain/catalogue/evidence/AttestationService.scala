package com.histmersion
package domain.catalogue.evidence
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.Write
import com.histmersion.api.catalogue.Attestation
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.Api
import com.histmersion.domain.library.Citekey
import com.histmersion.api.RowCount
import com.histmersion.api.personal.UnreadPage
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator

/** What attests a data point: an attestation names a source, a target (a finding, an event, an event's dating, a place,
  * an edge) and where in the source it is said (quote, offsets, page, IIIF region, CTS URN).
  */
trait AttestationService:
  /** Those of a source, a target kind and a target id (each optional), by id, one page of them. */
  def list(
      citekey: Option[Citekey],
      targetType: Option[Attestation.TargetType],
      targetId: Option[Attestation.TargetId],
      limit: PageSize,
      offset: Offset
  ): Attestation.List

  def get(id: Attestation.Id): Option[Attestation]

  /** A new attestation, with its id; the citekey and target are stored as given (checked by the caller). */
  def create(in: Attestation.Put)(using WritePermit)(using Creator.New): Attestation

  /** Change the fields `p` names; the others stay. */
  def patch(id: Attestation.Id, p: Attestation.Patch)(using WritePermit): Either[Api.Error, Attestation]

  def delete(id: Attestation.Id)(using WritePermit): Boolean

object AttestationService:

  def apply(db: Transactor): AttestationService = Implementation(db)

  private def missing(id: Attestation.Id) =
    Api.Error(Problem.Unclassified(Legacy.Text(s"No attestation $id")), Some(Api.Error.Code(Write.CodeNotFound)))

  final private class Implementation(db: Transactor) extends AttestationService:
    def list(
        citekey: Option[Citekey],
        targetType: Option[Attestation.TargetType],
        targetId: Option[Attestation.TargetId],
        limit: PageSize,
        offset: Offset
    ): Attestation.List = db.read(
      AttestationRepo.list(
        citekey,
        targetType.map(t => BAttestationFullRow.TargetType(t.value)),
        targetId.map(t => BAttestationFullRow.TargetId(t.value)),
        limit,
        offset
      )
    )
    def get(id: Attestation.Id): Option[Attestation] =
      db.read(AttestationRepo.get(AttestationRepo.AttestationId(id.value)))
    def create(in: Attestation.Put)(using WritePermit)(using Creator.New): Attestation =
      db.write(AttestationRepo.create(in))
    def patch(id: Attestation.Id, p: Attestation.Patch)(using WritePermit): Either[Api.Error, Attestation] =
      db.write:
        if !AttestationRepo.exists(AttestationRepo.AttestationId(id.value)) then Left(missing(id))
        else
          AttestationRepo.update(AttestationRepo.AttestationId(id.value), p)
          Right(AttestationRepo.get(AttestationRepo.AttestationId(id.value)).get)
    def delete(id: Attestation.Id)(using WritePermit): Boolean =
      db.write(AttestationRepo.delete(AttestationRepo.AttestationId(id.value)))

  /** The same rules over the shared in-memory state; ids are given in order, as the database's sequence gives them. */
  final class InMemory(state: AtomicReference[CatalogueState]) extends AttestationService:
    def list(
        citekey: Option[Citekey],
        targetType: Option[Attestation.TargetType],
        targetId: Option[Attestation.TargetId],
        limit: PageSize,
        offset: Offset
    ): Attestation.List =
      def named(o: Option[String]) = o.map(_.trim).filter(_.nonEmpty)
      val (ck, tt, ti) = (named(citekey.map(_.value)), named(targetType.map(_.value)), named(targetId.map(_.value)))
      val all = state.get.attestations.filter(a =>
        ck.forall(_ == a.citekey.value) && tt.forall(_ == a.targetType.value) && ti.forall(_ == a.targetId.value)
      )
      val from = offset.value.max(0)
      Attestation.List.of(
        RowCount(all.size),
        all.sortBy(a => a.id.value: Long).slice(from, from + limit.value.max(1)).toList,
        UnreadPage.Offset(from)
      )

    def get(id: Attestation.Id): Option[Attestation] = state.get.attestations.find(_.id == id)

    def create(in: Attestation.Put)(using WritePermit)(using Creator.New): Attestation =
      val after = state.updateAndGet { s =>
        val id = s.lastAttestationId.value + 1
        val a = Attestation(
          Attestation.Id(id),
          in.citekey,
          in.targetType,
          in.targetId,
          in.quote,
          in.prefix,
          in.suffix,
          in.charStart,
          in.charEnd,
          in.chunkOrd,
          in.page,
          in.canvas,
          in.xywh,
          in.ctsUrn,
          in.note
        )
        s.modify(_.attestations).using(_ :+ a).modify(_.lastAttestationId).setTo(CatalogueState.LastAttestationId(id))
      }
      after.attestations.last

    def patch(id: Attestation.Id, p: Attestation.Patch)(using WritePermit): Either[Api.Error, Attestation] =
      if get(id).isEmpty then Left(missing(id))
      else
        def patched(a: Attestation): Attestation =
          a.modify(_.quote)
            .using(p.quote.orElse(_))
            .modify(_.prefix)
            .using(p.prefix.orElse(_))
            .modify(_.suffix)
            .using(p.suffix.orElse(_))
            .modify(_.charStart)
            .using(p.charStart.orElse(_))
            .modify(_.charEnd)
            .using(p.charEnd.orElse(_))
            .modify(_.chunkOrd)
            .using(p.chunkOrd.orElse(_))
            .modify(_.page)
            .using(p.page.orElse(_))
            .modify(_.canvas)
            .using(p.canvas.orElse(_))
            .modify(_.xywh)
            .using(p.xywh.orElse(_))
            .modify(_.ctsUrn)
            .using(p.ctsUrn.orElse(_))
            .modify(_.note)
            .using(p.note.orElse(_))
        val after = state.updateAndGet(_.modify(_.attestations).using(_.map(a => if a.id == id then patched(a) else a)))
        Right(after.attestations.find(_.id == id).get)

    def delete(id: Attestation.Id)(using WritePermit): Boolean =
      val had = get(id).isDefined
      val _ = state.updateAndGet(_.modify(_.attestations).using(_.filterNot(_.id == id)))
      had
