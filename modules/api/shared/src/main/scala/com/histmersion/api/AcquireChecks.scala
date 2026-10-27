package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The acquisition list's legal re-check on the wire (docs/research-process-managers.md "As built: the
  * acquisition list's re-check"): what an item's last check found (in `GET /api/queue/acquire`), a pass
  * run by hand (`POST /api/queue/acquire/recheck`) and the status line (`agent autopilot status`, `about`).
  * The rules are in `core/jvm` (`AcquireRecheck`), the pass in the server's managers' tick. */
object AcquireChecks:

  /** One acquisition item's last check: when (ISO instant), what it found (`closed` | `fetched` |
    * `no-copy` | `failed`), the short reason shown with the item, and how many times it was checked. */
  final case class AcquireCheck(item: Int, checkedAt: String, outcome: String, reason: String, checks: Int = 1)
      derives KindlingsCodecAsObject
  object AcquireCheck:
    given Schema[AcquireCheck] = KindlingsSchema.derived[AcquireCheck].schema

  /** `POST /api/queue/acquire/recheck`: a pass now. `dryRun` = list the items it would close and the
    * due ones it would try (no fetcher is asked, nothing is stored); `limit` = at most this many tried
    * (default `acquire.recheck.per-pass`; the day's cap and the shared allowance still hold); `id` = this
    * one item only, due or not; `by` = who asked. */
  final case class RecheckRequest(dryRun: Boolean = false, limit: Option[Int] = None, id: Option[Int] = None,
      by: Option[String] = None) derives KindlingsCodecAsObject
  object RecheckRequest:
    given Schema[RecheckRequest] = KindlingsSchema.derived[RecheckRequest].schema

  /** One item of a pass: `closed` (in the corpus with its original), `fetched` (a legal copy found and
    * converting), `no-copy`, `failed`, `no-doi`; in a dry run `would-close`, `would-try`, `due` (past the
    * pass's caps). `citekey` = the source it was closed by or fetched into. */
  final case class RecheckRow(item: Int, doi: Option[String] = None, action: String = "", detail: String = "",
      citekey: Option[String] = None) derives KindlingsCodecAsObject
  object RecheckRow:
    given Schema[RecheckRow] = KindlingsSchema.derived[RecheckRow].schema

  /** What a pass did (a dry run: would do). `open` = the open acquisition items, `withDoi` = those naming a
    * DOI, `due` = the ones due for a try, `allowance` = how many this pass could try; `note` = why it did
    * less than asked (draining, the caps, not connected). */
  final case class RecheckResult(dryRun: Boolean, open: Int = 0, withDoi: Int = 0, due: Int = 0, allowance: Int = 0,
      closed: Int = 0, fetched: Int = 0, noCopy: Int = 0, failed: Int = 0, rows: List[RecheckRow] = Nil,
      note: Option[String] = None) derives KindlingsCodecAsObject
  object RecheckResult:
    given Schema[RecheckResult] = KindlingsSchema.derived[RecheckResult].schema

  /** The re-check as the status shows it: on or off, the open items naming a DOI, the ones due, the items
    * tried today and the day's cap, the copies found today, the items closed and copies found in the last
    * 7 days, the window (`every-days`), when the next pass runs, and why it waits (off, draining, …). */
  final case class RecheckStatus(enabled: Boolean, withDoi: Int = 0, due: Int = 0, triedToday: Int = 0, perDay: Int = 0,
      fetchedToday: Int = 0, closedWeek: Int = 0, fetchedWeek: Int = 0, everyDays: Int = 0, nextPass: Option[String] = None,
      note: Option[String] = None) derives KindlingsCodecAsObject
  object RecheckStatus:
    given Schema[RecheckStatus] = KindlingsSchema.derived[RecheckStatus].schema

  val Closed     = "closed"
  val Fetched    = "fetched"
  val NoCopy     = "no-copy"
  val Failed     = "failed"
  val NoDoi      = "no-doi"
  val WouldClose = "would-close"
  val WouldTry   = "would-try"
  val Due        = "due"

/** The re-check as Markdown — every surface (CLI, MCP) prints this. Pure. */
object AcquireChecksMarkdown:
  import AcquireChecks.*

  /** `acquire re-check: 12 due, 3 closed and 1 found this week, 5 of 40 tried today — next pass …`. */
  def line(s: RecheckStatus): String =
    if !s.enabled then "acquire re-check: off (acquire.recheck.enabled = false)"
    else
      s"acquire re-check: ${s.due} of ${Plural.count(s.withDoi, "item")} with a DOI due, " +
        s"${s.closedWeek} closed and ${s.fetchedWeek} found this week, ${s.triedToday} of ${s.perDay} tried today" +
        s.nextPass.fold("")(n => s" — next pass $n") + s.note.fold("")(n => s" ($n)")

  def result(r: RecheckResult): String =
    val verb = if r.dryRun then "would " else ""
    val head = s"# Acquisition list re-check${if r.dryRun then " (dry run: no fetcher asked, nothing stored)" else ""}\n\n" +
      s"- ${Plural.count(r.open, "open item")}, ${r.withDoi} with a DOI; ${r.due} due for a try, ${r.allowance} this pass may try\n" +
      (if r.dryRun then
        s"- ${verb}close ${Plural.count(r.rows.count(_.action == WouldClose), "item")} the corpus holds with its text\n" +
          s"- ${verb}try ${Plural.count(r.rows.count(_.action == WouldTry), "item")} through the legal chain"
      else
        s"- closed ${Plural.count(r.closed, "item")} the corpus holds with its text\n" +
          s"- a legal copy found for ${r.fetched} (converting: the conversion closes the item), " +
          s"no legal copy for ${r.noCopy}, no answer for ${r.failed}") +
      r.note.fold("")(n => s"\n- _${n}_")
    val rows = r.rows.map { row =>
      s"- #${row.item}${row.doi.fold("")(d => s" https://doi.org/$d")} · ${row.action}" +
        row.citekey.fold("")(ck => s" [[$ck]]") + (if row.detail.nonEmpty then s": ${row.detail}" else "")
    }
    if rows.isEmpty then head + "\n\n_Nothing to do._" else head + "\n\n## Items\n\n" + rows.mkString("\n")
