package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class SeedRequest(dir: String, force: Boolean = false) derives KindlingsCodecAsObject, KindlingsSchema
object SeedRequest:
  given Schema[SeedRequest] = summon[KindlingsSchema[SeedRequest]].schema
