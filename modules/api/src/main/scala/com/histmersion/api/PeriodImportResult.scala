package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PeriodImportResult(
    periods: Int,
    roots: Int = 0,
    presets: Int = 0,
    skipped: List[SkippedPeriod] = Nil,
    skippedCount: Int = 0,
    removed: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PeriodImportResult:
  given Schema[PeriodImportResult] = summon[KindlingsSchema[PeriodImportResult]].schema
