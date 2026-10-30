package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One bar of the timeline strip: an inclusive `[start, end]` year range and how many units (sources by default) have a
  * period overlapping it. See [[Aggregate]].
  */
final case class TimeBucket(start: Long, end: Long, count: Int) derives KindlingsCodecAsObject, KindlingsSchema
object TimeBucket:
  given Schema[TimeBucket] = summon[KindlingsSchema[TimeBucket]].schema
