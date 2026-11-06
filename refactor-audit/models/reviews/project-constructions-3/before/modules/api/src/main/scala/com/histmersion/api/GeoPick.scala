package com.histmersion.api

import sttp.tapir.Schema
import com.histmersion.api.catalogue.GeoTime

enum GeoPick:
  case Mid, Start, End, Earliest, Latest

  def wire: com.histmersion.api.GeoPick.WireResult = this match
    case Mid      => _root_.com.histmersion.api.GeoPick.WireResult("mid")
    case Start    => _root_.com.histmersion.api.GeoPick.WireResult("start")
    case End      => _root_.com.histmersion.api.GeoPick.WireResult("end")
    case Earliest => _root_.com.histmersion.api.GeoPick.WireResult("earliest")
    case Latest   => _root_.com.histmersion.api.GeoPick.WireResult("latest")

  def label: com.histmersion.api.GeoPick.LabelResult = this match
    case Mid      => _root_.com.histmersion.api.GeoPick.LabelResult("States at midpoint")
    case Start    => _root_.com.histmersion.api.GeoPick.LabelResult("States at start")
    case End      => _root_.com.histmersion.api.GeoPick.LabelResult("States at end")
    case Earliest => _root_.com.histmersion.api.GeoPick.LabelResult("All states in period (earliest shape)")
    case Latest   => _root_.com.histmersion.api.GeoPick.LabelResult("All states in period (latest shape)")

  def snapshot: com.histmersion.api.GeoPick.SnapshotResult = this match
    case Mid | Start | End => _root_.com.histmersion.api.GeoPick.SnapshotResult(true)
    case _                 => _root_.com.histmersion.api.GeoPick.SnapshotResult(false)

object GeoPick:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelResult = LabelResult.T
  object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SnapshotResult = SnapshotResult.T
  object SnapshotResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearOfResult = YearOfResult.T
  object YearOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearOfLo = YearOfLo.T
  object YearOfLo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearOfHi = YearOfHi.T
  object YearOfHi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveAsOf = ResolveAsOf.T
  object ResolveAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveFrom = ResolveFrom.T
  object ResolveFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveTo = ResolveTo.T
  object ResolveTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.GeoPick.ParseS): Option[GeoPick] = values.find(_.wire == (s).value.trim.toLowerCase(java.util.Locale.ROOT))

  given WireEnumValues[GeoPick]:
    def all: Array[GeoPick] = GeoPick.values
    def wire(e: GeoPick): com.histmersion.api.GeoPick.WireResult = e.wire
    def fromWire(s: com.histmersion.api.GeoPick.FromWireS): Option[GeoPick] = parse(s)
  given io.circe.Encoder[GeoPick] = WireEnum.encoder
  given io.circe.Decoder[GeoPick] = WireEnum.decoder
  given Schema[GeoPick] = WireEnum.schema

  def yearOf(pick: GeoPick, lo: com.histmersion.api.GeoPick.YearOfLo, hi: com.histmersion.api.GeoPick.YearOfHi): com.histmersion.api.GeoPick.YearOfResult = pick match
    case Start => lo
    case End   => hi
    case _     => ((lo).value + (hi).value) / 2

  def resolve(asOf: Option[com.histmersion.api.GeoPick.ResolveAsOf], from: Option[com.histmersion.api.GeoPick.ResolveFrom], to: Option[com.histmersion.api.GeoPick.ResolveTo], pick: GeoPick): GeoTime =
    val span = (from, to) match
      case (Some(a), Some(b)) => Some(((a).value.min((b).value), (a).value.max((b).value)))
      case (Some(a), None)    => Some((a, a))
      case (None, Some(b))    => Some((b, b))
      case (None, None)       => None
    span match
      case None                                        => asOf.fold(GeoTime.Always)(GeoTime.At(_))
      case Some((lo, hi)) if pick.snapshot || lo == hi => GeoTime.At(yearOf(pick, lo, hi))
      case Some((lo, hi))                              => GeoTime.Within(lo, hi, latest = _root_.com.histmersion.api.catalogue.GeoTime.Within.Latest(pick == Latest))
