package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class RenameRequest(newCitekey: String) derives KindlingsCodecAsObject, KindlingsSchema
object RenameRequest:
  given Schema[RenameRequest] = summon[KindlingsSchema[RenameRequest]].schema
