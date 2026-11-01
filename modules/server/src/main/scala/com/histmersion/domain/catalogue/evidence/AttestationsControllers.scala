package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.{
  AliasKindsAdapter,
  ApiError,
  Attestation,
  AttestationDeleted,
  AttestationList,
  AttestationPatch,
  AttestationPut,
  AttestationTarget,
  Write
}
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.evidence.passages.PassageSource
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.identity.AliasKind

import java.util.Locale

/** The attestations' operations as REST, MCP and the CLI reach them. A reference to a record of a renamed kind (an
  * event, a place, a source, an article) names it by its identifier now — `current` resolves an old one; a finding id
  * or an edge is kept as given. `page` is the page size a request's limit makes.
  */
final class AttestationsControllers(
    attestations: AttestationService,
    sources: PassageSource,
    current: (AliasKind, String) => String,
    page: Option[Int] => Int,
    maintenance: WriteGate
):

  private def refId(targetType: String, id: String): String =
    val kind = targetType.trim.toLowerCase(Locale.ROOT) match
      case "event"   => Some(AliasKindsAdapter.EventRecords)
      case "place"   => Some(AliasKindsAdapter.PlaceRecords)
      case "source"  => Some(AliasKindsAdapter.SourceRecords)
      case "article" => Some(AliasKindsAdapter.ArticleRecords)
      case _         => None
    kind.fold(id)(current(_, id))

  def list(
      citekey: Option[String],
      targetType: Option[String],
      targetId: Option[String],
      limit: Option[Int],
      offset: Option[Int]
  ): AttestationList =
    val target = for t <- targetType; i <- targetId yield refId(t, i)
    attestations.list(
      citekey.map(current(AliasKindsAdapter.SourceRecords, _)),
      targetType,
      target.orElse(targetId),
      page(limit),
      offset.getOrElse(0)
    )

  def get(id: Long): Either[ApiError, Attestation] =
    attestations.get(id).toRight(ApiError(s"No attestation $id", Some(Write.CodeNotFound)))

  /** An unknown target type is a 400 and a missing source a 404 — checked first: the table's constraints would answer
    * both with a 500.
    */
  def create(caller: Caller, in: AttestationPut): Either[ApiError, Attestation] =
    maintenance.guard("create_attestation"):
      val ck = current(AliasKindsAdapter.SourceRecords, in.citekey)
      for
        t <- AttestationTarget.check(in.targetType).left.map(ApiError(_, Some(Write.CodeInvalid)))
        _ <- Either.cond(sources.canonical(ck).isDefined, (), Write.notFound(ck))
      yield attestations.create(in.copy(citekey = ck, targetType = t, targetId = refId(t, in.targetId)))

  def patch(caller: Caller, id: Long, p: AttestationPatch): Either[ApiError, Attestation] =
    maintenance.guard("patch_attestation")(attestations.patch(id, p))

  def delete(caller: Caller, id: Long): Either[ApiError, AttestationDeleted] =
    maintenance.guard("delete_attestation"):
      if attestations.delete(id) then Right(AttestationDeleted(id, true))
      else Left(ApiError(s"No attestation $id", Some(Write.CodeNotFound)))
