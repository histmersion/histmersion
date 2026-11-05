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
  type WireOfResult = WireOfResult.T
  object WireOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def wireOf(f: MetadataField): com.histmersion.api.library.MetadataField.WireOfResult = f match
    case Year       => "year"
    case Venue      => "venue"
    case Doi        => "doi"
    case Provenance => "provenance"
    case CalibreId  => "calibreId"

  def fromWire(s: com.histmersion.api.library.MetadataField.FromWireS): Option[MetadataField] = values.find(wireOf(_) == s)

  given WireEnumValues[MetadataField]:
    def all: Array[MetadataField] = MetadataField.values
    def wire(e: MetadataField): com.histmersion.api.library.MetadataField.WireResult = wireOf(e)
    def fromWire(s: com.histmersion.api.library.MetadataField.FromWireS): Option[MetadataField] = MetadataField.fromWire(s)

  given Encoder[MetadataField] = WireEnum.encoder
  given Decoder[MetadataField] = WireEnum.decoder
  given Schema[MetadataField] = WireEnum.schema
