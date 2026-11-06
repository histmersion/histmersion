package com.histmersion.api.checks
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum FormatSeverity:
  case Error, Warning

object FormatSeverity:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given WireEnumValues[FormatSeverity]:
    def all: Array[FormatSeverity] = FormatSeverity.values
    def wire(e: FormatSeverity): _root_.com.histmersion.api.WireEnumValues.WireResult = _root_.com.histmersion.api.WireEnumValues.WireResult((_root_.com.histmersion.api.checks.FormatSeverity.WireResult(e.toString.toLowerCase(java.util.Locale.ROOT))).value)
    def fromWire(s: _root_.com.histmersion.api.WireEnumValues.FromWireS): _root_.scala.Option[FormatSeverity] = FormatSeverity.values.find(_.toString.equalsIgnoreCase((s).value))
  given Encoder[FormatSeverity] = WireEnum.encoder
  given Decoder[FormatSeverity] = WireEnum.decoder
  given Schema[FormatSeverity] = WireEnum.schema
