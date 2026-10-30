package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class LabelRename(to: String) derives KindlingsCodecAsObject, KindlingsSchema
object LabelRename:
  given Schema[LabelRename] = summon[KindlingsSchema[LabelRename]].schema
