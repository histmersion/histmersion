package com.histmersion
package api.research

import com.histmersion.util.problem.Problem

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

final case class CritiqueReason(
    src: Citekey,
    dst: Citekey,
    reason: Coverage.Rationale,
    by: Option[CritiqueReason.By] = None
) derives JsonCodec:
  def ref: CritiqueRef = CritiqueRef(src, dst)

object CritiqueReason:

  export api.automation.Activity.Actor as By

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[CritiqueReason] = KindlingsSchema
    .derived[CritiqueReason]
    .schema
    .description(
      "Why a disagreement between two sources (an edge of the critique checklist) does not bear on the article"
    )
    .modify(_.src)(
      _.description("The critic: the source the edge starts at (challenges/refutes/updates/superseded_by)")
    )
    .modify(_.dst)(_.description("The source it disagrees with"))
    .modify(_.reason)(
      _.description(
        "One plain sentence: why the critique does not touch what the article cites the source for. Empty removes it"
      )
    )
    .modify(_.by)(_.description("Who set it (filled by the server when left out)"))

  val MaxChars: Int = Coverage.Reason.MaxChars

  /** A reason as the command line writes it: `<src>><dst>=<why>`. */
  type Spec = Spec.T
  object Spec extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Spec): Either[Problem, CritiqueReason] =
    val text = s"$s"
    val i = text.indexOf('=')
    if i < 0 then
      Left(
        Problem.Invalid.Unreadable.here(
          Problem.TypeName.of(Spec),
          Problem.Raw(s"$s"),
          Vector(Problem.Raw("a1999>b2001=it concerns the dating only"))
        )
      )
    else
      CritiqueRef
        .parse(CritiqueRef.Wire(text.take(i)))
        .map(r => CritiqueReason(r.src, r.dst, Coverage.Rationale(text.drop(i + 1).trim)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tidy(r: CritiqueReason): Either[Problem, CritiqueReason] =
    val text = Option(r.reason).map(w => s"$w".trim.replaceAll("\\s+", " ")).getOrElse("")
    if text.length > MaxChars then
      Left(
        Problem.Invalid.OutOfRange(
          Problem.TypeName.of(Coverage.Rationale),
          Problem.Raw.of(text.length.toLong),
          Problem.Invalid.Bounds(None, Some(Problem.Raw.of(MaxChars.toLong))),
          Vector(Problem.Field("reason"), Problem.Field(s"${r.ref.wire}"))
        )
      )
    else
      Right(
        r.modify(_.src)
          .setTo(Citekey(r.src.value.trim.toLowerCase(java.util.Locale.ROOT)))
          .modify(_.dst)
          .setTo(Citekey(r.dst.value.trim.toLowerCase(java.util.Locale.ROOT)))
          .modify(_.reason)
          .setTo(Coverage.Rationale(s"$text"))
      )
