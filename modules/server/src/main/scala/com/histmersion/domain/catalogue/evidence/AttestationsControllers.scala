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

  private def refId(targetType: com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetType, id: com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetId): com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetId =
    AliasKinds.ofTarget(targetType).fold(id)(current(_, id))

  def list(
      citekey: Option[com.histmersion.domain.library.Citekey],
      targetType: Option[com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetType],
      targetId: Option[com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetId],
      limit: Option[com.histmersion.PageSize],
      offset: Option[com.histmersion.Offset]
  ): Attestation.List =
    val target = for t <- targetType; i <- targetId yield refId(t, i)
    attestations.list(
      citekey.map(current(AliasKinds.SourceRecords, _)),
      targetType,
      target.orElse(targetId),
      page(limit),
      offset.getOrElse(0)
    )

  def get(id: com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.Id): Either[Api.Error, Attestation] =
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

  def patch(caller: Caller, id: com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.Id, p: Attestation.Patch): Either[Api.Error, Attestation] =
    maintenance.guard("patch_attestation")(attestations.patch(id, p))

  def delete(caller: Caller, id: com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.Id): Either[Api.Error, Attestation.Deleted] =
    maintenance.guard("delete_attestation"):
      if attestations.delete(id) then Right(Attestation.Deleted(id, true))
      else Left(Api.Error(s"No attestation $id", Some(Write.CodeNotFound)))

object AttestationsControllers:
  type Alias = Alias.T
  object Alias extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentArg2 = com.histmersion.domain.catalogue.evidence.AttestationsControllers.Alias
  val CurrentArg2: _root_.com.histmersion.domain.catalogue.evidence.AttestationsControllers.Alias.type = _root_.com.histmersion.domain.catalogue.evidence.AttestationsControllers.Alias
  type CurrentResult = com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetId
  val CurrentResult: _root_.com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetId.type = _root_.com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetId
  type PageArg1 = com.histmersion.Offset
  val PageArg1: _root_.com.histmersion.Offset.type = _root_.com.histmersion.Offset
  type PageResult = com.histmersion.Offset
  val PageResult: _root_.com.histmersion.Offset.type = _root_.com.histmersion.Offset

