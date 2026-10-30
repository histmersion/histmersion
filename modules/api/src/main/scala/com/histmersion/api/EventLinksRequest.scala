package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

/** `POST /api/events/{id}/links`: `recompute` the candidates; `confirm` sources (each becomes an attestation from its
  * best passage — refused for a source with no passage naming the event); `reject` sources (they stop being proposed).
  */
final case class EventLinksRequest(recompute: Boolean = false, confirm: List[String] = Nil, reject: List[String] = Nil)
    derives KindlingsSchema
object EventLinksRequest:
  given Schema[EventLinksRequest] = summon[KindlingsSchema[EventLinksRequest]].schema
  given Codec.AsObject[EventLinksRequest] = StrictJson.codec(KindlingsCodecAsObject.derived[EventLinksRequest])
