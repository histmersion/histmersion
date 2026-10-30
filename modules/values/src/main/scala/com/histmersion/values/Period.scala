package com.histmersion.values

/** A span of deep time, both ends included; a single year is a period whose ends are the same year. */
final case class Period private (start: Year, end: Year):
  def isPoint: Boolean = start.value == end.value

object Period:
  def from(start: Year, end: Year): Either[PeriodError, Period] =
    Either.cond(start.value <= end.value, new Period(start, end), PeriodError.EndsBeforeStart(start, end))

  def point(year: Year): Period = new Period(year, year)
