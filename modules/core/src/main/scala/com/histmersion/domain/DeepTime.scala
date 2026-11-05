package com.histmersion.domain

import com.histmersion.{Period, Year}

/** Years as the corpus stores them (signed `Long`s, BCE negative) made into the model's `Year` and `Period`.
  */
object DeepTime:
  /** `None` for a year beyond what a `Year` holds — no source dates anything to it. */
  type YearY = YearY.T
  object YearY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanA = SpanA.T
  object SpanA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanB = SpanB.T
  object SpanB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def year(y: com.histmersion.domain.DeepTime.YearY): Option[Year] = Option.when(y.isValidInt)(Year(y.toInt))

  /** The period between two years given in either order; `None` when either lies beyond a `Year`. */
  def span(a: com.histmersion.domain.DeepTime.SpanA, b: com.histmersion.domain.DeepTime.SpanB): Option[Period] =
    for
      start <- year(a.min(b))
      end <- year(a.max(b))
      period <- Period.from(start, end).toOption
    yield period
