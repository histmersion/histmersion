package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class AssetsStored(citekey: String, stored: Int) derives KindlingsCodecAsObject, KindlingsSchema
object AssetsStored:
  given Schema[AssetsStored] = summon[KindlingsSchema[AssetsStored]].schema
