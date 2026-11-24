package com.histmersion
package plugins.events
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.Cursor

import com.histmersion.plugin.{Latitude, Longitude, Year}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** Reading a catalogue's JSON: a field as text, a whole number or a number, whatever the catalogue wrote it as. */
@atBoundary(BoundaryKind.Json, "reads a catalogue JSON field as text or number whatever it was written as")
object JsonFields:
  type Decimal = Decimal.T
  object Decimal extends Newtype[Double, Pure]
  type Integer = Integer.T
  object Integer extends Newtype[Long, Pure]
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Year = Year.T
  object Year extends Newtype[Long, Pure]

  extension (c: Cursor)
    /** The field as text: a string, or a number as written; trimmed, if it is not blank. */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def str(k: Key): Option[Text] = c.downField(k.value).literal.map(Text(_))

    /** The field as a whole number: an integer, a numeric string, or a fraction cut to its whole part. */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def long(k: Key): Option[Integer] =
      val f = c.downField(k.value)
      f.integer.orElse(f.focus.flatMap(_.asNumber).flatMap(_.toDouble).map(_.toLong)).map(Integer(_))

    /** The field as a number: a number or a numeric string. */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def dbl(k: Key): Option[Decimal] = c.downField(k.value).double.map(Decimal(_))

  /** A year as the SPI states it; `None` beyond what a `Year` holds. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def year(y: Year): Option[plugin.Year] =
    Option.when(y.value.isValidInt)(plugin.Year(y.value.toInt))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def latitude(l: Option[Decimal]): Option[Latitude] =
    l.filter(d => d.value >= -90.0 && d.value <= 90.0).map(d => Latitude(d.value))
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def longitude(l: Option[Decimal]): Option[Longitude] =
    l.filter(d => d.value >= -180.0 && d.value <= 180.0).map(d => Longitude(d.value))
