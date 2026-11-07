package com.histmersion.web

import com.histmersion.api.{Paging, Plural, Wording}
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import com.histmersion.api.automation.{ListFilters, Queue}
import com.histmersion.api.catalogue.SkippedPeriod
import com.histmersion.api.jobs.{JobDetail, JobInfo, JobState}
import com.histmersion.api.library.Ingest

/** The background (OCR) jobs window: `GET /api/jobs`, filtered by state through [[UiState.jobStatus]] (on the server),
  * a page at a time ([[ListPaging]]: the heading says how many of how many, "Show older" loads the next page, the
  * filter buttons count the whole table), polled while any job is still queued or running — with backoff, and not at
  * all once every job has finished or the window is closed.
  */
object JobsView:
  type AgoText = AgoText.T
  object AgoText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ButtonLabel = ButtonLabel.T
  object ButtonLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Iso = Iso.T
  object Iso extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobId = JobId.T
  object JobId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindMeaning = KindMeaning.T
  object KindMeaning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Millis = Millis.T
  object Millis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mine = Mine.T
  object Mine extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkippedLine = SkippedLine.T
  object SkippedLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateCount = StateCount.T
  object StateCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateLabel = StateLabel.T
  object StateLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateMeaning = StateMeaning.T
  object StateMeaning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type What = What.T
  object What extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** How many jobs a page lists. */


  val Limit = 100

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** The polling schedule — the one every polled window shares ([[com.histmersion.web.Poll]]). */
  object Poll:
    type Changed = Changed.T
    object Changed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Delay = Delay.T
    object Delay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstDelay = FirstDelay.T
    object FirstDelay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxDelay = MaxDelay.T
    object MaxDelay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstMs = FirstMsValue
    type MaxMs = MaxMsValue

    val FirstMs: com.histmersion.web.JobsView.Poll.FirstMs = com.histmersion.web.Poll.FirstMs
    val MaxMs: com.histmersion.web.JobsView.Poll.MaxMs = com.histmersion.web.Poll.MaxMs

    /** The delay before the next poll, or `None` to stop: no job left to wait for. */
    def next(previous: Option[com.histmersion.web.JobsView.Poll.Delay], jobs: List[JobInfo], changed: com.histmersion.web.JobsView.Poll.Changed): Option[com.histmersion.web.JobsView.Poll.Delay] =
      com.histmersion.web.Poll.next(previous, jobs.exists(!_.isTerminal), changed)

  /** Is any job still queued or running — anywhere in the table (the server's counts), not just among the rows a filter
    * shows; the rows' own states when the server sent no counts.
    */
  def live(rows: List[JobInfo], page: Paging.Page): Boolean =
    if page.counts.isEmpty then rows.exists(!_.isTerminal)
    else page.count(JobState.Queued.wire) + page.count(JobState.Running.wire) > 0

  /** The jobs a state filter shows. */
  def visible(jobs: List[JobInfo], filter: Option[JobState]): List[JobInfo] =
    filter.fold(jobs)(s => jobs.filter(_.state == s))

  /** The jobs a whole filter shows, from the rows already loaded — the states and the kinds; the dates and the text are
    * left to the server's page.
    */
  def visible(jobs: List[JobInfo], f: ListFilters.JobFilter): List[JobInfo] =
    val n = f.normalized
    jobs.filter(j => (n.status.isEmpty || n.status.contains(j.status)) && (n.kind.isEmpty || n.kind.contains(j.kind)))

  /** The query the window sends, from its filter fields. Pure. */
  def filterOf(
      status: Option[JobState],
      kinds: List[com.histmersion.api.Jobs.Filter],
      since: Option[com.histmersion.api.automation.ListFilters.JobFilter.Since],
      until: Option[com.histmersion.api.automation.ListFilters.JobFilter.Until],
      subject: Option[com.histmersion.api.automation.ListFilters.JobFilter.Subject]
  ): ListFilters.JobFilter =
    ListFilters.JobFilter(status.toList.map(_.wire), kinds, since, until, subject)

  /** Did a job that ticks off an acquisition item finish since `before`? Then that list changed. */
  def ticksOff(before: List[JobInfo], after: List[JobInfo]): Boolean =
    val doneBefore = before.filter(_.succeeded).map(_.id).toSet
    after.exists(j => j.succeeded && j.acquireItem.isDefined && !doneBefore(j.id))

  /** How many jobs are in each state, for the filter buttons. */
  def counts(jobs: List[JobInfo]): Map[JobState, com.histmersion.web.JobsView.StateCount] =
    jobs.groupMapReduce(_.state)(_ => 1)(_ + _)

  /** What kind of work a job is, for a person ([[Wording.jobKind]]); a kind it does not know, as words. */
  def kindLabel(kind: com.histmersion.web.JobsView.Kind): com.histmersion.api.ItemKind.Label = Wording.jobKindOf(kind)

  /** What each kind of job does; a tooltip on its filter chip, after its identifier (for the command line). */
  def kindMeaning(kind: com.histmersion.web.JobsView.Kind): Option[com.histmersion.web.JobsView.KindMeaning] =
    com.histmersion.api.RegisteredJobKinds.all.parse(kind).map(_.meaning.value)

  /** What each job state means for a person, and what comes next; a tooltip on its filter button. */
  def stateMeaning(s: JobState): com.histmersion.web.JobsView.StateMeaning = s match
    case JobState.Queued  => com.histmersion.web.JobsView.StateMeaning("Waiting for a free worker; it starts by itself.")
    case JobState.Running => com.histmersion.web.JobsView.StateMeaning("A worker is doing it now; its progress line says how far it got.")
    case JobState.Done    => "Finished without an error; an ingest's source is stored now."
    case JobState.Failed  =>
      "Ended with the error shown on its row, was cancelled before it started, or was interrupted by a server restart."
    case JobState.Paused =>
      "A person paused it after one of its parts; resumed, it goes on with the parts it had not done."
    case JobState.Cancelled => com.histmersion.web.JobsView.StateMeaning("A person cancelled it; it stopped after the part it was on.")

  /** What a job without a citekey is about, from its stored [[JobDetail]] (#770): an init job names what it imports.
    * `None` for a job that has a citekey, or no detail.
    */
  def what(j: JobInfo): Option[com.histmersion.web.JobsView.What] =
    if j.citekey.isDefined then None else JobDetail.subject(j)

  /** The period definitions an import left out (#771): how many, and which. */
  def skippedLine(j: JobInfo): Option[com.histmersion.web.JobsView.SkippedLine] =
    j.detail.filter(SkippedPeriod.SkippedCount.of(_) > 0).map { d =>
      val ids = SkippedPeriod.Skipped.of(d).map(_.id)
      val more = SkippedPeriod.SkippedCount.of(d) - ids.size
      s"skipped ${Plural.count(SkippedPeriod.SkippedCount.of(d), com.histmersion.api.Plural.CountSingular("period definition"))}: ${ids.mkString(", ")}" +
        (if more > 0 then s" and $more more" else "")
    }

  def stateLabel(s: JobState): com.histmersion.web.JobsView.StateLabel = Wording.jobState(s)

  /** Is the job's subject a stored source yet? An ingest creates the source only when its OCR finished; a re-OCR runs
    * on a source that already exists.
    */
  def subjectStored(j: JobInfo): Boolean = j.succeeded || j.kind == com.histmersion.api.library.IngestJobKinds.Reocr.kind

  /** `3 s ago`, `4 min ago`, `2 h ago`, `5 d ago` — `thenMs` and `nowMs` in epoch milliseconds. */
  def ago(thenMs: com.histmersion.web.JobsView.Millis, nowMs: com.histmersion.web.JobsView.Millis): com.histmersion.web.JobsView.AgoText =
    val s = math.max(0L, ((nowMs - thenMs) / 1000).toLong)
    if s < 60 then s"$s s ago"
    else if s < 3600 then s"${s / 60} min ago"
    else if s < 86400 then s"${s / 3600} h ago"
    else s"${s / 86400} d ago"

  private def agoOf(iso: com.histmersion.web.JobsView.Iso): com.histmersion.web.JobsView.AgoText =
    val t = js.Date.parse(iso)
    if iso.isEmpty || t.isNaN then com.histmersion.web.JobsView.AgoText("") else ago(t, js.Date.now())

  // ── state ───────────────────────────────────────────────────────────────────────────────────────

  /** The rows, the poll and "Show older" ([[PolledList]]); an OCR job that ticks its acquisition item off, seen by a
    * poll, reloads an open "to acquire" window.
    */
  /** The window's filter fields (they live in the URL hash). */
  private val filterFields: List[Field[?]] =
    List(UiState.jobStatus, UiState.jobKinds, UiState.jobSince, UiState.jobUntil, UiState.jobSubject)

  private def currentFilter(): ListFilters.JobFilter =
    filterOf(
      UiState.jobStatus.now(),
      UiState.jobKinds.now(),
      UiState.jobSince.now(),
      UiState.jobUntil.now(),
      UiState.jobSubject.now()
    )

  /** The filter, as a signal, so a change restarts the list from the newest page. */
  private val filterSignal: Signal[ListFilters.JobFilter] = UiState.signal.map(_ => currentFilter()).distinct

  private val list = PolledList[JobInfo](
    fetch = (upTo, before) => ApiClient.jobs(currentFilter(), Some(Limit), upTo = upTo, before = before),
    id = _.id.value,
    live = live,
    after = (cur, rows) => if ticksOff(cur.rows, rows) then QueueView.changed.emit(Queue.Kind.Acquire)
  )
  private def listing = list.listing

  /** The job this page just started — marked in the list until the window closes. */
  private val started = Var(Option.empty[String])

  /** A job this page started (an upload): show it, and poll quickly again. */
  def track(jobId: com.histmersion.web.JobsView.JobId): Unit =
    started.set(Some(jobId))
    list.refresh(changed = com.histmersion.web.Poll.DataChanged(true))

  // ── view ────────────────────────────────────────────────────────────────────────────────────────

  def pane: HtmlElement =
    div(
      cls := "tool-pane jobs-pane",
      list.lifecycle,
      onUnmountCallback(_ => started.set(None)),
      filterSignal.changes --> (_ => list.restart()),
      div(
        cls := "pane-bar",
        filterButtons,
        button(typ := "button", cls := "btn", "refresh", onClick --> (_ => list.restart()))
      ),
      div(
        cls := "toolbar list-filters",
        ListFilterBar.chips(
          com.histmersion.web.ListFilterBar.Label("kind"),
          listing.signal.map(_.page.facet(com.histmersion.api.library.Facet.Name("kind"))),
          UiState.jobKinds,
          kindLabel,
          kindMeaning,
          about = "Show only jobs of the kinds picked (any of them); none picked shows every kind."
        ),
        div(
          cls := "btn-group",
          ListFilterBar.text(com.histmersion.web.ListFilterBar.Label("subject: a citekey, a target, an id"), UiState.jobSubject),
          ListFilterBar.day(com.histmersion.web.ListFilterBar.Label("started on or after"), UiState.jobSince),
          ListFilterBar.day(com.histmersion.web.ListFilterBar.Label("started on or before"), UiState.jobUntil),
          ListFilterBar.clearButton(filterFields)
        )
      ),
      list.errorLine(com.histmersion.api.UiLabels.Text("Could not list jobs")),
      child.maybe <-- shownSignal.combineWith(list.loaded.signal).map { (l, filter, isLoaded) =>
        Option.when(isLoaded && l.rows.nonEmpty)(
          p(
            cls := "muted list-heading",
            ListPaging.heading(
              l,
              com.histmersion.api.Plural.Noun.Singular("job"),
              filter.map(_.wire),
              _.status,
              filter.map(stateLabel).toList ++ currentFilter().normalized.kind.map(kindLabel)
            )
          )
        )
      },
      child.maybe <-- shownSignal.combineWith(list.loaded.signal).map { (l, filter, isLoaded) =>
        if !isLoaded then Some(p(cls := "muted", "Loading…"))
        else
          Option.when(l.rows.isEmpty)(
            p(
              cls := "muted",
              if ListFilterBar.anySet(filterFields.drop(1)) then "No jobs match the filter."
              else filter.fold("No background jobs yet.")(s => s"No ${stateLabel(s).toLowerCase} jobs.")
            )
          )
      },
      // keyed by the row itself: a row that did not change keeps its element, so new rows arriving on
      // top do not rebuild (or move the reader off) the ones below
      ul(
        cls := "job-list",
        aria.busy <-- list.loading,
        children <-- shownSignal
          .combineWith(started.signal)
          .map((l, _, mine) => l.rows.map(j => (j, mine.contains(j.id))))
          .split(identity)((key, _, _) => row(key._1, key._2))
      ),
      list.olderButton
    )

  /** The listing with the rows the filter shows — at once, from the rows loaded, when the filter changes; the server's
    * page replaces them a moment later.
    */
  private def shownSignal: Signal[(ListPaging.Listing[JobInfo], Option[JobState])] =
    listing.signal.combineWith(filterSignal).map((l, f) => (l.copy(rows = visible(l.rows, f)), UiState.jobStatus.now()))

  private def filterButtons: HtmlElement =
    def btn(label: com.histmersion.web.JobsView.ButtonLabel, value: Option[JobState], count: Signal[com.histmersion.web.JobsView.StateCount]) =
      button(
        typ := "button",
        cls := "btn btn--toggle",
        title := value.fold("Every job, whatever its state.")(stateMeaning),
        aria.pressed <-- UiState.jobStatus.signal.map(v => (v == value).toString),
        span(label),
        child.text <-- count.map(n => if n > 0 then s" ${Paging.grouped(n)}" else ""),
        onClick --> (_ => UiState.set(UiState.jobStatus := value)())
      )
    div(
      cls := "btn-group jobs-filter",
      role := "group",
      aria.label := "state",
      btn(com.histmersion.web.JobsView.ButtonLabel("All"), None, listing.signal.map(l => ListPaging.count(l, None, _.status))),
      JobState.values.toList.map(s =>
        btn(stateLabel(s), Some(s), listing.signal.map(l => ListPaging.count(l, Some(s.wire), _.status)))
      )
    )

  private[web] def row(j: JobInfo, mine: com.histmersion.web.JobsView.Mine): HtmlElement =
    li(
      cls := s"job-row job-${j.state.wire}",
      cls("job-mine") := mine,
      span(cls := s"job-state state-${j.state.wire}", title := stateLabel(j.state), stateLabel(j.state)),
      div(
        cls := "job-main",
        div(
          cls := "job-subject",
          j.citekey match
            case Some(ck) if subjectStored(j) => SourceLink(ck.value)
            case Some(ck)                     => code(cls := "job-ck", title := "not stored yet", ck.value)
            case None                         => what(j).fold(emptyNode)(w => span(cls := "job-what", w)),
          span(cls := "muted", s" · ${kindLabel(j.kind.value)}"),
          j.acquireItem.map(i =>
            span(
              cls := "muted job-item",
              " · for ",
              QueueItemLink(
                Queue.Kind.Acquire,
                i,
                title := (if j.succeeded then s"acquisition item $i was ticked off by this job"
                          else s"acquisition item $i is ticked off when this job succeeds"),
                s"acquisition item $i"
              )
            )
          )
        ),
        j.progress.filter(_.nonEmpty).map(pr => div(cls := "job-progress", pr)),
        j.error.filter(_.nonEmpty).map(e => div(cls := "job-error", e)),
        skippedLine(j).map(l =>
          div(
            cls := "job-skipped muted",
            title := j.detail.toList
              .flatMap(SkippedPeriod.Skipped.of)
              .map(sp => s"${sp.id} ${sp.label}: ${sp.detail}")
              .mkString("\n"),
            l
          )
        ),
        div(
          cls := "job-meta muted",
          // an agent run is a job: its row leads to the run; any other job, to its own page
          if j.kind == com.histmersion.api.automation.AgentJobKinds.Agent.kind then
            WinLink(WindowKey.Run(j.id.value), title := s"agent run ${j.id}", s"run ${j.id.value.take(8)}")
          else RouteLink(Route.Job(j.id.value), cls := "link-btn", title := s"job ${j.id}", s"job ${j.id.value.take(8)}"),
          Option(j.createdAt).filter(_.nonEmpty).map(c => span(title := c, s" · started ${agoOf(c)}")),
          Option(j.updatedAt)
            .filter(u => u.nonEmpty && u != j.createdAt)
            .map(u => span(title := u, s" · updated ${agoOf(u)}"))
        )
      )
    )
