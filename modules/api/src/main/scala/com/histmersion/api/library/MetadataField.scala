package com.histmersion.api.library
import scala.annotation.tailrec
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum MetadataField:
  case Year, Venue, Doi, Provenance, CalibreId

object MetadataField:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def wireOf(f: MetadataField): com.histmersion.api.library.MetadataField.Wire = f match
    case Year       => _root_.com.histmersion.api.library.MetadataField.Wire("year")
    case Venue      => _root_.com.histmersion.api.library.MetadataField.Wire("venue")
    case Doi        => _root_.com.histmersion.api.library.MetadataField.Wire("doi")
    case Provenance => _root_.com.histmersion.api.library.MetadataField.Wire("provenance")
    case CalibreId  => _root_.com.histmersion.api.library.MetadataField.Wire("calibreId")

  def fromWire(s: com.histmersion.api.library.MetadataField.Wire): Option[MetadataField] = values.find(wireOf(_) == s)

  given WireEnumValues[MetadataField]:
    def all: Array[MetadataField] = MetadataField.values
    def wire(e: MetadataField): _root_.com.histmersion.api.WireEnumValues.Wire = _root_.com.histmersion.api.WireEnumValues.Wire((wireOf(e)).value)
    def fromWire(s: _root_.com.histmersion.api.WireEnumValues.Wire): _root_.scala.Option[MetadataField] = MetadataField.fromWire(_root_.com.histmersion.api.library.MetadataField.Wire((s).value))

  given Encoder[MetadataField] = WireEnum.encoder
  given Decoder[MetadataField] = WireEnum.decoder
  given Schema[MetadataField] = WireEnum.schema
