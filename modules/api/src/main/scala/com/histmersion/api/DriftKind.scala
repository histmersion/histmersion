package com.histmersion.api

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

enum DriftKind(val wire: String):
  case Unplaced extends DriftKind("unplaced")

  case ReasonForCited extends DriftKind("reason-for-cited")

  case ReasonOutside extends DriftKind("reason-outside-filter")

object DriftKind:
  given WireEnumValues[DriftKind]:
    def all: Array[DriftKind] = DriftKind.values
    def wire(e: DriftKind): String = e.wire
    def fromWire(s: String): Option[DriftKind] = DriftKind.values.find(_.wire == s.trim.toLowerCase)
  given Encoder[DriftKind] = WireEnum.encoder
  given Decoder[DriftKind] = WireEnum.decoder
  given Schema[DriftKind] = WireEnum.schema
