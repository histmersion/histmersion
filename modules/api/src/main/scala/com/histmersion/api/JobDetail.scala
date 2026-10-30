package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Structured facts about a job beyond its citekey (`jobs.detail`): what an init job imports, and the PeriodO
  * definitions an import left out (#771) — how many, and which up to a limit.
  */
final case class JobDetail(
    init: Option[InitPlan] = None,
    skippedPeriods: List[SkippedPeriod] = Nil,
    skippedPeriodCount: Int = 0,
    /** A merge apply's: the plan it carries out (its hash). */
    planHash: Option[String] = None,
    /** A vacuum job's: the tables and whether it analyzes (#1445). */
    vacuum: Option[VacuumPlan] = None,
    /** A media job's: what it brings in, in words ("Wikidata pictures of every place not asked yet"). */
    media: Option[String] = None,
    /** An events job's (#1449): what it does, in words ("Wikidata events, dry run"). */
    events: Option[String] = None,
    /** An event import's report, left on the job when it ends. */
    eventImport: Option[EventImportReport] = None,
    /** An event re-derivation's report (`events rederive`), left on the job when it ends. */
    eventRederive: Option[EventRederiveReport] = None,
    /** Any other background job's (#1946: the places fetch, land and hierarchy builds): what it does, in words ("fetch
      * cliopatria").
      */
    about: Option[String] = None,
    /** A re-check job's: what it re-checks, since when, and the stopped job it resumes. */
    recheck: Option[AcquireChecks.RecheckJob] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object JobDetail:
  given Schema[JobDetail] = summon[KindlingsSchema[JobDetail]].schema

  /** What a job works on, for a person: its citekey, what an init job imports, the plan a merge apply carries out, or
    * what a vacuum job vacuums.
    */
  def subject(j: JobInfo): Option[String] =
    j.citekey
      .orElse(j.detail.flatMap(_.init).map(InitPlan.describe))
      .orElse(j.detail.flatMap(_.planHash).map(h => s"merge plan $h"))
      .orElse(j.detail.flatMap(_.vacuum).map(Vacuum.describe))
      .orElse(j.detail.flatMap(_.media))
      .orElse(j.detail.flatMap(_.events))
      .orElse(j.detail.flatMap(_.about))
      .orElse(j.detail.flatMap(_.recheck).map(AcquireChecks.RecheckJob.describe))
