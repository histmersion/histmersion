package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MergeResolution(
    issue: MergeResolution.Issue,
    decision: MergeResolution.Decision,
    text: Option[MergeResolution.Text] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MergeResolution:
  type Issue = Issue.T
  object Issue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
  type Decision = com.histmersion.api.exchange.MergeDecision.Kind.Wire
  val Decision: _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire.type = _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
