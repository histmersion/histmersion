package com.histmersion.api.research
import hearth.kindlings.optics.syntax.*

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CritiqueReason(src: com.histmersion.api.library.Citekey, dst: com.histmersion.api.library.Citekey, reason: CritiqueReason.Reason, by: Option[CritiqueReason.By] = None)
    derives KindlingsCodecAsObject:
  def ref: CritiqueRef = CritiqueRef(src, dst)

object CritiqueReason:
  type MaxChars = MaxChars.T
  object MaxChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TidyResultPart1 = TidyResultPart1.T
  object TidyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type By = By.T
  object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  val MaxChars: com.histmersion.api.research.CritiqueReason.MaxChars = _root_.com.histmersion.api.research.CritiqueReason.MaxChars(Coverage.Reason.MaxChars)

  def parse(s: com.histmersion.api.research.CritiqueReason.ParseS): Either[com.histmersion.api.research.CritiqueReason.ParseResultPart1, CritiqueReason] =
    val i = (s).value.indexOf('=')
    if i < 0 then Left(_root_.com.histmersion.api.research.CritiqueReason.ParseResultPart1(s"'$s': write it as <src>><dst>=<why> (an empty why removes the reason)"))
    else CritiqueRef.parse(_root_.com.histmersion.api.research.CritiqueRef.ParseS((s).value.take(i))).map(r => CritiqueReason(r.src, r.dst, _root_.com.histmersion.api.research.CritiqueReason.Reason((s).value.drop(i + 1).trim)))

  def tidy(r: CritiqueReason): Either[com.histmersion.api.research.CritiqueReason.TidyResultPart1, CritiqueReason] =
    val text = Option(r.reason).map((_).value.trim.replaceAll("\\s+", " ")).getOrElse("")
    if text.length > MaxChars then
      Left(_root_.com.histmersion.api.research.CritiqueReason.TidyResultPart1(s"reason for ${r.ref.wire}: ${text.length} characters (at most $MaxChars — one sentence)"))
    else
      Right(
        r.modify(
          _.src).setTo(r.src.trim.toLowerCase(java.util.Locale.ROOT)).modify(_.dst).setTo(r.dst.trim.toLowerCase(java.util.Locale.ROOT)).modify(_.reason).setTo(text
        )
      )
