package com.histmersion
package api.checks
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure

enum FormatSeverity:
  case Error, Warning

object FormatSeverity:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[FormatSeverity]:
    def all: Array[FormatSeverity] = values
    def wire(e: FormatSeverity): WireEnumValues.Wire = WireEnumValues.Wire((Wire(e.toString.toLowerCase(java.util.Locale.ROOT))).value)
    def fromWire(s: WireEnumValues.Wire): Option[FormatSeverity] = values.find(_.toString.equalsIgnoreCase((s).value))
  given Encoder[FormatSeverity] = WireEnum.encoder
  given Decoder[FormatSeverity] = WireEnum.decoder
  given Schema[FormatSeverity] = WireEnum.schema
