package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.catalogue.{EventEdge, EventParticipant}
import com.histmersion.api.research.Article
import io.circe.{Decoder, Encoder}

final case class DeleteBlocker(kind: DeleteBlocker.Kind, id: String, title: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object DeleteBlocker:
  given Schema[DeleteBlocker] = summon[KindlingsSchema[DeleteBlocker]].schema

  enum Kind(val wire: String, val label: String):
    case Article extends Kind("article", "cited by article")
    case Event extends Kind("event", "primary source of event")
    case EventEdge extends Kind("event-edge", "source of event relation")
    case EventParticipant extends Kind("event-participant", "source of event participant")
    case Job extends Kind("job", "still being written by job")

  object Kind:
    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): String = e.wire
      def fromWire(s: String): Option[Kind] = Kind.values.find(_.wire == s.trim.toLowerCase)
    given Encoder[Kind] = WireEnum.encoder
    given Decoder[Kind] = WireEnum.decoder
    given Schema[Kind] = WireEnum.schema
