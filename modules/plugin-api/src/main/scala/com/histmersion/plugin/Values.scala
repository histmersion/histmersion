package com.histmersion.plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.numeric.Interval
import io.github.iltotore.iron.constraint.string.Match

/** The values the SPI exchanges about works, places, events and pictures, each a type that says what it
  * means. They are Iron newtypes: opaque, so at run time a `Year` is an `Int` and a `Doi` a `String`.
  *
  * A literal is checked when the plugin compiles: `Year(-1600)`, `Doi("10.1234/abc")`, `Latitude(36.4)`.
  * A value read at run time is parsed: `Doi.either(raw)` (`Left` with the reason), `Doi.option(raw)`.
  * `.value` takes the plain value out. A value whose type says it always holds (`Year`, `Licence`) is
  * built from any value of its base type: `Year(year.toInt)`.
  *
  * The host checks every value a plugin hands over again (a plugin built against another constraint
  * could hand over anything): what fails is refused for that record, with the value in the reason. */

/** A year of deep time, signed: 1600 BCE is `Year(-1600)`. Counted astronomically, so every integer is
  * a year (there is a year 0). */
type Year = Year.T

object Year extends RefinedType[Int, Pure]

/** A span of deep time, both ends included; a single year is a period whose ends are the same year.
  * Built with [[Period.from]] (the start must not be after the end) or [[Period.point]]. */
final case class Period private (start: Year, end: Year):
  def isPoint: Boolean = start.value == end.value

object Period:
  /** The period from `start` to `end`, or why there is none (it would end before it starts). */
  def from(start: Year, end: Year): Either[String, Period] =
    Either.cond(start.value <= end.value, new Period(start, end),
      s"the period ends (${end.value}) before it starts (${start.value})")

  /** The period of one year. */
  def point(year: Year): Period = new Period(year, year)

/** Degrees north (negative: south) on WGS84, from -90 to 90. */
type Latitude = Latitude.T

object Latitude extends RefinedType[Double, Interval.Closed[-90.0, 90.0]]

/** Degrees east (negative: west) on WGS84, from -180 to 180. */
type Longitude = Longitude.T

object Longitude extends RefinedType[Double, Interval.Closed[-180.0, 180.0]]

/** A DOI as the registry writes it after `https://doi.org/`: `10.<registrant>/<suffix>` (`10.1234/abc`). */
type Doi = Doi.T

object Doi extends RefinedType[String, Match["10\\.[0-9]+/\\S+"]]

/** A Wikidata item: `Q` and its number (`Q5788`). */
type WikidataId = WikidataId.T

object WikidataId extends RefinedType[String, Match["Q[1-9][0-9]*"]]

/** A licence AS ITS PROVIDER STATES IT: an SPDX id (`CC-BY-4.0`, `CC0-1.0`), a short name (`CC BY-SA 3.0`,
  * `Public domain`), a Creative Commons URL, or the provider's own words. Never judged by the plugin: the
  * host reads the statement and decides what it allows, so any text is a statement (one the host cannot
  * read is "unknown", which it never stores). */
type Licence = Licence.T

object Licence extends RefinedType[String, Pure]
