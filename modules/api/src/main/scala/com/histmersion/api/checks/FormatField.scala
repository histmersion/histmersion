package com.histmersion.api.checks
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum FormatField:
  case Body, Summary, Notes

object FormatField:
  given WireEnumValues[FormatField]:
    def all: Array[FormatField] = FormatField.values
    def wire(e: FormatField): String = e.toString.toLowerCase(java.util.Locale.ROOT)
    def fromWire(s: String): Option[FormatField] = FormatField.values.find(_.toString.equalsIgnoreCase(s))
  given Encoder[FormatField] = WireEnum.encoder
  given Decoder[FormatField] = WireEnum.decoder
  given Schema[FormatField] = WireEnum.schema
