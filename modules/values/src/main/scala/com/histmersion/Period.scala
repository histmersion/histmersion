package com.histmersion

import com.histmersion.Year
final case class Period private (start: Year, end: Year):
  def isPoint: Boolean = (start.value == end.value)

object Period:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def from(start: Year, end: Year): Either[Period.Error, Period] =
    Either.cond(start.value <= end.value, new Period(start, end), Period.Error.EndsBeforeStart(start, end))

  def point(year: Year): Period = new Period(year, year)

  enum Error:
    case EndsBeforeStart(start: Year, end: Year)

    def message: com.histmersion.Period.Problem = this match
      case EndsBeforeStart(s, e) => _root_.com.histmersion.Period.Problem(s"the period ends (${e.value}) before it starts (${s.value})")

  object Error:
