package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class CalibreGaps(count: Int, gaps: List[CalibreGap]) derives KindlingsCodecAsObject, KindlingsSchema
object CalibreGaps:
  given Schema[CalibreGaps] = summon[KindlingsSchema[CalibreGaps]].schema
