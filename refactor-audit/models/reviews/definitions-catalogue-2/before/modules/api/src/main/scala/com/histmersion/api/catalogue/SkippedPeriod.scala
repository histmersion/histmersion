package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class SkippedPeriod(id: String, label: String, reason: Period.Skip, detail: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object SkippedPeriod:


  /** The period presets an init job skipped (the first of them), in its detail. */
  val Skipped: com.histmersion.api.jobs.JobDetail.Field[List[SkippedPeriod]] =
    com.histmersion.api.jobs.JobDetail.Field("skippedPeriods", Nil)

  /** How many period presets an init job skipped, in its detail. */
  val SkippedCount: com.histmersion.api.jobs.JobDetail.Field[Int] =
    com.histmersion.api.jobs.JobDetail.Field("skippedPeriodCount", 0)
