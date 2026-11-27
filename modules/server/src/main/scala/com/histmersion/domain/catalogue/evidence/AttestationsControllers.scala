package com.histmersion
package domain.catalogue.evidence

import com.histmersion.util.problem.{Legacy, Problem}
import io.scalaland.chimney.dsl.*
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.domain.catalogue.AttestationTarget

import com.histmersion.api.Write
import com.histmersion.api.automation.Activity
import com.histmersion.api.catalogue.Attestation
import com.histmersion.domain.Caller
import com.histmersion.domain.library.Citekey
import com.histmersion.{Offset, PageSize}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind

import java.util.Locale
import com.histmersion.api.Api
import com.histmersion.domain.catalogue.evidence.passages.Passage
import com.histmersion.api.RowCount
import com.histmersion.api.personal.UnreadPage
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The attestations' operations as REST, MCP and the CLI reach them. A reference to a record of a renamed kind (an
  * event, a place, a source, an article) names it by its identifier now — `current` resolves an old one; a finding id
  * or an edge is kept as given. `page` is the page size a request's limit makes.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final class AttestationsControllers(
    attestations: AttestationService,
    sources: Passage.Source,
    current: (AliasKind, AttestationsControllers.Alias) => AttestationsControllers.Alias,
    page: Option[PageSize] => PageSize,
    maintenance: WriteGate
):

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def invalid(m: Problem) = Api.Error(m, Some(Api.Error.Code(Write.CodeInvalid)))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def notFound(id: Attestation.Id) =
    Api.Error(Problem.Unclassified(Legacy.Text(s"No attestation $id")), Some(Api.Error.Code(Write.CodeNotFound)))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def refId(targetType: Attestation.TargetType, id: Attestation.TargetId): Attestation.TargetId =
    AliasKinds
      .ofTarget(Activity.Entry.TargetKind(targetType.value))
      .fold(id)(k => Attestation.TargetId(current(k, AttestationsControllers.Alias(id.value)).value))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def list(
      citekey: Option[com.histmersion.api.library.Citekey],
      targetType: Option[Attestation.TargetType],
      targetId: Option[Attestation.TargetId],
      limit: Option[PageSize],
      offset: Option[Offset]
  ): Attestation.List =
    val target = for t <- targetType; i <- targetId yield refId(t, i)
    // a key that is no citekey names no source, so no attestation
    val keys =
      citekey.map(c => Citekey.option(current(AliasKinds.SourceRecords, AttestationsControllers.Alias(c.value)).value))
    if keys.contains(None) then Attestation.List.of(RowCount(0), Nil, UnreadPage.Offset(0))
    else
      attestations.list(keys.flatten, targetType, target.orElse(targetId), page(limit), offset.getOrElse(Offset.Start))

  def get(id: Attestation.Id): Either[Api.Error, Attestation] =
    attestations.get(id).toRight(notFound(id))

  /** An unknown target type is a 400 and a missing source a 404 — checked first: the table's constraints would answer
    * both with a 500.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def create(caller: Caller, in: Attestation.Put)(using Creator.New): Either[Api.Error, Attestation] =
    maintenance.guard(WriteGate.OperationName("create_attestation")):
      val ck = current(AliasKinds.SourceRecords, AttestationsControllers.Alias(in.citekey.value)).value
      for
        wire <- AttestationTarget
          .check(AttestationTarget.Wire(in.targetType.value))
          .left
          .map(p => invalid(p))
        t = Attestation.TargetType(wire.value)
        _ <- Either.cond(
          Citekey.option(ck).flatMap(sources.canonical).isDefined,
          (),
          Write.notFound(com.histmersion.api.library.Citekey(ck))
        )
      yield attestations.create(
        in.modify(_.citekey)
          .setTo(com.histmersion.api.library.Citekey(ck))
          .modify(_.targetType)
          .setTo(t)
          .modify(_.targetId)
          .setTo(refId(t, in.targetId))
      )

  def patch(caller: Caller, id: Attestation.Id, p: Attestation.Patch): Either[Api.Error, Attestation] =
    maintenance.guard(WriteGate.OperationName("patch_attestation"))(attestations.patch(id, p))

  def delete(caller: Caller, id: Attestation.Id): Either[Api.Error, Attestation.Deleted] =
    maintenance.guard(WriteGate.OperationName("delete_attestation")):
      if attestations.delete(id) then Right(Attestation.Deleted(id, Attestation.Deleted.Value(true)))
      else Left(notFound(id))

object AttestationsControllers:
  /** An identifier an alias table reads: the text of a record's old or current name. */
  type Alias = Alias.T
  object Alias extends Newtype[String, Pure]
