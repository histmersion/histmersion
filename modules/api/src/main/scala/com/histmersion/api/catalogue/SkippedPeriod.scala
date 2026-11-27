package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobDetail
import com.histmersion.lint.{tolerated, LintRule}

final case class SkippedPeriod(
    id: SkippedPeriod.Id,
    label: SkippedPeriod.Label,
    reason: Period.Skip,
    detail: SkippedPeriod.Detail
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
object SkippedPeriod:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  val Skipped: JobDetail.Field[List[SkippedPeriod]] =
    new com.histmersion.api.jobs.JobDetail.Field(JobDetail.Field.Name("skippedPeriods"), Nil)

  /** How many period presets an init job skipped, in its detail. */
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  val SkippedCount: JobDetail.Field[Period.ImportResult.SkippedCount] =
    new com.histmersion.api.jobs.JobDetail.Field(
      JobDetail.Field.Name("skippedPeriodCount"),
      Period.ImportResult.SkippedCount(0)
    )
