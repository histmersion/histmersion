package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventRederiveRequest(provider: Option[String] = None, dryRun: Boolean = false)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventRederiveRequest:
  given Schema[EventRederiveRequest] = summon[KindlingsSchema[EventRederiveRequest]].schema
