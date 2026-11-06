package com.histmersion.api.research
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum DriftKind(val wire: DriftKind.Wire):
  case Unplaced extends DriftKind(_root_.com.histmersion.api.research.DriftKind.Wire("unplaced"))

  case ReasonForCited extends DriftKind(_root_.com.histmersion.api.research.DriftKind.Wire("reason-for-cited"))

  case ReasonOutside extends DriftKind(_root_.com.histmersion.api.research.DriftKind.Wire("reason-outside-filter"))

object DriftKind:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given WireEnumValues[DriftKind]:
    def all: Array[DriftKind] = DriftKind.values
    def wire(e: DriftKind): com.histmersion.api.research.DriftKind.WireResult = e.wire
    def fromWire(s: com.histmersion.api.research.DriftKind.FromWireS): Option[DriftKind] = DriftKind.values.find(_.wire == s.trim.toLowerCase)
  given Encoder[DriftKind] = WireEnum.encoder
  given Decoder[DriftKind] = WireEnum.decoder
  given Schema[DriftKind] = WireEnum.schema
