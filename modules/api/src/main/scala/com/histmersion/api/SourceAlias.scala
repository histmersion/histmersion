package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class SourceAlias(alias: String, citekey: String) derives KindlingsCodecAsObject, KindlingsSchema
object SourceAlias:
  given Schema[SourceAlias] = summon[KindlingsSchema[SourceAlias]].schema
