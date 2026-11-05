package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class SkippedPeriod(id: SkippedPeriod.Id, label: SkippedPeriod.Label, reason: Period.Skip, detail: SkippedPeriod.Detail)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object SkippedPeriod:


  /** The period presets an init job skipped (the first of them), in its detail. */
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Detail = Detail.T
  object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Skipped: com.histmersion.api.jobs.JobDetail.Field[List[SkippedPeriod]] =
    com.histmersion.api.jobs.JobDetail.Field("skippedPeriods", Nil)

  /** How many period presets an init job skipped, in its detail. */
  val SkippedCount: com.histmersion.api.jobs.JobDetail.Field[Int] =
    com.histmersion.api.jobs.JobDetail.Field("skippedPeriodCount", 0)
