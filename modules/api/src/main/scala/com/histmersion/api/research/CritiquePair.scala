package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CritiquePair(
    src: CritiqueSide,
    kinds: List[CritiquePair.Kinds],
    dst: CritiqueSide,
    state: CritiqueState,
    reason: Option[CritiquePair.Reason] = None,
    reasonBy: Option[CritiquePair.ReasonBy] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def ref: CritiqueRef = CritiqueRef(src.citekey, dst.citekey)
  def open: com.histmersion.api.research.CritiquePair.OpenResult = _root_.com.histmersion.api.research.CritiquePair.OpenResult(state == CritiqueState.Open)

  def criticised: CritiqueSide = if Critiques.olderFirst(kinds) then src else dst

  def critic: CritiqueSide = if Critiques.olderFirst(kinds) then dst else src

  def bearing: com.histmersion.api.research.CritiquePair.BearingResult = ((open) == _root_.com.histmersion.api.research.CritiquePair.OpenResult(true)) && ((criticised.cited) == _root_.com.histmersion.api.research.CritiqueSide.Cited(true))

  def line: com.histmersion.api.research.CritiquePair.LineResult = _root_.com.histmersion.api.research.CritiquePair.LineResult(s"[[${src.citekey}]] ${kinds.mkString("/")} [[${dst.citekey}]]")

object CritiquePair:
  type OpenResult = OpenResult.T
  object OpenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BearingResult = BearingResult.T
  object BearingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineResult = LineResult.T
  object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Kinds = Kinds.T
  object Kinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReasonBy = ReasonBy.T
  object ReasonBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
