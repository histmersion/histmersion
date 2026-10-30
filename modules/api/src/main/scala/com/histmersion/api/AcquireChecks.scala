package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The acquisition list's legal re-check on the wire (docs/research-process-managers.md "As built: the acquisition
  * list's re-check"): what an item's last check found (in `GET /api/queue/acquire`), a pass run by hand (`POST
  * /api/queue/acquire/recheck`) and the status line (`agent autopilot status`, `about`). The rules are in `core`
  * (`AcquireRecheck`), the pass in the server's managers' tick.
  */
object AcquireChecks:

  /** One acquisition item's last check: when (ISO instant), what it found (`closed` | `fetched` | `no-copy` |
    * `failed`), the short reason shown with the item, and how many times it was checked.
    */
  final case class AcquireCheck(item: Int, checkedAt: String, outcome: String, reason: String, checks: Int = 1)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object AcquireCheck:
    given Schema[AcquireCheck] = summon[KindlingsSchema[AcquireCheck]].schema

  /** `POST /api/queue/acquire/recheck`: a pass now. `dryRun` = list the items it would close and the due ones it would
    * try (no fetcher is asked, nothing is stored); `limit` = at most this many tried (default
    * `acquire.recheck.per-pass`; the day's cap and the shared allowance still hold); `id` = this one item only, due or
    * not; `by` = who asked; `all` = every open item that names a DOI, due or not, past the caps and the shared
    * allowance. A pass of more than one item runs as a job (`job`).
    */
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

  /** One item of a pass: `closed` (in the corpus with its original), `fetched` (a legal copy found and converting),
    * `no-copy`, `failed`, `no-doi`; in a dry run `would-close`, `would-try`, `due` (past the pass's caps). `citekey` =
    * the source it was closed by or fetched into.
    */
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

  /** What a pass did (a dry run: would do). `open` = the open acquisition items, `withDoi` = those naming a DOI, `due` =
    * the ones due for a try (with `all`: every one naming a DOI), `allowance` = how many this pass could try; `note` =
    * why it did less than asked (draining, the caps, not connected); `job` = the job a pass of more than one item runs
    * as (the counts and rows are then its progress, not here).
    */
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

  /** What a re-check job does (`jobs.detail`): every open item naming a DOI (`all`), or at most `upTo` due ones;
    * `since` (ISO instant) = when it first started — an item checked since was checked by it, and a job that `resumes`
    * a stopped one (its id) skips those; `by` = who asked.
    */
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

    /** The `jobs.kind` of a re-check job. */
    val JobKind = com.histmersion.api.JobKind.AcquireRecheck.wire

    /** `the acquisition list's legal re-check of every open item with a DOI (resuming job …)`. Pure. */
    def describe(j: RecheckJob): String =
      "the acquisition list's legal re-check of " +
        (if j.all then "every open item with a DOI" else s"up to ${Plural.count(j.upTo, "due item")}") +
        j.resumes.fold("")(id => s" (resuming job $id)")

  /** The re-check as the status shows it: on or off, the open items naming a DOI, the ones due, the items tried today
    * and the day's cap, the copies found today, the items closed and copies found in the last 7 days, the window
    * (`every-days`), when the next pass runs, why it waits (off, draining, …), and the re-check job running or stopped.
    */
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
      /** A re-check job running (`job <id> running: <its progress>`), or one stopped that a tick resumes. */
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
