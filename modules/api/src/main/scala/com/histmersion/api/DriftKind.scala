package com.histmersion.api

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

/** How the stored coverage has drifted from the live corpus and the body. */
enum DriftKind(val wire: String):
  /** A source the filter returns that is neither cited nor given a reason. */
  case Unplaced extends DriftKind("unplaced")

  /** A reason for a source the body now cites (it is not shown; it is stale). */
  case ReasonForCited extends DriftKind("reason-for-cited")

  /** A reason for a source the filter no longer returns. */
  case ReasonOutside extends DriftKind("reason-outside-filter")

object DriftKind:
  given WireEnumValues[DriftKind]:
    def all: Array[DriftKind] = DriftKind.values
    def wire(e: DriftKind): String = e.wire
    def fromWire(s: String): Option[DriftKind] = DriftKind.values.find(_.wire == s.trim.toLowerCase)
  given Encoder[DriftKind] = WireEnum.encoder
  given Decoder[DriftKind] = WireEnum.decoder
  given Schema[DriftKind] = WireEnum.schema
