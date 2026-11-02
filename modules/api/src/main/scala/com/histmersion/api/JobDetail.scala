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

  /** What a job is about: its source, else what its detail says (each kind's owner says how). */
  def subject(j: JobInfo): Option[String] = RegisteredJobKinds.all.subject(j)
