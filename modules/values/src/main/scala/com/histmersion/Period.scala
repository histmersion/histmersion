package com.histmersion

import com.histmersion.Year
import io.github.iltotore.iron.constraint.any.Pure
final case class Period private (start: Year, end: Year):
  def isPoint: Boolean = (start == end)

object Period:
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]

  def from(start: Year, end: Year): Either[Error, Period] =
    Either.cond(start <= end, new Period(start, end), Error.EndsBeforeStart(start, end))

  def point(year: Year): Period = new Period(year, year)

  enum Error derives CanEqual:
    case EndsBeforeStart(start: Year, end: Year)

    def message: Problem = this match
      case EndsBeforeStart(s, e) => Problem(s"the period ends (${e.value}) before it starts (${s.value})")

