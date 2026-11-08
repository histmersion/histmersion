package com.histmersion.plugins.events

import com.histmersion.plugin.{Latitude, Longitude, Year}
import io.circe.ACursor

/** Reading a catalogue's JSON: a field as text, a whole number or a number, whatever the catalogue wrote it as. */
object JsonFields:
  type Decimal = Decimal.T
  object Decimal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Integer = Integer.T
  object Integer extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Year = Year.T
  object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def str(c: ACursor, k: com.histmersion.plugins.events.JsonFields.Key): Option[com.histmersion.plugins.events.JsonFields.Text] =
    c.downField(k.value).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty).map(t => Text(t))

  def long(c: ACursor, k: com.histmersion.plugins.events.JsonFields.Key): Option[com.histmersion.plugins.events.JsonFields.Integer] =
    c.downField(k.value)
      .focus
      .flatMap(j =>
        j.asNumber
          .map(n => n.toLong.getOrElse(n.toDouble.toLong))
          .orElse(j.asString.flatMap(_.trim.toLongOption))
      )
      .map(n => Integer(n))

  def dbl(c: ACursor, k: com.histmersion.plugins.events.JsonFields.Key): Option[com.histmersion.plugins.events.JsonFields.Decimal] =
    c.downField(k.value).focus.flatMap(j => j.asNumber.map(_.toDouble).orElse(j.asString.flatMap(_.trim.toDoubleOption))).map(d => Decimal(d))

  /** A year as the SPI states it; `None` beyond what a `Year` holds. */
  def year(y: com.histmersion.plugins.events.JsonFields.Year): Option[com.histmersion.plugin.Year] = Option.when(y.value.isValidInt)(com.histmersion.plugin.Year(y.value.toInt))

  def latitude(l: Option[com.histmersion.plugins.events.JsonFields.Decimal]): Option[Latitude] = l.filter(d => d.value >= -90.0 && d.value <= 90.0).map(d => Latitude(d.value))
  def longitude(l: Option[com.histmersion.plugins.events.JsonFields.Decimal]): Option[Longitude] = l.filter(d => d.value >= -180.0 && d.value <= 180.0).map(d => Longitude(d.value))
