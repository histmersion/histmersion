package com.histmersion.api

import sttp.tapir.Schema
import com.histmersion.api.catalogue.GeoTime

enum GeoPick:
  case Mid, Start, End, Earliest, Latest

  def wire: String = this match
    case Mid      => "mid"
    case Start    => "start"
    case End      => "end"
    case Earliest => "earliest"
    case Latest   => "latest"

  def label: String = this match
    case Mid      => "States at midpoint"
    case Start    => "States at start"
    case End      => "States at end"
    case Earliest => "All states in period (earliest shape)"
    case Latest   => "All states in period (latest shape)"

  def snapshot: Boolean = this match
    case Mid | Start | End => true
    case _                 => false

object GeoPick:
  def parse(s: String): Option[GeoPick] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  given WireEnumValues[GeoPick]:
    def all: Array[GeoPick] = GeoPick.values
    def wire(e: GeoPick): String = e.wire
    def fromWire(s: String): Option[GeoPick] = parse(s)
  given io.circe.Encoder[GeoPick] = WireEnum.encoder
  given io.circe.Decoder[GeoPick] = WireEnum.decoder
  given Schema[GeoPick] = WireEnum.schema

  def yearOf(pick: GeoPick, lo: Long, hi: Long): Long = pick match
    case Start => lo
    case End   => hi
    case _     => (lo + hi) / 2

  def resolve(asOf: Option[Long], from: Option[Long], to: Option[Long], pick: GeoPick): GeoTime =
    val span = (from, to) match
      case (Some(a), Some(b)) => Some((a.min(b), a.max(b)))
      case (Some(a), None)    => Some((a, a))
      case (None, Some(b))    => Some((b, b))
      case (None, None)       => None
    span match
      case None                                        => asOf.fold(GeoTime.Always)(GeoTime.At(_))
      case Some((lo, hi)) if pick.snapshot || lo == hi => GeoTime.At(yearOf(pick, lo, hi))
      case Some((lo, hi))                              => GeoTime.Within(lo, hi, latest = pick == Latest)
