package com.histmersion
package api

import sttp.tapir.Schema
import com.histmersion.api.catalogue.GeoTime
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year

enum GeoPick derives CanEqual:
  case Mid, Start, End, Earliest, Latest

  def wire: GeoPick.Wire = this match
    case Mid      => GeoPick.Wire("mid")
    case Start    => GeoPick.Wire("start")
    case End      => GeoPick.Wire("end")
    case Earliest => GeoPick.Wire("earliest")
    case Latest   => GeoPick.Wire("latest")

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
  object Wire extends Newtype[String, Pure]

  def parse(s: Wire): Option[GeoPick] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

  given WireEnumValues[GeoPick]:
    def all: Array[GeoPick] = values
    def wire(e: GeoPick): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    def fromWire(s: WireEnumValues.Wire): Option[GeoPick] = parse(Wire((s).value))
  given io.circe.Encoder[GeoPick] = WireEnum.encoder
  given io.circe.Decoder[GeoPick] = WireEnum.decoder
  given Schema[GeoPick] = WireEnum.schema

  def yearOf(pick: GeoPick, lo: Year, hi: Year): Year = pick match
    case Start => Year((lo).value)
    case End   => Year((hi).value)
    case _     => Year(((lo).value + (hi).value) / 2)

  def resolve(asOf: Option[Year], from: Option[Year], to: Option[Year], pick: GeoPick): GeoTime =
    val span = (from, to) match
      case (Some(a), Some(b)) => Some((Year(a.value.min(b.value)), Year(a.value.max(b.value))))
      case (Some(a), None)    => Some((a, a))
      case (None, Some(b))    => Some((b, b))
      case (None, None)       => None
    span match
      case None                                                      => asOf.fold(GeoTime.Always)(GeoTime.At(_))
      case Some((lo, hi)) if ((pick.snapshot) == (true)) || lo == hi => GeoTime.At(yearOf(pick, lo, hi))
      case Some((lo, hi)) => GeoTime.Within(lo, hi, latest = GeoTime.Within.Latest(pick == Latest))
