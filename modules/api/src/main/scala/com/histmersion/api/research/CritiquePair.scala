package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CritiquePair(
    src: CritiqueSide,
    kinds: List[String],
    dst: CritiqueSide,
    state: CritiqueState,
    reason: Option[String] = None,
    reasonBy: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def ref: CritiqueRef = CritiqueRef(src.citekey, dst.citekey)
  def open: Boolean = state == CritiqueState.Open

  def criticised: CritiqueSide = if Critiques.olderFirst(kinds) then src else dst

  def critic: CritiqueSide = if Critiques.olderFirst(kinds) then dst else src

  def bearing: Boolean = open && criticised.cited

  def line: String = s"[[${src.citekey}]] ${kinds.mkString("/")} [[${dst.citekey}]]"
object CritiquePair:
  given Schema[CritiquePair] = summon[KindlingsSchema[CritiquePair]].schema
