package com.histmersion.plugins.events

import com.histmersion.plugin.{Latitude, Longitude, Year}
import io.circe.ACursor

/** Reading a catalogue's JSON: a field as text, a whole number or a number, whatever the catalogue wrote it as. */
object JsonFields:
  def str(c: ACursor, k: String): Option[String] =
    c.downField(k).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)

  def long(c: ACursor, k: String): Option[Long] =
    c.downField(k)
      .focus
      .flatMap(j =>
        j.asNumber
          .map(n => n.toLong.getOrElse(n.toDouble.toLong))
          .orElse(j.asString.flatMap(_.trim.toLongOption))
      )

  def dbl(c: ACursor, k: String): Option[Double] =
    c.downField(k).focus.flatMap(j => j.asNumber.map(_.toDouble).orElse(j.asString.flatMap(_.trim.toDoubleOption)))

  /** A year as the SPI states it; `None` beyond what a `Year` holds. */
  def year(y: Long): Option[Year] = Option.when(y.isValidInt)(Year(y.toInt))

  def latitude(l: Option[Double]): Option[Latitude] = l.flatMap(Latitude.option)
  def longitude(l: Option[Double]): Option[Longitude] = l.flatMap(Longitude.option)
