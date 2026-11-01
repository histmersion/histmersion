package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.{ApiError, Attestation, AttestationList, AttestationPatch, AttestationPut, Write}
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference

/** What attests a data point: an attestation names a source, a target (a finding, an event, an event's dating, a place,
  * an edge) and where in the source it is said (quote, offsets, page, IIIF region, CTS URN).
  */
trait AttestationService:
  /** Those of a source, a target kind and a target id (each optional), by id, one page of them. */
  def list(
      citekey: Option[String],
      targetType: Option[String],
      targetId: Option[String],
      limit: Int,
      offset: Int
  ): AttestationList

  def get(id: Long): Option[Attestation]

  /** A new attestation, with its id; the citekey and target are stored as given (checked by the caller). */
  def create(in: AttestationPut): Attestation

  /** Change the fields `p` names; the others stay. */
  def patch(id: Long, p: AttestationPatch): Either[ApiError, Attestation]

  def delete(id: Long): Boolean

object AttestationService:
  def apply(db: Transactor): AttestationService = Implementation(db)

  private def missing(id: Long) = ApiError(s"No attestation $id", Some(Write.CodeNotFound))

  final private class Implementation(db: Transactor) extends AttestationService:
    def list(
        citekey: Option[String],
        targetType: Option[String],
        targetId: Option[String],
        limit: Int,
        offset: Int
    ): AttestationList = db.read(AttestationRepo.list(citekey, targetType, targetId, limit, offset))
    def get(id: Long): Option[Attestation] = db.read(AttestationRepo.get(id))
    def create(in: AttestationPut): Attestation = db.transact(AttestationRepo.create(in))
    def patch(id: Long, p: AttestationPatch): Either[ApiError, Attestation] =
      db.transact:
        if !AttestationRepo.exists(id) then Left(missing(id))
        else
          AttestationRepo.update(id, p)
          Right(AttestationRepo.get(id).get)
    def delete(id: Long): Boolean = db.transact(AttestationRepo.delete(id))

  /** The same rules over the shared in-memory state; ids are given in order, as the database's sequence gives them. */
  final class InMemory(state: AtomicReference[CatalogueState]) extends AttestationService:
    def list(
        citekey: Option[String],
        targetType: Option[String],
        targetId: Option[String],
        limit: Int,
        offset: Int
    ): AttestationList =
      def named(o: Option[String]) = o.map(_.trim).filter(_.nonEmpty)
      val (ck, tt, ti) = (named(citekey), named(targetType), named(targetId))
      val all = state.get.attestations.filter(a =>
        ck.forall(_ == a.citekey) && tt.forall(_ == a.targetType) && ti.forall(_ == a.targetId)
      )
      val from = offset.max(0)
      AttestationList.of(all.size, all.sortBy(_.id).slice(from, from + limit.max(1)).toList, from)

    def get(id: Long): Option[Attestation] = state.get.attestations.find(_.id == id)

    def create(in: AttestationPut): Attestation =
      val after = state.updateAndGet { s =>
        val id = s.lastAttestationId + 1
        val a = Attestation(
          id,
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
        s.modify(_.attestations).using(_ :+ a).modify(_.lastAttestationId).setTo(id)
      }
      after.attestations.last

    def patch(id: Long, p: AttestationPatch): Either[ApiError, Attestation] =
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

    def delete(id: Long): Boolean =
      val had = get(id).isDefined
      val _ = state.updateAndGet(_.modify(_.attestations).using(_.filterNot(_.id == id)))
      had
