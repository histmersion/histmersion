package com.histmersion.api.research
import hearth.kindlings.optics.syntax.*

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CritiqueReason(src: String, dst: String, reason: String, by: Option[String] = None)
    derives KindlingsCodecAsObject:
  def ref: CritiqueRef = CritiqueRef(src, dst)

object CritiqueReason:
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

  def parse(s: String): Either[String, CritiqueReason] =
    val i = s.indexOf('=')
    if i < 0 then Left(s"'$s': write it as <src>><dst>=<why> (an empty why removes the reason)")
    else CritiqueRef.parse(s.take(i)).map(r => CritiqueReason(r.src, r.dst, s.drop(i + 1).trim))

  def tidy(r: CritiqueReason): Either[String, CritiqueReason] =
    val text = Option(r.reason).map(_.trim.replaceAll("\\s+", " ")).getOrElse("")
    if text.length > MaxChars then
      Left(s"reason for ${r.ref.wire}: ${text.length} characters (at most $MaxChars — one sentence)")
    else
      Right(
        r.modify(
          _.src).setTo(r.src.trim.toLowerCase(java.util.Locale.ROOT)).modify(_.dst).setTo(r.dst.trim.toLowerCase(java.util.Locale.ROOT)).modify(_.reason).setTo(text
        )
      )
