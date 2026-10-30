package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class AssessRequest(id: String, apply: Boolean = false, forceStatus: Boolean = false)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object AssessRequest:
  given Schema[AssessRequest] = summon[KindlingsSchema[AssessRequest]].schema
