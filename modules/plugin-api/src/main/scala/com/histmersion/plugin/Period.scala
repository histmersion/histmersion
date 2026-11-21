package com.histmersion
package plugin

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A span of deep time, both ends included; a single year is a period whose ends are the same year. Built with
  * [[Period.from]] (the start must not be after the end) or [[Period.point]].
  */
final case class Period private (start: Year, end: Year) derives CanEqual:
  def isPoint: Boolean = start == end

object Period:

  def from(start: Year, end: Year): Either[Problem, Period] =
    Either.cond(
      start <= end,
      new Period(start, end),
      Problem.Invalid.Inverted.here(Problem.Invalid.Year.of(start), Problem.Invalid.Year.of(end))
    )

  def point(year: Year): Period = new Period(year, year)
