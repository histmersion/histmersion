package com.histmersion.api.catalogue
import sttp.tapir.Schema
import com.histmersion.WireEnumValues
import com.histmersion.api.{WireEnum, YearFmt}
import com.histmersion.api.given

enum PlaceDating:
  case Stated, EndOnly, Unknown

  def wire: String = this match
    case Stated  => "stated"
    case EndOnly => "end-only"
    case Unknown => "unknown"

object PlaceDating:
  def parse(s: String): Option[PlaceDating] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  given WireEnumValues[PlaceDating]:
    def all: Array[PlaceDating] = PlaceDating.values
    def wire(e: PlaceDating): String = e.wire
    def fromWire(s: String): Option[PlaceDating] = parse(s)
  given io.circe.Encoder[PlaceDating] = WireEnum.encoder
  given io.circe.Decoder[PlaceDating] = WireEnum.decoder
  given Schema[PlaceDating] = WireEnum.schema

  def describe(dating: PlaceDating, start: Option[Long], end: Option[Long]): String = dating match
    case Unknown => "date unknown"
    case EndOnly => end.fold("date unknown")(e => s"end ${YearFmt(e)}, start unknown")
    case Stated  => YearFmt.openPeriod(start, end, none = "")
