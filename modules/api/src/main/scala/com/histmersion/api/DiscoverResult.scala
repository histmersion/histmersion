package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class DiscoverResult(query: String, count: Int, works: List[WorkRef])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object DiscoverResult:
  given Schema[DiscoverResult] = summon[KindlingsSchema[DiscoverResult]].schema
