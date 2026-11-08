package com.histmersion
package api.library
import scala.annotation.tailrec
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure

enum MetadataField derives CanEqual:
  case Year, Venue, Doi, Provenance, CalibreId

object MetadataField:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def wireOf(f: MetadataField): Wire = f match
    case Year       => Wire("year")
    case Venue      => Wire("venue")
    case Doi        => Wire("doi")
    case Provenance => Wire("provenance")
    case CalibreId  => Wire("calibreId")

  def fromWire(s: Wire): Option[MetadataField] = values.find(wireOf(_) == s)

  given WireEnumValues[MetadataField]:
    def all: Array[MetadataField] = values
    def wire(e: MetadataField): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
    def fromWire(s: WireEnumValues.Wire): Option[MetadataField] = MetadataField.fromWire(Wire((s).value))

  given Encoder[MetadataField] = WireEnum.encoder
  given Decoder[MetadataField] = WireEnum.decoder
  given Schema[MetadataField] = WireEnum.schema
