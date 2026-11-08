package com.histmersion
package plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** The values the SPI exchanges about works, places, events and pictures, each a type that says what it means. They are
  * Iron newtypes: opaque, so at run time a `Year` is an `Int` and a `Doi` a `String`.
  *
  * None of them judges validity — that is the host's: a value is built from any value of its base type (`Year(-1600)`,
  * `Doi(raw.trim)`, `Latitude(lat)`), `.value` takes the plain value out.
  *
  * The host checks every value a plugin hands over where it becomes the model's: a DOI that is none, a Wikidata id that
  * is no item or a position off the globe is left out of its record with a warning (the rest of the record is kept); a
  * dating that cannot be one refuses the record, with the value in the reason.
  */

/** A span of deep time, both ends included; a single year is a period whose ends are the same year. Built with
  * [[Period.from]] (the start must not be after the end) or [[Period.point]].
  */
final case class Period private (start: Year, end: Year) derives CanEqual:
  def isPoint: Boolean = start == end

object Period:
  /** The period from `start` to `end`, or why there is none (it would end before it starts). */

  def from(start: Year, end: Year): Either[Problem, Period] =
    Either.cond(
      start <= end,
      new Period(start, end),
      Problem(s"the period ends (${end.value}) before it starts (${start.value})")
    )

  /** The period of one year. */
  def point(year: Year): Period = new Period(year, year)
