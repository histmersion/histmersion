package com.histmersion

import com.histmersion.Year
final case class Period private (start: Year, end: Year):
  def isPoint: Boolean = start.value == end.value

object Period:
  def from(start: Year, end: Year): Either[Period.Error, Period] =
    Either.cond(start.value <= end.value, new Period(start, end), Period.Error.EndsBeforeStart(start, end))

  def point(year: Year): Period = new Period(year, year)

  enum Error:
    case EndsBeforeStart(start: Year, end: Year)

    def message: String = this match
      case EndsBeforeStart(s, e) => s"the period ends (${e.value}) before it starts (${s.value})"
