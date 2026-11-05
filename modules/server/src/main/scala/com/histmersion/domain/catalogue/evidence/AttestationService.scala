package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.Write
import com.histmersion.api.catalogue.Attestation
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.Api

/** What attests a data point: an attestation names a source, a target (a finding, an event, an event's dating, a place,
  * an edge) and where in the source it is said (quote, offsets, page, IIIF region, CTS URN).
  */
trait AttestationService:
  /** Those of a source, a target kind and a target id (each optional), by id, one page of them. */
  def list(
      citekey: Option[com.histmersion.domain.library.Citekey],
      targetType: Option[com.histmersion.domain.catalogue.evidence.AttestationService.ListTargetType],
      targetId: Option[com.histmersion.domain.catalogue.evidence.AttestationService.ListTargetId],
      limit: com.histmersion.domain.catalogue.evidence.AttestationService.ListLimit,
      offset: com.histmersion.domain.catalogue.evidence.AttestationService.ListOffset
  ): Attestation.List

  def get(id: com.histmersion.domain.catalogue.evidence.AttestationService.GetId): Option[Attestation]

  /** A new attestation, with its id; the citekey and target are stored as given (checked by the caller). */
  def create(in: Attestation.Put): Attestation

  /** Change the fields `p` names; the others stay. */
  def patch(id: com.histmersion.domain.catalogue.evidence.AttestationService.PatchId, p: Attestation.Patch): Either[Api.Error, Attestation]

  def delete(id: com.histmersion.domain.catalogue.evidence.AttestationService.DeleteId): com.histmersion.domain.catalogue.evidence.AttestationService.DeleteResult

object AttestationService:
  type ListTargetType = ListTargetType.T
  object ListTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListTargetId = ListTargetId.T
  object ListTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListLimit = ListLimit.T
  object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListOffset = ListOffset.T
  object ListOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetId = GetId.T
  object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchId = PatchId.T
  object PatchId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteResult = DeleteResult.T
  object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteId = DeleteId.T
  object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MissingId = MissingId.T
  object MissingId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): AttestationService = Implementation(db)

  private def missing(id: com.histmersion.domain.catalogue.evidence.AttestationService.MissingId) = Api.Error(s"No attestation $id", Some(Write.CodeNotFound))

  final private class Implementation(db: Transactor) extends AttestationService:
    def list(
        citekey: Option[com.histmersion.domain.library.Citekey],
        targetType: Option[com.histmersion.domain.catalogue.evidence.AttestationService.Implementation.ListTargetType],
        targetId: Option[com.histmersion.domain.catalogue.evidence.AttestationService.Implementation.ListTargetId],
        limit: com.histmersion.domain.catalogue.evidence.AttestationService.Implementation.ListLimit,
        offset: com.histmersion.domain.catalogue.evidence.AttestationService.Implementation.ListOffset
    ): Attestation.List = db.read(AttestationRepo.list(citekey, targetType, targetId, limit, offset))
    def get(id: com.histmersion.domain.catalogue.evidence.AttestationService.Implementation.GetId): Option[Attestation] = db.read(AttestationRepo.get(id))
    def create(in: Attestation.Put): Attestation = db.transact(AttestationRepo.create(in))
    def patch(id: com.histmersion.domain.catalogue.evidence.AttestationService.Implementation.PatchId, p: Attestation.Patch): Either[Api.Error, Attestation] =
      db.transact:
        if !AttestationRepo.exists(id) then Left(missing(id))
        else
          AttestationRepo.update(id, p)
          Right(AttestationRepo.get(id).get)
    def delete(id: com.histmersion.domain.catalogue.evidence.AttestationService.Implementation.DeleteId): com.histmersion.domain.catalogue.evidence.AttestationService.Implementation.DeleteResult = db.transact(AttestationRepo.delete(id))

  object Implementation:
    type ListTargetType = ListTargetType.T
    object ListTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListTargetId = ListTargetId.T
    object ListTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListLimit = ListLimit.T
    object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListOffset = ListOffset.T
    object ListOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetId = GetId.T
    object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchId = PatchId.T
    object PatchId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteResult = DeleteResult.T
    object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteId = DeleteId.T
    object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The same rules over the shared in-memory state; ids are given in order, as the database's sequence gives them. */
  final class InMemory(state: AtomicReference[CatalogueState]) extends AttestationService:
    def list(
        citekey: Option[com.histmersion.domain.library.Citekey],
        targetType: Option[com.histmersion.domain.catalogue.evidence.AttestationService.InMemory.ListTargetType],
        targetId: Option[com.histmersion.domain.catalogue.evidence.AttestationService.InMemory.ListTargetId],
        limit: com.histmersion.domain.catalogue.evidence.AttestationService.InMemory.ListLimit,
        offset: com.histmersion.domain.catalogue.evidence.AttestationService.InMemory.ListOffset
    ): Attestation.List =
      def named(o: Option[String]) = o.map(_.trim).filter(_.nonEmpty)
      val (ck, tt, ti) = (named(citekey), named(targetType), named(targetId))
      val all = state.get.attestations.filter(a =>
        ck.forall(_ == a.citekey) && tt.forall(_ == a.targetType) && ti.forall(_ == a.targetId)
      )
      val from = offset.max(0)
      Attestation.List.of(all.size, all.sortBy(_.id).slice(from, from + limit.max(1)).toList, from)

    def get(id: com.histmersion.domain.catalogue.evidence.AttestationService.InMemory.GetId): Option[Attestation] = state.get.attestations.find(_.id == id)

    def create(in: Attestation.Put): Attestation =
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

    def patch(id: com.histmersion.domain.catalogue.evidence.AttestationService.InMemory.PatchId, p: Attestation.Patch): Either[Api.Error, Attestation] =
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

    def delete(id: com.histmersion.domain.catalogue.evidence.AttestationService.InMemory.DeleteId): com.histmersion.domain.catalogue.evidence.AttestationService.InMemory.DeleteResult =
      val had = get(id).isDefined
      val _ = state.updateAndGet(_.modify(_.attestations).using(_.filterNot(_.id == id)))
      had

  object InMemory:
    type ListTargetType = ListTargetType.T
    object ListTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListTargetId = ListTargetId.T
    object ListTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListLimit = ListLimit.T
    object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListOffset = ListOffset.T
    object ListOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetId = GetId.T
    object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchId = PatchId.T
    object PatchId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteResult = DeleteResult.T
    object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteId = DeleteId.T
    object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
