package com.histmersion.api.catalogue
import sttp.tapir.Schema
import com.histmersion.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum PlaceTimeBasis:
  case Dated, Timeless, Modern, Grouping, Findings

  def wire: String = this match
    case Dated    => "dated"
    case Timeless => "timeless"
    case Modern   => "modern"
    case Grouping => "grouping"
    case Findings => "findings"

object PlaceTimeBasis:
  def parse(s: String): Option[PlaceTimeBasis] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  given WireEnumValues[PlaceTimeBasis]:
    def all: Array[PlaceTimeBasis] = PlaceTimeBasis.values
    def wire(e: PlaceTimeBasis): String = e.wire
    def fromWire(s: String): Option[PlaceTimeBasis] = parse(s)
  given io.circe.Encoder[PlaceTimeBasis] = WireEnum.encoder
  given io.circe.Decoder[PlaceTimeBasis] = WireEnum.decoder
  given Schema[PlaceTimeBasis] = WireEnum.schema

  def of(kind: String, modern: Boolean, imported: Boolean, undated: Boolean, hasGeometry: Boolean): PlaceTimeBasis =
    if !imported && (kind == com.histmersion.domain.catalogue.Place.Kind.Feature || (modern && kind == com.histmersion.domain.catalogue.Place.Kind.Region))
    then Timeless
    else if modern then Modern
    else if undated && !hasGeometry then Grouping
    else if undated then Findings
    else Dated

  def visible(basis: PlaceTimeBasis, hi: Long, modernFrom: Long, overlaps: => Boolean, evidence: => Boolean): Boolean =
    basis match
      case Timeless | Grouping => true
      case Modern              => hi >= modernFrom
      case Dated               => overlaps
      case Findings            => evidence

  val FindingsNote: String = "dated by its findings — the place has no date of its own"
