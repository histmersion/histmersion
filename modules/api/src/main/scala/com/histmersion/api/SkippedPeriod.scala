package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class SkippedPeriod(id: String, label: String, reason: PeriodSkip, detail: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object SkippedPeriod:
  given Schema[SkippedPeriod] = summon[KindlingsSchema[SkippedPeriod]].schema

  /** The period presets an init job skipped (the first of them), in its detail. */
  val Skipped: com.histmersion.util.jobs.JobDetailField[List[SkippedPeriod]] =
    com.histmersion.util.jobs.JobDetailField("skippedPeriods", Nil)

  /** How many period presets an init job skipped, in its detail. */
  val SkippedCount: com.histmersion.util.jobs.JobDetailField[Int] =
    com.histmersion.util.jobs.JobDetailField("skippedPeriodCount", 0)
