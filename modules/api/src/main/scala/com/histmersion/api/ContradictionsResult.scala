package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class ContradictionsResult(rows: List[ContradictionRow], total: Int)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object ContradictionsResult:
  given Schema[ContradictionsResult] = summon[KindlingsSchema[ContradictionsResult]].schema
