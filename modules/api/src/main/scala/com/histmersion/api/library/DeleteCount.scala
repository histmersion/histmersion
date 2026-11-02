package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class DeleteCount(part: DeletePart, rows: Int) derives KindlingsCodecAsObject, KindlingsSchema
object DeleteCount:
  given Schema[DeleteCount] = summon[KindlingsSchema[DeleteCount]].schema
