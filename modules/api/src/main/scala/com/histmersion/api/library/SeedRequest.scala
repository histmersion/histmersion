package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class SeedRequest(dir: String, force: Boolean = false) derives KindlingsCodecAsObject, KindlingsSchema
object SeedRequest:
  given Schema[SeedRequest] = summon[KindlingsSchema[SeedRequest]].schema
