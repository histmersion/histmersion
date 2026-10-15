package com.histmersion.web

import com.histmersion.core.*
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** The background (OCR) jobs window: `GET /api/jobs`, filtered by state through
  * [[UiState.jobStatus]] (on the server), a page at a time ([[ListPaging]]: the heading says how many
  * of how many, "Show older" loads the next page, the filter buttons count the whole table), polled
  * while any job is still queued or running — with backoff, and not at all once every job has
  * finished or the window is closed. */
object JobsView:

  /** How many jobs a page lists. */
  val Limit = 100

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** The polling schedule: a quick first look, then slower while nothing changes. */
  object Poll:
    val FirstMs = 1000
    val MaxMs   = 15000
    val Factor  = 1.6

    /** The delay before the next poll, or `None` to stop: nothing left to wait for. A change (a job
      * moved on, or one was started from this page) goes back to the quick delay. */
    def next(previous: Option[Int], jobs: List[JobInfo], changed: Boolean): Option[Int] =
      nextLive(previous, jobs.exists(!_.isTerminal), changed)

    /** [[next]], from whether anything is still live. */
    def nextLive(previous: Option[Int], live: Boolean, changed: Boolean): Option[Int] =
      if !live then None
      else if changed then Some(FirstMs)
      else Some(previous.fold(FirstMs)(p => math.min(MaxMs, math.round(p * Factor).toInt)))

  /** Is any job still queued or running — anywhere in the table (the server's counts), not just
    * among the rows a filter shows; the rows' own states when the server sent no counts. */
  def live(rows: List[JobInfo], page: Paging.Page): Boolean =
    if page.counts.isEmpty then rows.exists(!_.isTerminal)
    else page.count(JobState.Queued.wire) + page.count(JobState.Running.wire) > 0

  /** The jobs a state filter shows. */
  def visible(jobs: List[JobInfo], filter: Option[JobState]): List[JobInfo] =
    filter.fold(jobs)(s => jobs.filter(_.state == s))

  /** Did a job that ticks off an acquisition item finish since `before`? Then that list changed. */
  def ticksOff(before: List[JobInfo], after: List[JobInfo]): Boolean =
    val doneBefore = before.filter(_.succeeded).map(_.id).toSet
    after.exists(j => j.succeeded && j.acquireItem.isDefined && !doneBefore(j.id))

  /** How many jobs are in each state, for the filter buttons. */
  def counts(jobs: List[JobInfo]): Map[JobState, Int] =
    jobs.groupMapReduce(_.state)(_ => 1)(_ + _)

  /** What kind of work a job is, for a person. */
  def kindLabel(kind: String): String = kind match
    case Ingest.KindIngest => "OCR of an upload"
    case Ingest.KindReocr  => "OCR again"
    case InitPlan.JobKind  => "reference data import"
    case MergeApplyJob.JobKind => "merge apply"
    case other             => Label.display(other)

  /** What a job without a citekey is about, from its stored [[JobDetail]] (#770): an init job names
    * what it imports. `None` for a job that has a citekey, or no detail. */
  def what(j: JobInfo): Option[String] =
    if j.citekey.isDefined then None else JobDetail.subject(j)

  /** The period definitions an import left out (#771): how many, and which. */
  def skippedLine(j: JobInfo): Option[String] =
    j.detail.filter(_.skippedPeriodCount > 0).map { d =>
      val ids  = d.skippedPeriods.map(_.id)
      val more = d.skippedPeriodCount - ids.size
      s"skipped ${Plural.count(d.skippedPeriodCount, "period definition")}: ${ids.mkString(", ")}" +
        (if more > 0 then s" and $more more" else "")
    }

  def stateLabel(s: JobState): String = s.wire

  /** Is the job's subject a stored source yet? An ingest creates the source only when its OCR
    * finished; a re-OCR runs on a source that already exists. */
  def subjectStored(j: JobInfo): Boolean = j.succeeded || j.kind == Ingest.KindReocr

  /** `3 s ago`, `4 min ago`, `2 h ago`, `5 d ago` — `thenMs` and `nowMs` in epoch milliseconds. */
  def ago(thenMs: Double, nowMs: Double): String =
    val s = math.max(0L, ((nowMs - thenMs) / 1000).toLong)
    if s < 60 then s"$s s ago"
    else if s < 3600 then s"${s / 60} min ago"
    else if s < 86400 then s"${s / 3600} h ago"
    else s"${s / 86400} d ago"

  private def agoOf(iso: String): String =
    val t = js.Date.parse(iso)
    if iso.isEmpty || t.isNaN then "" else ago(t, js.Date.now())

  // ── state ───────────────────────────────────────────────────────────────────────────────────────

  private val listing   = Var(ListPaging.Listing.empty[JobInfo])
  private val loaded    = Var(false)
  private val older     = Var(false)
  private val failure   = Var(Option.empty[String])
  /** The job this page just started — marked in the list until the window closes. */
  private val started   = Var(Option.empty[String])
  // `var`s: the one pending poll, its delay, and whether the window is there to be refreshed
  private var timer: Int            = 0
  private var delay: Option[Int]    = None
  private var mounted: Boolean      = false
  private var generation: Int       = 0

  /** A job this page started (an upload): show it, and poll quickly again. */
  def track(jobId: String): Unit =
    started.set(Some(jobId))
    if mounted then refresh(changed = true)

  private def stop(): Unit =
    dom.window.clearTimeout(timer)
    delay = None

  /** Start over from the newest page: the window opened, the filter changed, or "refresh". */
  private def restart(): Unit = fetch(reset = true, changed = true)

  /** A poll: the rows shown, current again ([[ListPaging]] says how). */
  private def refresh(changed: Boolean = false): Unit = fetch(reset = false, changed)

  private def fetch(reset: Boolean, changed: Boolean): Unit =
    dom.window.clearTimeout(timer)
    generation += 1
    val mine = generation
    val cur  = listing.now()
    val upTo = if reset then None else ListPaging.pollUpTo(cur, _.id)
    older.set(false)
    ApiClient.jobs(UiState.jobStatus.now(), Some(Limit), upTo = upTo).onComplete { r =>
      if mounted && mine == generation then
        r.fold(
          e =>
            // the last row shown is gone (it cannot page from there): start over
            if upTo.isDefined then restart()
            else
              failure.set(Some(e.getMessage))
              // keep trying while the window is open, at the slowest pace
              schedule(Some(Poll.MaxMs)),
          (rows, page) =>
            val next  = if upTo.isEmpty then ListPaging.first(rows, page) else ListPaging.polled(cur, rows, page)
            val moved = changed || next != cur
            // an OCR job just ticked its acquisition item off: an open "to acquire" window reloads
            if !reset && loaded.now() && ticksOff(cur.rows, rows) then QueueView.changed.emit(Queue.Kind.Acquire)
            failure.set(None)
            listing.set(next)
            loaded.set(true)
            schedule(Poll.nextLive(delay, live(rows, page), moved))
        )
    }

  /** "Show older": the next page, appended. A poll on its way is superseded (and rescheduled here). */
  private def showOlder(): Unit =
    listing.now().page.next.foreach { cursor =>
      dom.window.clearTimeout(timer)
      generation += 1
      val mine = generation
      older.set(true)
      ApiClient.jobs(UiState.jobStatus.now(), Some(Limit), before = Some(cursor)).onComplete { r =>
        if mounted && mine == generation then
          older.set(false)
          r.fold(
            e => { failure.set(Some(e.getMessage)); schedule(Some(Poll.MaxMs)) },
            (rows, page) =>
              failure.set(None)
              listing.update(ListPaging.appended(_, rows, page, _.id))
              schedule(Poll.nextLive(delay, live(listing.now().rows, page), changed = false))
          )
      }
    }

  private def schedule(next: Option[Int]): Unit =
    delay = next
    next.foreach(ms => timer = dom.window.setTimeout(() => refresh(), ms))

  // ── view ────────────────────────────────────────────────────────────────────────────────────────

  def pane: HtmlElement =
    div(
      cls := "tool-pane jobs-pane",
      onMountCallback { _ =>
        mounted = true
        loaded.set(false)
        restart()
      },
      UiState.jobStatus.signal.changes --> (_ => if mounted then restart()),
      onUnmountCallback { _ =>
        mounted = false
        stop()
        started.set(None)
      },
      div(
        cls := "pane-bar",
        filterButtons,
        button(typ := "button", cls := "btn", "refresh", onClick --> (_ => restart()))
      ),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", s"Could not list jobs: $m"))),
      child.maybe <-- shownSignal.combineWith(loaded.signal).map { (l, filter, isLoaded) =>
        Option.when(isLoaded && l.rows.nonEmpty)(
          p(cls := "muted list-heading", ListPaging.heading(l, "job", filter.map(stateLabel))))
      },
      child.maybe <-- shownSignal.combineWith(loaded.signal).map { (l, filter, isLoaded) =>
        if !isLoaded then Some(p(cls := "muted", "Loading…"))
        else Option.when(l.rows.isEmpty)(
          p(cls := "muted", filter.fold("No background jobs yet.")(s => s"No ${stateLabel(s)} jobs.")))
      },
      // keyed by the row itself: a row that did not change keeps its element, so new rows arriving on
      // top do not rebuild (or move the reader off) the ones below
      ul(cls := "job-list",
        children <-- shownSignal.combineWith(started.signal).map((l, _, mine) => l.rows.map(j => (j, mine.contains(j.id))))
          .split(identity)((key, _, _) => row(key._1, key._2))),
      ListPaging.olderButton(listing.signal.map(_.hasOlder), older.signal, () => showOlder())
    )

  /** The listing with the rows the filter shows — at once, from the rows loaded, when the filter
    * changes; the server's page replaces them a moment later. */
  private def shownSignal: Signal[(ListPaging.Listing[JobInfo], Option[JobState])] =
    listing.signal.combineWith(UiState.jobStatus.signal).map((l, f) => (l.copy(rows = visible(l.rows, f)), f))

  private def filterButtons: HtmlElement =
    def btn(label: String, value: Option[JobState], count: Signal[Int]) =
      button(
        typ := "button",
        cls := "btn btn--toggle",
        aria.pressed <-- UiState.jobStatus.signal.map(v => (v == value).toString),
        span(label),
        child.text <-- count.map(n => if n > 0 then s" ${Paging.grouped(n)}" else ""),
        onClick --> (_ => UiState.set(UiState.jobStatus := value)())
      )
    div(
      cls := "btn-group jobs-filter",
      btn("all", None, listing.signal.map(l => ListPaging.count(l, None, _.status))),
      JobState.values.toList.map(s => btn(stateLabel(s), Some(s), listing.signal.map(l => ListPaging.count(l, Some(s.wire), _.status))))
    )

  private def row(j: JobInfo, mine: Boolean): HtmlElement =
    li(
      cls := s"job-row job-${j.state.wire}",
      cls("job-mine") := mine,
      span(cls := s"job-state state-${j.state.wire}", title := stateLabel(j.state), stateLabel(j.state)),
      div(
        cls := "job-main",
        div(
          cls := "job-subject",
          j.citekey match
            case Some(ck) if subjectStored(j) => SourceLink(ck)
            case Some(ck)                     => code(cls := "job-ck", title := "not stored yet", ck)
            case None                         => what(j).fold(emptyNode)(w => span(cls := "job-what", w)),
          span(cls := "muted", s" · ${kindLabel(j.kind)}"),
          j.acquireItem.map(i => span(cls := "muted job-item",
            title := (if j.succeeded then s"acquisition item $i was ticked off by this job"
                      else s"acquisition item $i is ticked off when this job succeeds"),
            s" · for acquisition item $i"))
        ),
        j.progress.filter(_.nonEmpty).map(pr => div(cls := "job-progress", pr)),
        j.error.filter(_.nonEmpty).map(e => div(cls := "job-error", e)),
        skippedLine(j).map(l => div(cls := "job-skipped muted",
          title := j.detail.toList.flatMap(_.skippedPeriods).map(sp => s"${sp.id} ${sp.label}: ${sp.detail}")
            .mkString("\n"), l)),
        div(
          cls := "job-meta muted",
          span(title := s"job ${j.id}", s"job ${j.id.take(8)}"),
          Option(j.createdAt).filter(_.nonEmpty).map(c => span(title := c, s" · started ${agoOf(c)}")),
          Option(j.updatedAt).filter(u => u.nonEmpty && u != j.createdAt)
            .map(u => span(title := u, s" · updated ${agoOf(u)}"))
        )
      )
    )
