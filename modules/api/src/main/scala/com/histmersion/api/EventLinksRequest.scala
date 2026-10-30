package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

final case class EventLinksRequest(recompute: Boolean = false, confirm: List[String] = Nil, reject: List[String] = Nil)
    derives KindlingsSchema
object EventLinksRequest:
  given Schema[EventLinksRequest] = summon[KindlingsSchema[EventLinksRequest]].schema
  given Codec.AsObject[EventLinksRequest] = StrictJson.codec(KindlingsCodecAsObject.derived[EventLinksRequest])
