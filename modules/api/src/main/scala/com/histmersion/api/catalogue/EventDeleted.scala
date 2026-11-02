package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventDeleted(id: String, deleted: Boolean) derives KindlingsCodecAsObject, KindlingsSchema
object EventDeleted:
  given Schema[EventDeleted] = summon[KindlingsSchema[EventDeleted]].schema
