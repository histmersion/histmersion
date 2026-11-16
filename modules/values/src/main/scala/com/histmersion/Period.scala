package com.histmersion

import com.histmersion.util.problem.Problem
import com.histmersion.Year
final case class Period private (start: Year, end: Year):
  def isPoint: Boolean = (start == end)

object Period:

  def from(start: Year, end: Year): Either[Error, Period] =
    Either.cond(start <= end, new Period(start, end), Error.EndsBeforeStart(start, end))

  def point(year: Year): Period = new Period(year, year)

  enum Error:
    case EndsBeforeStart(start: Year, end: Year)

    def message: Problem = this match
      case EndsBeforeStart(s, e) =>
        Problem.Invalid.Inverted.here(Problem.Invalid.Year.of(s), Problem.Invalid.Year.of(e))
