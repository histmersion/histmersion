package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class RenameCount(table: String, rows: Int) derives KindlingsCodecAsObject, KindlingsSchema
object RenameCount:
  given Schema[RenameCount] = summon[KindlingsSchema[RenameCount]].schema
