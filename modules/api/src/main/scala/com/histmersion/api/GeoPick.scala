package com.histmersion.api

import sttp.tapir.Schema
import com.histmersion.api.catalogue.GeoTime

enum GeoPick:
  case Mid, Start, End, Earliest, Latest

  def wire: com.histmersion.api.GeoPick.Wire = this match
    case Mid      => _root_.com.histmersion.api.GeoPick.Wire("mid")
    case Start    => _root_.com.histmersion.api.GeoPick.Wire("start")
    case End      => _root_.com.histmersion.api.GeoPick.Wire("end")
    case Earliest => _root_.com.histmersion.api.GeoPick.Wire("earliest")
    case Latest   => _root_.com.histmersion.api.GeoPick.Wire("latest")

  def label: String = this match
    case Mid      => ("States at midpoint")
    case Start    => ("States at start")
    case End      => ("States at end")
    case Earliest => ("All states in period (earliest shape)")
    case Latest   => ("All states in period (latest shape)")

  def snapshot: Boolean = this match
    case Mid | Start | End => (true)
    case _                 => (false)

object GeoPick:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.GeoPick.Wire): Option[GeoPick] = values.find(_.wire == _root_.com.histmersion.api.GeoPick.Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

  given WireEnumValues[GeoPick]:
    def all: Array[GeoPick] = GeoPick.values
    def wire(e: GeoPick): _root_.com.histmersion.api.WireEnumValues.Wire = _root_.com.histmersion.api.WireEnumValues.Wire((e.wire).value)
    def fromWire(s: _root_.com.histmersion.api.WireEnumValues.Wire): _root_.scala.Option[GeoPick] = parse(_root_.com.histmersion.api.GeoPick.Wire((s).value))
  given io.circe.Encoder[GeoPick] = WireEnum.encoder
  given io.circe.Decoder[GeoPick] = WireEnum.decoder
  given Schema[GeoPick] = WireEnum.schema

  def yearOf(pick: GeoPick, lo: com.histmersion.api.catalogue.Year, hi: com.histmersion.api.catalogue.Year): com.histmersion.api.catalogue.Year = pick match
    case Start => _root_.com.histmersion.api.catalogue.Year((lo).value)
    case End   => _root_.com.histmersion.api.catalogue.Year((hi).value)
    case _     => _root_.com.histmersion.api.catalogue.Year(((lo).value + (hi).value) / 2)

  def resolve(asOf: Option[com.histmersion.api.catalogue.Year], from: Option[com.histmersion.api.catalogue.Year], to: Option[com.histmersion.api.catalogue.Year], pick: GeoPick): GeoTime =
    val span = (from, to) match
      case (Some(a), Some(b)) => Some((_root_.com.histmersion.api.catalogue.Year(a.value.min(b.value)), _root_.com.histmersion.api.catalogue.Year(a.value.max(b.value))))
      case (Some(a), None)    => Some((a, a))
      case (None, Some(b))    => Some((b, b))
      case (None, None)       => None
    span match
      case None                                        => asOf.fold(GeoTime.Always)(GeoTime.At(_))
      case Some((lo, hi)) if ((pick.snapshot) == (true)) || lo == hi => GeoTime.At(yearOf(pick, lo, hi))
      case Some((lo, hi))                              => GeoTime.Within(lo, hi, latest = _root_.com.histmersion.api.catalogue.GeoTime.Within.Latest(pick == Latest))
