package com.histmersion
package api.research

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

final case class CritiqueReason(
    src: Citekey,
    dst: Citekey,
    reason: Problem,
    by: Option[CritiqueReason.By] = None
) derives JsonCodec:
  def ref: CritiqueRef = CritiqueRef(src, dst)

object CritiqueReason:

  export com.histmersion.api.automation.Activity.Actor as By

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

  def parse(s: Problem): Either[Problem, CritiqueReason] =
    val i = (s).sentence.indexOf('=')
    if i < 0 then
      Left(
        Problem.Unclassified(
          Legacy.Text(s"'${s.sentence}': write it as <src>><dst>=<why> (an empty why removes the reason)")
        )
      )
    else
      CritiqueRef
        .parse(CritiqueRef.Wire((s).sentence.take(i)))
        .map(r => CritiqueReason(r.src, r.dst, Problem.Unclassified(Legacy.Text((s).sentence.drop(i + 1).trim))))

  def tidy(r: CritiqueReason): Either[Problem, CritiqueReason] =
    val text = Option(r.reason).map((_).sentence.trim.replaceAll("\\s+", " ")).getOrElse("")
    if text.length > MaxChars then
      Left(
        Problem.Unclassified(
          Legacy.Text(s"reason for ${r.ref.wire}: ${text.length} characters (at most $MaxChars — one sentence)")
        )
      )
    else
      Right(
        r.modify(_.src)
          .setTo(Citekey(r.src.value.trim.toLowerCase(java.util.Locale.ROOT)))
          .modify(_.dst)
          .setTo(Citekey(r.dst.value.trim.toLowerCase(java.util.Locale.ROOT)))
          .modify(_.reason)
          .setTo(Problem.Unclassified(Legacy.Text(text)))
      )
