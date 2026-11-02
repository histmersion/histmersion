package com.histmersion

import com.histmersion.values.PeriodError
final case class Period private (start: Year, end: Year):
  def isPoint: Boolean = start.value == end.value

object Period:
  def from(start: Year, end: Year): Either[PeriodError, Period] =
    Either.cond(start.value <= end.value, new Period(start, end), PeriodError.EndsBeforeStart(start, end))

  def point(year: Year): Period = new Period(year, year)
