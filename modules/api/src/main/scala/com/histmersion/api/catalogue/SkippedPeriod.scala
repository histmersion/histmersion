package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class SkippedPeriod(id: SkippedPeriod.Id, label: SkippedPeriod.Label, reason: Period.Skip, detail: SkippedPeriod.Detail)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object SkippedPeriod:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Detail = Detail.T
  object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Skipped: com.histmersion.api.jobs.JobDetail.Field[List[SkippedPeriod]] =
    com.histmersion.api.jobs.JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("skippedPeriods"), Nil)

  /** How many period presets an init job skipped, in its detail. */
  val SkippedCount: com.histmersion.api.jobs.JobDetail.Field[com.histmersion.api.catalogue.Period.ImportResult.SkippedCount] =
    com.histmersion.api.jobs.JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("skippedPeriodCount"), _root_.com.histmersion.api.catalogue.Period.ImportResult.SkippedCount(0))
