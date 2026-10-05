package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.core.Agents.{RunDetail, RunInfo, RunState, TargetKind}
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** The embedded agent runs (docs/research-embedded-agents.md §5.3): the runs window
  * (`GET /api/agents/runs`, filtered through [[UiState.runStatus]]) and one window per run
  * (`GET /api/agents/runs/{id}`: steps, tokens, cost, warnings, the trace, and pause / resume /
  * cancel behind the write gate). Both POLL while something is still queued or running — quickly at
  * first, then backing off, and not at all once everything has settled or the window is closed —
  * the same schedule as the jobs window. */
object RunsView:

  /** How many of the newest runs are listed. */
  val Limit = 100

  /** Whether the server runs agents (`/api/config`); the toolbar offers these windows only then. */
  val enabled: Var[Boolean] = Var(false)

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** A run still moving: worth polling for. (A `waiting` run waits for a person, not for the server.) */
  def live(status: String): Boolean =
    RunState.parse(status).exists(s => s == RunState.Queued || s == RunState.Running)

  /** The polling schedule: `None` = stop (nothing is live); a change goes back to the quick delay. */
  def nextPoll(previous: Option[Int], anyLive: Boolean, changed: Boolean): Option[Int] =
    import JobsView.Poll.*
    if !anyLive then None
    else if changed then Some(FirstMs)
    else Some(previous.fold(FirstMs)(p => math.min(MaxMs, math.round(p * Factor).toInt)))

  /** The runs a status filter shows. */
  def visible(runs: List[RunInfo], filter: Option[RunState]): List[RunInfo] =
    filter.fold(runs)(s => runs.filter(_.status == s.wire))

  def counts(runs: List[RunInfo]): Map[String, Int] = runs.groupMapReduce(_.status)(_ => 1)(_ + _)

  /** What a person can do to a run in this state. */
  enum Action(val wire: String, val label: String):
    case Pause  extends Action("pause", "pause")
    case Resume extends Action("resume", "resume")
    case Cancel extends Action("cancel", "cancel")

  def actions(status: String): List[Action] = RunState.parse(status) match
    case Some(RunState.Queued) | Some(RunState.Running) => List(Action.Pause, Action.Cancel)
    case Some(RunState.Paused)                          => List(Action.Resume, Action.Cancel)
    // a run waiting for an approval goes on when the approval is decided; it can only be cancelled
    case Some(RunState.WaitingApproval)                 => List(Action.Cancel)
    case _                                              => Nil

  private val WaitingOn = """approval (\d+)""".r.unanchored

  /** The approval a waiting run waits for, from its progress line (`approve: waiting for approval 12`). */
  def waitingFor(r: RunInfo): Option[Long] =
    if !r.isWaiting then None
    else r.progress.collect { case WaitingOn(id) => id.toLong }

  /** Why a paused run stopped, from its `error` (`budget: …`, `provider: …`, `interrupted`). */
  def pausedFor(r: RunInfo): Option[String] =
    Option.when(r.status == RunState.Paused.wire)(r.error.map(_.takeWhile(_ != ':').trim).getOrElse("requested"))

  /** What a paused run's window says about its pause. An allowance pause ends by itself. */
  def pausedText(r: RunInfo, why: String): String =
    if why == Agents.PauseReason.Budget.wire then s"Paused: $why. Raise the budget to resume."
    else if why == Agents.PauseReason.Allowance.wire then
      s"Paused: every model of a role is below its allowance; ${r.resumeAt.fold("it resumes once an allowance is raised")(t =>
        s"it resumes by itself at $t")}."
    else s"Paused: $why."

  /** A budget the person typed: a positive number of dollars, or nothing. */
  def budget(s: String): Option[Double] = s.trim.toDoubleOption.filter(_ > 0)

  def tokens(u: Agents.Usage): String = s"${u.input} in / ${u.output} out"

  /** The columns of a run's steps table, in order. */
  val StepColumns: List[String] = List("#", "step", "kind", "try", "status", "model", "tokens", "USD", "note")

  /** One step's cells, each with its column's name (the label a narrow card shows). Pure. */
  def stepCells(s: Agents.StepInfo): List[(String, String)] =
    StepColumns.zip(List(s.seq.toString, s.name, s.kind, s.attempt.toString, s.status, s.model.getOrElse(""),
      s"${s.usage.input}/${s.usage.output}", AgentsMarkdown.usd(s.costUsd), s.error.getOrElse("")))

  // ── the runs window ─────────────────────────────────────────────────────────────────────────────

  private val runs    = Var(List.empty[RunInfo])
  private val loaded  = Var(false)
  private val failure = Var(Option.empty[String])
  // `var`s: the one pending poll, its delay, whether the window is there, and a generation counter so
  // an answer to a superseded request is dropped
  private var timer: Int         = 0
  private var delay: Option[Int] = None
  private var mounted: Boolean   = false
  private var generation: Int    = 0

  private def refresh(changed: Boolean = false): Unit =
    dom.window.clearTimeout(timer)
    generation += 1
    val mine = generation
    ApiClient.agentRuns(None, Some(Limit)).onComplete { r =>
      if mounted && mine == generation then
        r.fold(
          e =>
            failure.set(Some(e.getMessage))
            schedule(Some(JobsView.Poll.MaxMs)),
          rs =>
            val moved = changed || rs != runs.now()
            failure.set(None)
            runs.set(rs)
            loaded.set(true)
            schedule(nextPoll(delay, rs.exists(x => live(x.status)), moved))
        )
    }

  private def schedule(next: Option[Int]): Unit =
    delay = next
    next.foreach(ms => timer = dom.window.setTimeout(() => refresh(), ms))

  def pane: HtmlElement =
    div(
      cls := "tool-pane runs-pane",
      onMountCallback { _ =>
        mounted = true
        loaded.set(false)
        refresh(changed = true)
      },
      onUnmountCallback { _ =>
        mounted = false
        dom.window.clearTimeout(timer)
        delay = None
      },
      child.maybe <-- enabled.signal.map(on => Option.when(!on)(p(cls := "muted",
        "Embedded agents are off on this server (agents.enabled). Earlier runs are still listed."))),
      div(cls := "pane-bar", filterButtons,
        button(typ := "button", cls := "btn", "refresh", onClick --> (_ => refresh(changed = true))),
        button(typ := "button", cls := "btn", title := "each role's model chain, the allowances, the provider check",
          "models & allowances", onClick --> (_ => WindowManager.open(WindowKey.Models)))),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", s"Could not list runs: $m"))),
      child <-- runs.signal.combineWith(UiState.runStatus.signal, loaded.signal).map { (all, filter, isLoaded) =>
        val shown = visible(all, filter)
        if !isLoaded then p(cls := "muted", "Loading…")
        else if shown.isEmpty then p(cls := "muted", filter.fold("No agent runs yet.")(s => s"No ${s.wire} runs."))
        else ul(cls := "job-list run-list", shown.map(row))
      }
    )

  private def filterButtons: HtmlElement =
    def btn(label: String, value: Option[RunState], count: Signal[Int]) =
      button(
        typ := "button",
        cls := "layer-btn",
        cls("active") <-- UiState.runStatus.signal.map(_ == value),
        aria.pressed <-- UiState.runStatus.signal.map(v => (v == value).toString),
        span(cls := "layer-label", label),
        child.text <-- count.map(n => if n > 0 then s" $n" else ""),
        onClick --> (_ => UiState.set(UiState.runStatus := value)())
      )
    div(
      cls := "layer-toggles jobs-filter",
      btn("all", None, runs.signal.map(_.size)),
      RunState.values.toList.map(s => btn(s.wire, Some(s), runs.signal.map(counts(_).getOrElse(s.wire, 0))))
    )

  private def stateBadge(status: String): HtmlElement =
    span(cls := s"job-state state-$status", title := status, status)

  private def target(r: RunInfo): HtmlElement =
    TargetKind.parse(r.targetKind) match
      case Some(TargetKind.Article) => com.histmersion.web.ArticleLink(r.target, r.target)
      case Some(TargetKind.Goal) =>
        button(typ := "button", cls := "link-btn", s"goal ${r.target}",
          onClick --> (_ => WindowManager.open(WindowKey.Goal(r.target))))
      case _                        => SourceLink(r.target)

  private def row(r: RunInfo): HtmlElement =
    li(
      cls := s"job-row run-row job-${r.status}",
      stateBadge(r.status),
      div(
        cls := "job-main",
        div(cls := "job-subject",
          button(typ := "button", cls := "link-btn", title := "open this run", r.pipeline,
            onClick --> (_ => WindowManager.open(WindowKey.Run(r.id)))),
          " · ", target(r),
          Option(AgentsMarkdown.options(r)).filter(_.nonEmpty).map(o => span(cls := "muted", s" · $o"))),
        r.progress.filter(_.nonEmpty).map(pr => div(cls := "job-progress", pr)),
        r.error.filter(_.nonEmpty).map(e => div(cls := "job-error", e)),
        Option.when(r.warnings.nonEmpty)(div(cls := "run-warn-count muted",
          s"${Plural.count(r.warnings.size, "warning")}")),
        div(cls := "job-meta muted",
          span(title := s"run ${r.id}", s"run ${r.id.take(8)}"),
          span(s" · ${tokens(r.usage)} · ${AgentsMarkdown.usd(r.costUsd)} of ${AgentsMarkdown.usd(r.budgetUsd)} USD"),
          Option(r.createdAt).filter(_.nonEmpty).map(c => span(title := c, s" · started ${ago(c)}")))
      )
    )

  private def ago(iso: String): String =
    val t = js.Date.parse(iso)
    if iso.isEmpty || t.isNaN then "" else JobsView.ago(t, js.Date.now())

  // ── one run ─────────────────────────────────────────────────────────────────────────────────────

  /** One run's window. Its state is its own (several runs can be open at once). */
  def runPane(id: String): HtmlElement =
    val detail  = Var(Option.empty[RunDetail])
    val failure = Var(Option.empty[String])
    val busy    = Var(false)
    val raise   = Var("")
    // `var`s local to this window: its pending poll, the delay, whether it is mounted, the generation
    var timer: Int         = 0
    var delay: Option[Int] = None
    var mounted: Boolean   = false
    var generation: Int    = 0

    def refresh(changed: Boolean): Unit =
      dom.window.clearTimeout(timer)
      generation += 1
      val mine = generation
      ApiClient.agentRun(id).onComplete { r =>
        if mounted && mine == generation then
          r.fold(
            e =>
              failure.set(Some(e.getMessage))
              delay = Some(JobsView.Poll.MaxMs)
              timer = dom.window.setTimeout(() => refresh(false), JobsView.Poll.MaxMs),
            d =>
              val before = detail.now()
              failure.set(None)
              detail.set(Some(d))
              // a run that just settled (or started waiting) may have opened approvals
              if before.exists(b => live(b.run.status)) && !live(d.run.status) then ApprovalsView.changed.emit(())
              delay = nextPoll(delay, live(d.run.status), changed || !before.contains(d))
              delay.foreach(ms => timer = dom.window.setTimeout(() => refresh(false), ms))
          )
      }

    def act(a: Action): Unit =
      busy.set(true)
      val usd = if a == Action.Resume then budget(raise.now()) else None
      ApiClient.agentControl(id, a.wire, usd).onComplete { r =>
        busy.set(false)
        r.failed.foreach(e => failure.set(Some(e.getMessage)))
        if r.isSuccess then raise.set("")
        refresh(changed = true)
      }

    div(
      cls := "tool-pane run-pane",
      onMountCallback { _ => mounted = true; refresh(changed = true) },
      onUnmountCallback { _ => mounted = false; dom.window.clearTimeout(timer) },
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child <-- detail.signal.map {
        case None    => p(cls := "muted", "Loading…")
        case Some(d) => runBody(d, busy.signal, raise, act)
      }
    )

  private def runBody(d: RunDetail, busy: Signal[Boolean], raise: Var[String], act: Action => Unit): HtmlElement =
    val r = d.run
    div(
      cls := "run-detail",
      div(cls := "run-head",
        stateBadge(r.status),
        span(cls := "run-title", s" ${r.pipeline} · "), target(r),
        Option(AgentsMarkdown.options(r)).filter(_.nonEmpty).map(o => span(cls := "muted", s" ($o)"))),
      pausedFor(r).map(why => p(cls := "run-paused", pausedText(r, why),
        Option.when(why == Agents.PauseReason.Allowance.wire)(span(" ", button(typ := "button", cls := "link-btn",
          "models & allowances", onClick --> (_ => WindowManager.open(WindowKey.Models))))))),
      Option.when(r.isWaiting)(p(cls := "run-waiting",
        s"Waiting for a person to decide ${waitingFor(r).fold("its approval")(id => s"approval #$id")}; it goes on by itself once decided. ",
        button(typ := "button", cls := "link-btn", "open the approvals",
          onClick --> (_ => WindowManager.open(WindowKey.Approvals))))),
      r.goal.map(g => p(cls := "muted", "for goal ",
        button(typ := "button", cls := "link-btn", g, onClick --> (_ => WindowManager.open(WindowKey.Goal(g)))))),
      r.error.filter(e => e.nonEmpty && r.status != RunState.Paused.wire).map(e => p(cls := "job-error", e)),
      r.progress.filter(_.nonEmpty).map(pr => p(cls := "job-progress", pr)),
      dl(cls := "run-facts",
        dt("spent"), dd(s"${tokens(r.usage)} · ${AgentsMarkdown.usd(r.costUsd)} of ${AgentsMarkdown.usd(r.budgetUsd)} USD"),
        dt("model"), dd(r.model.getOrElse("the configured roles")),
        dt("run"), dd(code(r.id)),
        dt("started"), dd(r.createdAt), dt("updated"), dd(r.updatedAt)),
      controls(r, busy, raise, act),
      Option.when(r.warnings.nonEmpty)(div(cls := "run-warnings",
        h4("Warnings"), ul(r.warnings.map(w => li(w))))),
      h4("Steps"),
      table(cls := "run-steps",
        thead(tr(StepColumns.map(th(_)))),
        tbody(d.steps.map { s =>
          // each cell names its column: the narrow layout drops the header and shows every step as
          // a card of "column value" lines (an empty cell is left out there)
          tr(cls := s"step-${s.status}",
            stepCells(s).map { (col, v) =>
              td(dataAttr("label") := col, cls("step-note") := col == "note", cls("is-empty") := v.isEmpty, v)
            })
        })),
      Option.when(d.messages.nonEmpty)(div(cls := "run-trace",
        h4("Trace"),
        d.messages.groupBy(_.seq).toList.sortBy(_._1).map { (seq, ms) =>
          val name = d.steps.find(_.seq == seq).map(_.name).getOrElse(s"step $seq")
          htmlTag("details")(
            htmlTag("summary")(s"$name — ${Plural.count(ms.size, "message")}"),
            ms.map { m =>
              div(cls := s"trace-msg trace-${m.role}",
                div(cls := "trace-head muted", s"${m.role}${m.toolName.fold("")(t => s" · $t")}${m.latencyMs.fold("")(l => s" · $l ms")}"),
                pre(cls := "trace-body", m.content.orElse(m.toolInput).orElse(m.toolResult).getOrElse("")))
            })
        }))
    )

  private def controls(r: RunInfo, busy: Signal[Boolean], raise: Var[String], act: Action => Unit): HtmlElement =
    val acts = actions(r.status)
    div(
      cls := "form-row run-controls",
      child <-- Auth.writable.map { w =>
        if acts.isEmpty then span()
        else if !w then Auth.keyNeeded("control this run")
        else
          div(cls := "form-row",
            Option.when(acts.contains(Action.Resume))(input(
              typ := "text", cls := "text-input run-budget", aria.label := "new budget in USD",
              placeholder := s"budget USD (now ${AgentsMarkdown.usd(r.budgetUsd)})",
              controlled(value <-- raise.signal, onInput.mapToValue --> raise.writer))),
            acts.map(a => button(typ := "button", cls := (if a == Action.Cancel then "btn" else "btn primary"),
              disabled <-- busy, a.label, onClick --> (_ => act(a)))))
      }
    )
