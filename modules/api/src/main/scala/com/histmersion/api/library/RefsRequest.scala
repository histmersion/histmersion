package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class RefsRequest(id: String, apply: Boolean = false) derives KindlingsCodecAsObject, KindlingsSchema
object RefsRequest:
  given Schema[RefsRequest] = summon[KindlingsSchema[RefsRequest]].schema
