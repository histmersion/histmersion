package com.histmersion.plugin

import io.github.iltotore.iron.*

/** The values the SPI exchanges about works, places, events and pictures, each a type that says what it means. They are
  * Iron newtypes: opaque, so at run time a `Year` is an `Int` and a `Doi` a `String`.
  *
  * A literal is checked when the plugin compiles: `Year(-1600)`, `Doi("10.1234/abc")`, `Latitude(36.4)`. A value read
  * at run time is parsed: `Doi.either(raw)` (`Left` with the reason), `Doi.option(raw)`. `.value` takes the plain value
  * out. A value whose type says it always holds (`Year`, `Licence`) is built from any value of its base type:
  * `Year(year.toInt)`.
  *
  * The host checks every value a plugin hands over again (a plugin built against another constraint could hand over
  * anything): what fails is refused for that record, with the value in the reason.
  */

/** A span of deep time, both ends included; a single year is a period whose ends are the same year. Built with
  * [[Period.from]] (the start must not be after the end) or [[Period.point]].
  */
final case class Period private (start: Year, end: Year):
  def isPoint: Boolean = start.value == end.value

object Period:
  /** The period from `start` to `end`, or why there is none (it would end before it starts). */
  def from(start: Year, end: Year): Either[String, Period] =
    Either.cond(
      start.value <= end.value,
      new Period(start, end),
      s"the period ends (${end.value}) before it starts (${start.value})"
    )

  /** The period of one year. */
  def point(year: Year): Period = new Period(year, year)
