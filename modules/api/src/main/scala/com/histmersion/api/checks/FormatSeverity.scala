package com.histmersion.api.checks
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum FormatSeverity:
  case Error, Warning

object FormatSeverity:
  given WireEnumValues[FormatSeverity]:
    def all: Array[FormatSeverity] = FormatSeverity.values
    def wire(e: FormatSeverity): String = e.toString.toLowerCase(java.util.Locale.ROOT)
    def fromWire(s: String): Option[FormatSeverity] = FormatSeverity.values.find(_.toString.equalsIgnoreCase(s))
  given Encoder[FormatSeverity] = WireEnum.encoder
  given Decoder[FormatSeverity] = WireEnum.decoder
  given Schema[FormatSeverity] = WireEnum.schema
