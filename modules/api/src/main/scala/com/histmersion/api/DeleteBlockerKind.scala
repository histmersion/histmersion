package com.histmersion.api

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

/** What refuses a delete. */
enum DeleteBlockerKind(val wire: String, val label: String):
  case Article extends DeleteBlockerKind("article", "cited by article")
  case Event extends DeleteBlockerKind("event", "primary source of event")
  case EventEdge extends DeleteBlockerKind("event-edge", "source of event relation")
  case EventParticipant extends DeleteBlockerKind("event-participant", "source of event participant")
  case Job extends DeleteBlockerKind("job", "still being written by job")

object DeleteBlockerKind:
  given WireEnumValues[DeleteBlockerKind]:
    def all: Array[DeleteBlockerKind] = DeleteBlockerKind.values
    def wire(e: DeleteBlockerKind): String = e.wire
    def fromWire(s: String): Option[DeleteBlockerKind] = DeleteBlockerKind.values.find(_.wire == s.trim.toLowerCase)
  given Encoder[DeleteBlockerKind] = WireEnum.encoder
  given Decoder[DeleteBlockerKind] = WireEnum.decoder
  given Schema[DeleteBlockerKind] = WireEnum.schema
