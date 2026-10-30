package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

final case class EventPut(
    label: String,
    kind: String = "event",
    timeSpan: TimeSpan = TimeSpan(),
    historicity: String = "attested",
    geometry: Option[String] = None,
    placeId: Option[String] = None,
    parentId: Option[String] = None,
    description: Option[String] = None,
    citekey: Option[String] = None,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil,
    scale: Option[String] = None
) derives KindlingsSchema
object EventPut:
  given Schema[EventPut] = summon[KindlingsSchema[EventPut]].schema

  given Codec.AsObject[EventPut] = StrictJson.codec(KindlingsCodecAsObject.derived[EventPut])
