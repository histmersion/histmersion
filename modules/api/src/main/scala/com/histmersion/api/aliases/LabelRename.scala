package com.histmersion.api.aliases
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class LabelRename(to: String) derives KindlingsCodecAsObject, KindlingsSchema
object LabelRename:
  given Schema[LabelRename] = summon[KindlingsSchema[LabelRename]].schema
