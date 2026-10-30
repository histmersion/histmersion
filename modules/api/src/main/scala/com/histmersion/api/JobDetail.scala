package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class JobDetail(
    init: Option[InitPlan] = None,
    skippedPeriods: List[SkippedPeriod] = Nil,
    skippedPeriodCount: Int = 0,
    planHash: Option[String] = None,
    vacuum: Option[VacuumPlan] = None,
    media: Option[String] = None,
    events: Option[String] = None,
    eventImport: Option[EventImportReport] = None,
    eventRederive: Option[EventRederiveReport] = None,
    about: Option[String] = None,
    recheck: Option[AcquireChecks.RecheckJob] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object JobDetail:
  given Schema[JobDetail] = summon[KindlingsSchema[JobDetail]].schema

  def subject(j: JobInfo): Option[String] =
    j.citekey
      .orElse(j.detail.flatMap(_.init).map(InitPlan.describe))
      .orElse(j.detail.flatMap(_.planHash).map(h => s"merge plan $h"))
      .orElse(j.detail.flatMap(_.vacuum).map(Vacuum.describe))
      .orElse(j.detail.flatMap(_.media))
      .orElse(j.detail.flatMap(_.events))
      .orElse(j.detail.flatMap(_.about))
      .orElse(j.detail.flatMap(_.recheck).map(AcquireChecks.RecheckJob.describe))
