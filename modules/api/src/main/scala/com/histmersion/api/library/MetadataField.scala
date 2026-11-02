package com.histmersion.api.library
import scala.annotation.tailrec
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum MetadataField:
  case Year, Venue, Doi, Provenance, CalibreId

object MetadataField:
  def wireOf(f: MetadataField): String = f match
    case Year       => "year"
    case Venue      => "venue"
    case Doi        => "doi"
    case Provenance => "provenance"
    case CalibreId  => "calibreId"

  def fromWire(s: String): Option[MetadataField] = values.find(wireOf(_) == s)

  given WireEnumValues[MetadataField]:
    def all: Array[MetadataField] = MetadataField.values
    def wire(e: MetadataField): String = wireOf(e)
    def fromWire(s: String): Option[MetadataField] = MetadataField.fromWire(s)

  given Encoder[MetadataField] = WireEnum.encoder
  given Decoder[MetadataField] = WireEnum.decoder
  given Schema[MetadataField] = WireEnum.schema
