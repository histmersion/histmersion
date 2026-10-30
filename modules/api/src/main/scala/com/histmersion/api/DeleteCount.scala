package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class DeleteCount(part: DeletePart, rows: Int) derives KindlingsCodecAsObject, KindlingsSchema
object DeleteCount:
  given Schema[DeleteCount] = summon[KindlingsSchema[DeleteCount]].schema
