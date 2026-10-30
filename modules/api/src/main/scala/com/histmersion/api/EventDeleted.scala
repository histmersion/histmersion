package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Confirmation of event deletion. */
final case class EventDeleted(id: String, deleted: Boolean) derives KindlingsCodecAsObject, KindlingsSchema
object EventDeleted:
  given Schema[EventDeleted] = summon[KindlingsSchema[EventDeleted]].schema
