package com.histmersion
package plugins.events
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.Cursor

import com.histmersion.plugin.{Latitude, Longitude, Year}
import io.github.iltotore.iron.constraint.any.Pure

/** Reading a catalogue's JSON: a field as text, a whole number or a number, whatever the catalogue wrote it as. */
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

  def str(c: Cursor, k: Key): Option[Text] =
    c.downField(k.value)
      .focus
      .flatMap(j => j.asString.orElse(j.asNumber.map(_.toString)))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(t => Text(t))

  def long(c: Cursor, k: Key): Option[Integer] =
    c.downField(k.value)
      .focus
      .flatMap(j =>
        j.asNumber
          .flatMap(n => n.toLong.orElse(n.toDouble.map(_.toLong)))
          .orElse(j.asString.flatMap(_.trim.toLongOption))
      )
      .map(n => Integer(n))

  def dbl(c: Cursor, k: Key): Option[Decimal] =
    c.downField(k.value)
      .focus
      .flatMap(j => j.asNumber.flatMap(_.toDouble).orElse(j.asString.flatMap(_.trim.toDoubleOption)))
      .map(d => Decimal(d))

  /** A year as the SPI states it; `None` beyond what a `Year` holds. */
  def year(y: Year): Option[com.histmersion.plugin.Year] =
    Option.when(y.value.isValidInt)(com.histmersion.plugin.Year(y.value.toInt))

  def latitude(l: Option[Decimal]): Option[Latitude] =
    l.filter(d => d.value >= -90.0 && d.value <= 90.0).map(d => Latitude(d.value))
  def longitude(l: Option[Decimal]): Option[Longitude] =
    l.filter(d => d.value >= -180.0 && d.value <= 180.0).map(d => Longitude(d.value))
