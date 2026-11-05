package com.histmersion.domain.catalogue.evidence

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.domain.catalogue.AttestationTarget

import com.histmersion.api.Write
import com.histmersion.api.catalogue.Attestation
import com.histmersion.domain.Caller
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind

import java.util.Locale
import com.histmersion.api.Api
import com.histmersion.domain.catalogue.evidence.passages.Passage

/** The attestations' operations as REST, MCP and the CLI reach them. A reference to a record of a renamed kind (an
  * event, a place, a source, an article) names it by its identifier now — `current` resolves an old one; a finding id
  * or an edge is kept as given. `page` is the page size a request's limit makes.
  */
final class AttestationsControllers(
    attestations: AttestationService,
    sources: Passage.Source,
    current: (AliasKind, AttestationsControllers.CurrentArg2) => AttestationsControllers.CurrentResult,
    page: Option[AttestationsControllers.PageArg1] => AttestationsControllers.PageResult,
    maintenance: WriteGate
):

  private def refId(targetType: com.histmersion.domain.catalogue.evidence.AttestationsControllers.RefIdTargetType, id: com.histmersion.domain.catalogue.evidence.AttestationsControllers.RefIdId): com.histmersion.domain.catalogue.evidence.AttestationsControllers.RefIdResult =
    AliasKinds.ofTarget(targetType).fold(id)(current(_, id))

  def list(
      citekey: Option[com.histmersion.domain.library.Citekey],
      targetType: Option[com.histmersion.domain.catalogue.evidence.AttestationsControllers.ListTargetType],
      targetId: Option[com.histmersion.domain.catalogue.evidence.AttestationsControllers.ListTargetId],
      limit: Option[com.histmersion.domain.catalogue.evidence.AttestationsControllers.ListLimit],
      offset: Option[com.histmersion.domain.catalogue.evidence.AttestationsControllers.ListOffset]
  ): Attestation.List =
    val target = for t <- targetType; i <- targetId yield refId(t, i)
    attestations.list(
      citekey.map(current(AliasKinds.SourceRecords, _)),
      targetType,
      target.orElse(targetId),
      page(limit),
      offset.getOrElse(0)
    )

  def get(id: com.histmersion.domain.catalogue.evidence.AttestationsControllers.GetId): Either[Api.Error, Attestation] =
    attestations.get(id).toRight(Api.Error(s"No attestation $id", Some(Write.CodeNotFound)))

  /** An unknown target type is a 400 and a missing source a 404 — checked first: the table's constraints would answer
    * both with a 500.
    */
  def create(caller: Caller, in: Attestation.Put): Either[Api.Error, Attestation] =
    maintenance.guard("create_attestation"):
      val ck = current(AliasKinds.SourceRecords, in.citekey)
      for
        t <- AttestationTarget.check(in.targetType).left.map(Api.Error(_, Some(Write.CodeInvalid)))
        _ <- Either.cond(sources.canonical(ck).isDefined, (), Write.notFound(ck))
      yield attestations.create(in.modify(_.citekey).setTo(ck).modify(_.targetType).setTo(t).modify(_.targetId).setTo(refId(t, in.targetId)))

  def patch(caller: Caller, id: com.histmersion.domain.catalogue.evidence.AttestationsControllers.PatchId, p: Attestation.Patch): Either[Api.Error, Attestation] =
    maintenance.guard("patch_attestation")(attestations.patch(id, p))

  def delete(caller: Caller, id: com.histmersion.domain.catalogue.evidence.AttestationsControllers.DeleteId): Either[Api.Error, Attestation.Deleted] =
    maintenance.guard("delete_attestation"):
      if attestations.delete(id) then Right(Attestation.Deleted(id, true))
      else Left(Api.Error(s"No attestation $id", Some(Write.CodeNotFound)))

object AttestationsControllers:
  type CurrentArg2 = CurrentArg2.T
  object CurrentArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentResult = CurrentResult.T
  object CurrentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageArg1 = PageArg1.T
  object PageArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageResult = PageResult.T
  object PageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type RefIdResult = RefIdResult.T
  object RefIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefIdTargetType = RefIdTargetType.T
  object RefIdTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefIdId = RefIdId.T
  object RefIdId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type DeleteId = DeleteId.T
  object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
