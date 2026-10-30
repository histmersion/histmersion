package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One PeriodO definition left out of an import: its id and label, why, and the numbers that say so. */
final case class SkippedPeriod(id: String, label: String, reason: PeriodSkip, detail: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object SkippedPeriod:
  given Schema[SkippedPeriod] = summon[KindlingsSchema[SkippedPeriod]].schema
