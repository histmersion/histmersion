package com.histmersion
package api.research
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

enum DriftKind(val wire: DriftKind.Wire):
  case Unplaced extends DriftKind(DriftKind.Wire("unplaced"))

  case ReasonForCited extends DriftKind(DriftKind.Wire("reason-for-cited"))

  case ReasonOutside extends DriftKind(DriftKind.Wire("reason-outside-filter"))

object DriftKind:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]


  given WireEnumValues[DriftKind]:
    def all: Array[DriftKind] = values
    def wire(e: DriftKind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    def fromWire(s: WireEnumValues.Wire): Option[DriftKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
  given Encoder[DriftKind] = WireEnum.encoder
  given Decoder[DriftKind] = WireEnum.decoder
  given Schema[DriftKind] = WireEnum.schema
