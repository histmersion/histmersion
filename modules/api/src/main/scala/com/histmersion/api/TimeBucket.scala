package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class TimeBucket(start: Long, end: Long, count: Int) derives KindlingsCodecAsObject, KindlingsSchema
object TimeBucket:
  given Schema[TimeBucket] = summon[KindlingsSchema[TimeBucket]].schema
