package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobDetail

final case class SkippedPeriod(id: SkippedPeriod.Id, label: SkippedPeriod.Label, reason: Period.Skip, detail: SkippedPeriod.Detail)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object SkippedPeriod:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Detail = Detail.T
  object Detail extends RefinedType[String, Pure]

  val Skipped: JobDetail.Field[List[SkippedPeriod]] =
    new com.histmersion.api.jobs.JobDetail.Field(JobDetail.Field.Name("skippedPeriods"), Nil)

  /** How many period presets an init job skipped, in its detail. */
  val SkippedCount: JobDetail.Field[Period.ImportResult.SkippedCount] =
    new com.histmersion.api.jobs.JobDetail.Field(JobDetail.Field.Name("skippedPeriodCount"), Period.ImportResult.SkippedCount(0))
