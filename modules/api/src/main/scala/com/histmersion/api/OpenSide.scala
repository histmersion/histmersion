package com.histmersion.api

import com.histmersion.api.catalogue.Period
enum OpenSide:
  case Start, End

object OpenSide:
  type BoundsResultPart1 = BoundsResultPart1.T
  object BoundsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundsResultPart2 = BoundsResultPart2.T
  object BoundsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def bounds(period: Option[Period], open: Option[OpenSide]): (Option[com.histmersion.api.OpenSide.BoundsResultPart1], Option[com.histmersion.api.OpenSide.BoundsResultPart2]) =
    (period.map(_.start).filterNot(_ => open.contains(Start)), period.map(_.end).filterNot(_ => open.contains(End)))

  def split(s: Span): (Option[Period], Option[OpenSide]) = s match
    case Span(Some(a), Some(b)) => (Some(if a <= b then Period(a, b) else Period(b, a)), None)
    case Span(Some(a), None)    => (Some(Period(a, a)), Some(End))
    case Span(None, Some(b))    => (Some(Period(b, b)), Some(Start))
    case Span(None, None)       => (None, None)
