package com.histmersion
package api.checks
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure

enum FormatField derives CanEqual:
  case Body, Summary, Notes

object FormatField:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[FormatField]:
    def all: Array[FormatField] = values
    def wire(e: FormatField): WireEnumValues.Wire =
      WireEnumValues.Wire((Wire(e.toString.toLowerCase(java.util.Locale.ROOT))).value)
    def fromWire(s: WireEnumValues.Wire): Option[FormatField] = values.find(_.toString.equalsIgnoreCase((s).value))
  given JsonValueCodec[FormatField] = WireEnum.codec
  given Schema[FormatField] = WireEnum.schema
