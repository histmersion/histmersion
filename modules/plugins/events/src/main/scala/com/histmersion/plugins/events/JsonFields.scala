package com.histmersion.plugins.events

import com.histmersion.plugin.{Latitude, Longitude, Year}
import io.circe.ACursor

/** Reading a catalogue's JSON: a field as text, a whole number or a number, whatever the catalogue wrote it as. */
object JsonFields:
  type StrResult = StrResult.T
  object StrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrK = StrK.T
  object StrK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LongResult = LongResult.T
  object LongResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LongK = LongK.T
  object LongK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DblResult = DblResult.T
  object DblResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DblK = DblK.T
  object DblK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearY = YearY.T
  object YearY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LatitudeL = LatitudeL.T
  object LatitudeL extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LongitudeL = LongitudeL.T
  object LongitudeL extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def str(c: ACursor, k: com.histmersion.plugins.events.JsonFields.StrK): Option[com.histmersion.plugins.events.JsonFields.StrResult] =
    c.downField(k).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)

  def long(c: ACursor, k: com.histmersion.plugins.events.JsonFields.LongK): Option[com.histmersion.plugins.events.JsonFields.LongResult] =
    c.downField(k)
      .focus
      .flatMap(j =>
        j.asNumber
          .map(n => n.toLong.getOrElse(n.toDouble.toLong))
          .orElse(j.asString.flatMap(_.trim.toLongOption))
      )

  def dbl(c: ACursor, k: com.histmersion.plugins.events.JsonFields.DblK): Option[com.histmersion.plugins.events.JsonFields.DblResult] =
    c.downField(k).focus.flatMap(j => j.asNumber.map(_.toDouble).orElse(j.asString.flatMap(_.trim.toDoubleOption)))

  /** A year as the SPI states it; `None` beyond what a `Year` holds. */
  def year(y: com.histmersion.plugins.events.JsonFields.YearY): Option[Year] = Option.when(y.isValidInt)(Year(y.toInt))

  def latitude(l: Option[com.histmersion.plugins.events.JsonFields.LatitudeL]): Option[Latitude] = l.flatMap(Latitude.option)
  def longitude(l: Option[com.histmersion.plugins.events.JsonFields.LongitudeL]): Option[Longitude] = l.flatMap(Longitude.option)
