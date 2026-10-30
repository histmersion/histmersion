package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class CorpusCounts(
    sources: Int,
    findings: Int,
    articles: Int,
    events: Int,
    places: Int,
    periods: Int,
    presets: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CorpusCounts:
  given Schema[CorpusCounts] = summon[KindlingsSchema[CorpusCounts]].schema
