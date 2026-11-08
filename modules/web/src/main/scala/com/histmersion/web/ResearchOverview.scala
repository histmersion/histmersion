package com.histmersion
package web

import com.histmersion.api.Wording
import com.histmersion.api.UiLabels
import com.histmersion.api.automation.Agents.{ApprovalInfo, ApprovalKind, ApprovalState, Decision}
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import com.histmersion.api.automation.{AgentMode, Autopilot, ListFilters, Managers, PlanQuota}
import com.histmersion.api.instance.{UiConfig, UiNotices}
import com.histmersion.api.views.AutopilotStatus
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Goal
import com.histmersion.api.research.Article
import com.histmersion.api.automation.AgentScheduler
import com.histmersion.api.Paging

/** Research › Overview: the agents' state as ONE three-way control with what they are doing and the AI plan's weekly
  * allowance; the oldest decisions waiting for the person, each with its reason and what to read first BEFORE the
  * accept button (no bulk accept here); the goals with their progress and what blocks them; and the latest activity.
  * The agent managers stay one disclosure away.
  */
object ResearchOverview:
  type EpochMillis = EpochMillis.T
  object EpochMillis extends RefinedType[Double, Pure]
  type Percent = Percent.T
  object Percent extends RefinedType[Int, Pure]

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** A goal's progress: its managers' work items done, of all of them (done and still open). */

  final case class Progress(done: Progress.Done, total: Progress.Total):
    def pct: Percent = if total.value <= 0 then Percent(0) else Percent((done.value * 100 / total.value).max(0).min(100))

  object Progress:

    type Done = Done.T
    object Done extends RefinedType[Int, Pure]
    type Total = Total.T
    object Total extends RefinedType[Int, Pure]

  def progressOf(goal: Goal.Slug, managers: List[Managers.ManagerInfo]): Progress =
    val mine = managers.filter(_.goal.contains(goal))
    Progress(
      Progress.Done(mine.map(_.counts.done.value).sum),
      Progress.Total(mine.map(m => m.counts.done.value + m.counts.open.value).sum)
    )

  /** What keeps a goal from moving on, in words: the focus's reason it cannot use a slot, a decision it waits for, or a
    * manager that is starved or held.
    */
  def blocker(
      g: Autopilot.GoalProgress,
      focus: Option[Autopilot.FocusInfo],
      managers: List[Managers.ManagerInfo]
  ): Option[UiLabels.Text] =
    focus
      .flatMap(_.waiting.find(_.slug == g.slug))
      .map(w => Option(w.why).filter(_.value.nonEmpty).map(_.value).getOrElse(Wording.humanize(Wording.Id(w.code.value))))
      .orElse(Option.when(g.openApprovals.value > 0)(if g.openApprovals.value == 1 then "waits for your decision"
      else s"waits for ${g.openApprovals} of your decisions"))
      .orElse(
        managers
          .filter(_.goal.contains(g.slug))
          .flatMap(_.statusNote)
          .map(_.value)
          .find(n => n.startsWith("starved") || n.startsWith("held") || n.startsWith("paused"))
      )
      .map(UiLabels.Text(_))

  /** The decisions to show first: the oldest waiting. */
  def oldestFirst(as: List[ApprovalInfo]): List[ApprovalInfo] =
    as.filter(_.state == ApprovalState.Open.wire).sortBy(a => (a.createdAt.value: String, a.id.value: Long))

  /** What to read before deciding, and where it is: the source a status or edge is about (and the work it points at),
    * the article draft, the goal's plan, the manager a hint is for.
    */
  def evidence(a: ApprovalInfo): List[(UiLabels.Text, UiLabels.Text)] =
    val own = ApprovalKind.parse(a.kind) match
      case Some(ApprovalKind.StatusChange) =>
        List(UiLabels.Text("See the evidence") -> UiLabels.Text(SourceLink.href(Preview.Target.Source.Citekey(a.citekey.value))))
      case Some(ApprovalKind.Edge) =>
        List(UiLabels.Text("See the evidence") -> UiLabels.Text(SourceLink.href(Preview.Target.Source.Citekey(a.citekey.value)))) ++ a.target.map(t =>
          UiLabels.Text("Compare the two") -> UiLabels.Text(SourceLink.href(Preview.Target.Source.Citekey(t.value)))
        )
      case Some(ApprovalKind.ArticlePublish) | Some(ApprovalKind.ArticleFilter) =>
        a.subject.map(s => UiLabels.Text("Read it first") -> UiLabels.Text(ArticleLink.href(Article.Slug(s.value)))).toList
      case Some(ApprovalKind.GoalPlan) | Some(ApprovalKind.ArticlePlan) =>
        a.subject.map(s => UiLabels.Text("Read the plan first") -> UiLabels.Text(Route.Goal(Goal.Slug(s.value)).path)).toList
      case Some(ApprovalKind.HintEffect) =>
        a.subject.map(id => UiLabels.Text("See the manager") -> UiLabels.Text(Route.Manager(Route.ManagerId(id.value)).path)).toList
      case None => Nil
    if own.nonEmpty then own else List(UiLabels.Text("Read the whole proposal") -> UiLabels.Text(Route.Research(ResearchSection.Decisions).path))

  /** `5 hours`, `2 days`: how long the oldest decision has waited. */
  def waited(createdMs: EpochMillis, nowMs: EpochMillis): UiLabels.Text =
    val h = math.max(0L, ((nowMs.value - createdMs.value) / 3600000).toLong)
    if h < 1 then UiLabels.Text("less than an hour")
    else if h < 48 then (if h == 1 then UiLabels.Text("1 hour") else UiLabels.Text(s"$h hours"))
    else UiLabels.Text(s"${h / 24} days")

  /** The weekly limit left, per plan pool that reports one: `Claude plan: 61% of this week's limit left`. */
  def weekly(q: PlanQuota.QuotaInfo): List[UiLabels.Text] =
    q.plans.flatMap { p =>
      p.windows
        .find(w => w.kind.value.startsWith("seven_day") || w.limit.value.startsWith("seven_day"))
        .map(w =>
          UiLabels.Text(
            s"${Option(p.title).filter(_.value.nonEmpty).map(_.value).getOrElse(Wording.pool(AgentScheduler.PoolUse.Pool(p.pool.value)))}: ${w.leftPct.value.round}% of this week's limit left"
          )
        )
    }

  // ── the cards ───────────────────────────────────────────────────────────────────────────────────

  private def write(f: () => Unit): Unit = if Auth.writableNow then f() else WindowManager.open(WindowKey.ApiKey)

  /** The agents: one control of three choices, what is running and waiting, the weekly allowance. */
  private def agentsCard(status: Var[Option[AutopilotStatus]]): HtmlElement =
    val quota = Var(List.empty[String])
    val busy = Var(false)
    val failed = Var(Option.empty[String])
    val mode = status.signal.map(_.map(s => AgentMode.of(AgentMode.Step.Autopilot.On(s.on.value), AgentMode.Step.Drain.On(s.drain.isDefined))))
    def choose(m: AgentMode): Unit = write { () =>
      busy.set(true)
      ApiClient.setAgentMode(m).onComplete { r =>
        busy.set(false)
        r.fold(e => failed.set(Some(e.getMessage)), s => { failed.set(None); status.set(Some(s)) })
      }
    }
    sectionTag(
      cls := "card research-agents",
      aria.label := "Agents",
      onMountCallback(_ => ApiClient.agentQuota.foreach(q => quota.set(weekly(q).map(_.value)))),
      div(
        cls := "research-agents-text",
        div(cls := "research-agents-head", child.text <-- mode.map(_.fold("Research agents")(_.heading))),
        div(
          cls := "muted",
          child.text <-- Notices.agents.combineWith(quota.signal).map { (g, q) =>
            (g.toList.flatMap(a => List(s"${a.running} running now", s"${a.queued} waiting their turn")) ++ q)
              .mkString(" · ")
          }
        )
      ),
      div(
        cls := "seg",
        role := "radiogroup",
        aria.label := "Agent state",
        AgentMode.values.toList.map { m =>
          button(
            typ := "button",
            role := "radio",
            cls := s"seg-option seg-$m",
            cls("on") <-- mode.map(_.contains(m)),
            aria.checked <-- mode.map(_.contains(m).toString),
            disabled <-- busy.signal,
            m.wording,
            onClick --> (_ => choose(m))
          )
        }
      ),
      child.maybe <-- failed.signal.map(_.map(m => p(cls := "form-error", role := "alert", m)))
    )

  /** One decision: the question, why it was proposed, what to read first — and only then accept or reject. */
  private def decision(a: ApprovalInfo, decided: () => Unit): HtmlElement =
    val busy = Var(false)
    def decide(d: Decision): Unit = write { () =>
      busy.set(true)
      ApiClient.decide(a.id, d, None).onComplete { _ =>
        busy.set(false)
        Notices.ownWrite(UiNotices.Board.Approvals)
        decided()
      }
    }
    div(
      cls := "row decision-row",
      dataAttr("approval") := a.id.toString,
      div(cls := "decision-question", s"${Wording.capitalize(ApprovalsView.describe(a))}?"),
      div(
        cls := "muted decision-reason",
        ApprovalsView
          .evidenceLine(a)
          .map(e => span(s"Reason given: ${e.take(240)}${if e.length > 240 then "…" else ""} ")),
        a.runId.map(id => span("Proposed by ", RouteLink(Route.Run(Route.RunId(id.value)), s"agent work ${id.value.take(8)}"), "."))
      ),
      div(
        cls := "btn-group decision-actions",
        evidence(a).map((label, to) =>
          L.a(
            cls := "btn decision-evidence",
            href := to.value,
            label.value,
            onClick --> { ev =>
              if Router.plain(ev) && !to.value.startsWith("/source/") && !to.value.startsWith("/article/") then
                ev.preventDefault()
                Router.goHref(to.value)
            }
          )
        ),
        button(
          typ := "button",
          cls := "btn btn--primary decision-accept",
          disabled <-- busy.signal,
          "Accept",
          onClick --> (_ => decide(Decision.Approve))
        ),
        button(
          typ := "button",
          cls := "btn btn--ghost decision-reject",
          disabled <-- busy.signal,
          "Reject",
          onClick --> (_ => decide(Decision.Reject))
        )
      )
    )

  private def decisionsCard(): HtmlElement =
    val open = Var(Option.empty[List[ApprovalInfo]])
    def load(): Unit =
      ApiClient
        .approvals(ListFilters.ApprovalFilter(), Some(Paging.Limit(200)))
        .onComplete(r => open.set(Some(r.toOption.fold(Nil)(_._1))))
    sectionTag(
      cls := "card research-decisions",
      aria.label := "Needs your decision",
      onMountCallback(_ => load()),
      div(
        cls := "ch",
        h2("Needs your decision"),
        span(
          cls := "muted",
          child.text <-- open.signal.map(
            _.fold("")(as =>
              oldestFirst(as) match
                case Nil    => ""
                case h :: _ => s"${as.size} · oldest waiting ${waited(EpochMillis(js.Date.parse(h.createdAt.value)), EpochMillis(js.Date.now()))}"
            )
          )
        ),
        RouteLink(Route.Research(ResearchSection.Decisions), cls := "ch-link", "All decisions")
      ),
      child <-- open.signal.map {
        case None     => p(cls := "muted row", "Loading…")
        case Some(as) =>
          oldestFirst(as) match
            case Nil   => p(cls := "muted row", "Nothing waits for you.")
            case shown => div(shown.take(3).map(decision(_, () => load())))
      }
    )

  private def goalsCard(
      status: Signal[Option[AutopilotStatus]],
      managers: Signal[List[Managers.ManagerInfo]]
  ): HtmlElement =
    sectionTag(
      cls := "card research-goals",
      aria.label := "Goals",
      div(
        cls := "ch",
        h2("Goals"),
        RouteLink(
          Route.Research(ResearchSection.Goals),
          cls := "ch-link",
          child.text <-- status.map(_.fold("All goals")(s => s"All ${s.goals.size}"))
        )
      ),
      child <-- status.combineWith(managers).map {
        case (None, _)     => p(cls := "muted row", "Loading…")
        case (Some(s), ms) =>
          val top = s.goals.filter(_.parent.isEmpty).sortBy(g => g.lastActivity.map(_.value).getOrElse("")).reverse.take(6)
          if top.isEmpty then p(cls := "muted row", "No research goals yet.")
          else
            div(top.map { g =>
              val pr = progressOf(Goal.Slug(g.slug.value), ms)
              div(
                cls := "row goal-progress",
                dataAttr("goal") := g.slug.value,
                div(
                  cls := "goal-progress-head",
                  GoalsView.badge(Goal.StatusValue(g.status.value)),
                  RouteLink(Route.Goal(Goal.Slug(g.slug.value)), cls := "goal-progress-title", g.title.value),
                  span(
                    cls := "muted goal-steps",
                    if pr.total.value > 0 then s"${pr.done} of ${pr.total} steps" else "no steps yet"
                  )
                ),
                div(
                  cls := "bar",
                  role := "progressbar",
                  aria.valueNow := pr.pct.value.toDouble,
                  aria.valueMin := 0,
                  aria.valueMax := 100,
                  aria.label := s"${g.title}: ${pr.pct}% of its steps done",
                  styleAttr := s"--done:${pr.pct}%",
                  i()
                ),
                blocker(g, s.focus, ms).map(b => div(cls := "muted goal-blocker", s"Blocked: $b"))
              )
            })
      }
    )

  private def activityCard(ui: UiConfig): HtmlElement =
    sectionTag(
      cls := "card research-activity",
      aria.label := "Activity",
      div(
        cls := "ch",
        h2("Activity"),
        ActivityView.shortChips(ui),
        RouteLink(Route.Activity(ActivityTab.Everything), cls := "ch-link", "Full history")
      ),
      ActivityView.short(Paging.Limit(6))
    )

  /** The page. */
  def pane(ui: UiConfig): HtmlElement =
    val status = Var(Option.empty[AutopilotStatus])
    val managers = Var(List.empty[Managers.ManagerInfo])
    div(
      cls := "research-overview",
      onMountCallback { _ =>
        ApiClient.autopilot.foreach(s => status.set(Some(s)))
        if ui.agentsEnabled.value then ApiClient.managers.foreach(managers.set)
      },
      if ui.agentsEnabled.value then agentsCard(status)
      else
        p(
          cls := "muted",
          "Research agents are off on this server. Their settings are ",
          code("agents.enabled"),
          " in the server's configuration."
        )
      ,
      div(
        cls := "research-columns",
        if ui.agentsEnabled.value then decisionsCard() else emptyNode,
        goalsCard(status.signal, managers.signal)
      ),
      activityCard(ui),
      if ui.agentsEnabled.value then
        // open, as the managers were this page before: folding them away is the reader's choice
        htmlTag("details")(
          cls := "research-managers",
          htmlAttr("open", com.raquo.laminar.codecs.BooleanAsAttrPresenceCodec) := true,
          htmlTag("summary")("Agent managers, in detail"),
          ManagersView.pane
        )
      else emptyNode
    )
