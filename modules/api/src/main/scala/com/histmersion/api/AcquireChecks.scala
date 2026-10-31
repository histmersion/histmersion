package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object AcquireChecks:

  final case class AcquireCheck(item: Int, checkedAt: String, outcome: String, reason: String, checks: Int = 1)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object AcquireCheck:
    given Schema[AcquireCheck] = summon[KindlingsSchema[AcquireCheck]].schema

  final case class RecheckRequest(
      dryRun: Boolean = false,
      limit: Option[Int] = None,
      id: Option[Int] = None,
      by: Option[String] = None,
      all: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object RecheckRequest:
    given Schema[RecheckRequest] = summon[KindlingsSchema[RecheckRequest]].schema

  final case class RecheckRow(
      item: Int,
      doi: Option[String] = None,
      action: String = "",
      detail: String = "",
      citekey: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object RecheckRow:
    given Schema[RecheckRow] = summon[KindlingsSchema[RecheckRow]].schema

  final case class RecheckResult(
      dryRun: Boolean,
      open: Int = 0,
      withDoi: Int = 0,
      due: Int = 0,
      allowance: Int = 0,
      closed: Int = 0,
      fetched: Int = 0,
      noCopy: Int = 0,
      failed: Int = 0,
      rows: List[RecheckRow] = Nil,
      note: Option[String] = None,
      job: Option[JobInfo] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object RecheckResult:
    given Schema[RecheckResult] = summon[KindlingsSchema[RecheckResult]].schema

  final case class RecheckJob(
      all: Boolean,
      upTo: Int = 0,
      since: String = "",
      by: String = "",
      resumes: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object RecheckJob:
    given Schema[RecheckJob] = summon[KindlingsSchema[RecheckJob]].schema

    val JobKind = com.histmersion.api.JobKindsAdapter.AcquireRecheck.value

    def describe(j: RecheckJob): String =
      "the acquisition list's legal re-check of " +
        (if j.all then "every open item with a DOI" else s"up to ${Plural.count(j.upTo, "due item")}") +
        j.resumes.fold("")(id => s" (resuming job $id)")

  final case class RecheckStatus(
      enabled: Boolean,
      withDoi: Int = 0,
      due: Int = 0,
      triedToday: Int = 0,
      perDay: Int = 0,
      fetchedToday: Int = 0,
      closedWeek: Int = 0,
      fetchedWeek: Int = 0,
      everyDays: Int = 0,
      nextPass: Option[String] = None,
      note: Option[String] = None,
      job: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object RecheckStatus:
    given Schema[RecheckStatus] = summon[KindlingsSchema[RecheckStatus]].schema

  val Closed = "closed"
  val Fetched = "fetched"
  val NoCopy = "no-copy"
  val Failed = "failed"
  val NoDoi = "no-doi"
  val WouldClose = "would-close"
  val WouldTry = "would-try"
  val Due = "due"
