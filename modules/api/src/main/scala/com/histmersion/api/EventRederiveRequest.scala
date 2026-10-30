package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventRederiveRequest(provider: Option[String] = None, dryRun: Boolean = false)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventRederiveRequest:
  given Schema[EventRederiveRequest] = summon[KindlingsSchema[EventRederiveRequest]].schema
