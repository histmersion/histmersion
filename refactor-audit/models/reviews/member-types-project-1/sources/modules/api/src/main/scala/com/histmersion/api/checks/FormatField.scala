package com.histmersion.api.checks
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum FormatField:
  case Body, Summary, Notes

object FormatField:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given WireEnumValues[FormatField]:
    def all: Array[FormatField] = FormatField.values
    def wire(e: FormatField): com.histmersion.api.checks.FormatField.WireResult = e.toString.toLowerCase(java.util.Locale.ROOT)
    def fromWire(s: com.histmersion.api.checks.FormatField.FromWireS): Option[FormatField] = FormatField.values.find(_.toString.equalsIgnoreCase(s))
  given Encoder[FormatField] = WireEnum.encoder
  given Decoder[FormatField] = WireEnum.decoder
  given Schema[FormatField] = WireEnum.schema
