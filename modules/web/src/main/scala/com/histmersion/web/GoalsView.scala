package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.core.Agents.{ApprovalInfo, ApprovalKind, ApprovalState, Pipeline, RunInfo, RunRequest}
import com.histmersion.core.GoalPlans.*
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The research goals (docs/research-embedded-agents.md §14): the goals window (the goals as a tree)
  * and one window per goal — its sub-goals and links (sources, articles, search queries, queue
  * items), the plan waiting for approval with approve / reject, the approved plan, the schedule
  * editor (currency check and acquisition sweep: cron, on/off, cost cap) and the goal's runs. A
  * "plan" button asks the server's planner for a plan (only when the server runs agents); every
  * change is behind the write gate. Nothing here polls: a window reloads when it opens, after a
  * change it made, and when a run settles ([[ApprovalsView.changed]]). */
object GoalsView:

  /** How many goals the tree lists, and how many of a goal's runs its window shows. */
  val Limit    = 500
  val RunLimit = 15

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** Goals as a tree, depth first: each root (no parent, or a parent that is not listed) then its
    * sub-goals, by slug, with their depth. A cycle cannot loop: a goal is listed once. */
  def tree(goals: List[Goal]): List[(Goal, Int)] =
    val slugs = goals.map(_.slug).toSet
    val byParent = goals.groupBy(g => g.parentSlug.filter(slugs))
    def walk(parent: Option[String], depth: Int, seen: Set[String]): List[(Goal, Int)] =
      byParent.getOrElse(parent, Nil).sortBy(_.slug).filterNot(g => seen(g.slug)).flatMap { g =>
        (g, depth) :: walk(Some(g.slug), depth + 1, seen + g.slug)
      }
    walk(None, 0, Set.empty)

  /** The open plan approval of a goal, if a plan run is waiting on one. */
  def pendingPlan(as: List[ApprovalInfo], slug: String): Option[ApprovalInfo] =
    as.find(a => a.approvalKind.contains(ApprovalKind.GoalPlan) && a.subject.contains(slug) &&
      a.state == ApprovalState.Open.wire)

  /** What the schedule editor starts from: the schedule's own cron, else a sensible default. */
  def defaultCron(k: ScheduleKind): String = k match
    case ScheduleKind.Currency    => "@weekly"
    case ScheduleKind.Acquisition => "@monthly"

  /** The links of one kind. */
  def linksOf(ls: List[GoalLink], k: LinkKind): List[GoalLink] = ls.filter(_.kind == k)

  /** A link to add, from the editor's kind and text: `None` when the text is blank. */
  def linkToAdd(kind: LinkKind, raw: String, note: String): Option[LinkAdd] =
    Option(raw).map(_.trim).filter(_.nonEmpty).map(r => LinkAdd(kind, r, Option(note).map(_.trim).filter(_.nonEmpty)))

  // ── the goals window ────────────────────────────────────────────────────────────────────────────

  private val goals   = Var(List.empty[Goal])
  private val loaded  = Var(false)
  private val failure = Var(Option.empty[String])

  private def refresh(): Unit =
    ApiClient.goals(Some(Limit)).onComplete { r =>
      r.fold(e => failure.set(Some(e.getMessage)), gs => { failure.set(None); goals.set(gs.goals); loaded.set(true) })
    }

  def pane: HtmlElement =
    div(
      cls := "tool-pane goals-pane",
      onMountCallback { _ => loaded.set(false); refresh() },
      div(cls := "pane-bar", button(typ := "button", cls := "btn", "refresh", onClick --> (_ => refresh()))),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child <-- goals.signal.combineWith(loaded.signal).map { (gs, isLoaded) =>
        if !isLoaded then p(cls := "muted", "Loading…")
        else if gs.isEmpty then p(cls := "muted", "No research goals yet. Create one with histmersion goals or the put_goal tool.")
        else ul(cls := "goal-tree", tree(gs).map((g, depth) =>
          li(cls := "goal-row", styleAttr := s"--depth:$depth",
            span(cls := s"job-state goal-status state-${g.status}", g.status),
            button(typ := "button", cls := "link-btn", g.title, title := g.slug,
              onClick --> (_ => WindowManager.open(WindowKey.Goal(g.slug)))))))
      }
    )

  // ── one goal ────────────────────────────────────────────────────────────────────────────────────

  /** One goal's window; its state is its own (several goals can be open at once). */
  def goalPane(slug: String): HtmlElement =
    val view      = Var(Option.empty[GoalView])
    val runs      = Var(List.empty[RunInfo])
    val approvals = Var(List.empty[ApprovalInfo])
    val failure   = Var(Option.empty[String])
    val message   = Var(Option.empty[String])
    val busy      = Var(false)

    def refresh(): Unit =
      ApiClient.goalView(slug).onComplete(_.fold(e => failure.set(Some(e.getMessage)), v => { failure.set(None); view.set(Some(v)) }))
      if RunsView.enabled.now() then
        ApiClient.agentRuns(None, Some(RunLimit), Some(slug)).foreach(r => runs.set(r._1))
      ApiClient.approvals(None, Some(ApprovalsView.Limit)).foreach(a => approvals.set(a._1))

    def write[A](what: String)(f: => scala.concurrent.Future[A]): Unit =
      busy.set(true)
      f.onComplete { r =>
        busy.set(false)
        r.fold(e => failure.set(Some(s"$what: ${e.getMessage}")), _ => { failure.set(None); message.set(Some(s"$what: done")) })
        refresh()
      }

    def plan(): Unit =
      busy.set(true)
      ApiClient.startRun(RunRequest(Pipeline.PlanGoal.wire, slug)).onComplete { r =>
        busy.set(false)
        r.fold(e => failure.set(Some(s"plan: ${e.getMessage}")), run => {
          failure.set(None)
          message.set(Some("The planner is running; its plan will wait here for your approval."))
          WindowManager.open(WindowKey.Run(run.id))
        })
        refresh()
      }

    div(
      cls := "tool-pane goal-pane",
      onMountCallback { _ => refresh() },
      ApprovalsView.changed.events --> (_ => refresh()),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child.maybe <-- message.signal.map(_.map(m => p(cls := "form-ok", m))),
      child <-- view.signal.map {
        case None    => p(cls := "muted", "Loading…")
        case Some(v) =>
          div(
            cls := "goal-detail",
            div(cls := "run-head", span(cls := s"job-state state-${v.goal.status}", v.goal.status),
              span(cls := "run-title", s" ${v.goal.title}")),
            v.goal.notes.filter(_.nonEmpty).map(n => p(n)),
            dl(cls := "run-facts",
              dt("slug"), dd(code(v.goal.slug)),
              Option.when(v.goal.tags.nonEmpty)(List(dt("tags"), dd(v.goal.tags.mkString(", ")))).toList.flatten,
              Option.when(v.goal.regions.nonEmpty)(List(dt("regions"), dd(v.goal.regions.mkString(", ")))).toList.flatten,
              v.goal.parentSlug.toList.flatMap(ps => List(dt("part of"),
                dd(button(typ := "button", cls := "link-btn", ps, onClick --> (_ => WindowManager.open(WindowKey.Goal(ps))))))),
              v.budgetUsd.toList.flatMap(b => List(dt("run cap"), dd(s"${AgentsMarkdown.usd(b)} USD")))),
            planSection(slug, approvals.signal, busy.signal, () => plan()),
            Option.when(v.children.nonEmpty)(div(h4("Sub-goals"), ul(v.children.map(c =>
              li(button(typ := "button", cls := "link-btn", c.title, onClick --> (_ => WindowManager.open(WindowKey.Goal(c.slug)))),
                span(cls := "muted", s" (${c.status})")))))),
            linksSection(slug, v.links, busy.signal, write),
            schedulesSection(slug, v.schedules, busy.signal, write),
            v.plan.map(pl => htmlTag("details")(cls := "goal-plan", htmlTag("summary")("the approved plan"), ApprovalsView.planBlock(pl))),
            runsSection(runs.signal)
          )
      }
    )

  private def planSection(slug: String, approvals: Signal[List[ApprovalInfo]], busy: Signal[Boolean],
      plan: () => Unit): HtmlElement =
    div(
      cls := "goal-section",
      h4("Plan"),
      child <-- approvals.map(as => pendingPlan(as, slug)).map {
        case Some(a) => div(p(cls := "muted", "A plan waits for your decision. Nothing is created before you approve it."),
          ul(cls := "job-list approval-list", ApprovalsView.row(a)))
        case None =>
          div(
            child <-- RunsView.enabled.signal.combineWith(Auth.writable).map { (agents, w) =>
              if !agents then p(cls := "muted", "Planning needs the server's embedded agents (agents.enabled).")
              else if !w then Auth.keyNeeded("plan this goal")
              else
                div(cls := "form-row",
                  button(typ := "button", cls := "btn btn--primary", "plan", disabled <-- busy, onClick --> (_ => plan())),
                  span(cls := "muted", " Sub-goals, search queries and works to read; you approve them before anything is created."))
            })
      }
    )

  private def linksSection(slug: String, links: List[GoalLink], busy: Signal[Boolean],
      write: String => (=> scala.concurrent.Future[Any]) => Unit): HtmlElement =
    val kind = Var(LinkKind.Source)
    val ref  = Var("")
    val note = Var("")
    def entry(l: GoalLink): HtmlElement =
      li(
        l.kind match
          case LinkKind.Source    => SourceLink(l.ref, label = l.title.map(t => s"${l.ref} — $t"))
          case LinkKind.Article   => com.histmersion.web.ArticleLink(l.ref, l.title.getOrElse(l.ref))
          case LinkKind.Query     => span(s"“${l.ref}”")
          case LinkKind.QueueItem => span(s"#${l.ref} ", span(cls := "muted", l.title.getOrElse("(gone)"))),
        Option.when(l.title.isEmpty && l.kind != LinkKind.Query)(span(cls := "muted", " (missing)")),
        l.note.filter(_.nonEmpty).map(n => span(cls := "muted", s" · $n")),
        child.maybe <-- Auth.writable.map(w => Option.when(w)(
          button(typ := "button", cls := "link-btn goal-unlink", "unlink", disabled <-- busy,
            onClick --> (_ => write("unlink")(ApiClient.removeGoalLink(slug, l.kind, l.ref)))))))
    div(
      cls := "goal-section",
      h4("Linked"),
      if links.isEmpty then p(cls := "muted", "Nothing is linked yet.")
      else div(LinkKind.values.toList.map(k => linksOf(links, k)).filter(_.nonEmpty).map(ls =>
        div(h5(ls.head.kind.wire.replace('-', ' ')), ul(cls := "goal-links", ls.map(entry))))),
      child <-- Auth.writable.map { w =>
        if !w then Auth.keyNeeded("link to this goal")
        else
          div(cls := "form-row goal-link-add",
            select(cls := "field", aria.label := "link kind",
              LinkKind.values.toList.map(k => option(value := k.wire, k.wire.replace('-', ' '))),
              controlled(value <-- kind.signal.map(_.wire),
                onChange.mapToValue --> (v => LinkKind.parse(v).foreach(kind.set)))),
            input(typ := "text", cls := "field", aria.label := "what to link",
              placeholder := "citekey, article slug, query or item id",
              controlled(value <-- ref.signal, onInput.mapToValue --> ref.writer)),
            input(typ := "text", cls := "field", aria.label := "note", placeholder := "note (optional)",
              controlled(value <-- note.signal, onInput.mapToValue --> note.writer)),
            button(typ := "button", cls := "btn", "link", disabled <-- busy, onClick --> { _ =>
              linkToAdd(kind.now(), ref.now(), note.now()).foreach { l =>
                write("link")(ApiClient.addGoalLink(slug, l))
                ref.set(""); note.set("")
              }
            }))
      }
    )

  private def schedulesSection(slug: String, ss: List[GoalSchedule], busy: Signal[Boolean],
      write: String => (=> scala.concurrent.Future[Any]) => Unit): HtmlElement =
    def editor(k: ScheduleKind): HtmlElement =
      val current = ss.find(_.kind == k)
      val cron    = Var(current.map(_.cron).getOrElse(defaultCron(k)))
      val on      = Var(current.forall(_.enabled))
      val budget  = Var(current.flatMap(_.budgetUsd).fold("")(AgentsMarkdown.usd))
      li(cls := "goal-schedule",
        div(strong(k.wire), span(cls := "muted", k match
          case ScheduleKind.Currency    => " — assess and followups over the goal's sources"
          case ScheduleKind.Acquisition => " — search its queries; a model picks what to add to the acquisition list")),
        current.map(c => div(cls := "muted",
          c.nextRunAt.fold("not scheduled")(n => s"next $n"),
          c.lastRunAt.map(l => s" · last run $l"),
          c.lastRun.map(id => span(" · ", button(typ := "button", cls := "link-btn", s"run ${id.take(8)}",
            onClick --> (_ => WindowManager.open(WindowKey.Run(id)))))),
          c.lastNote.filter(_.nonEmpty).map(n => s" · $n"))),
        child <-- Auth.writable.map { w =>
          if !w then current.fold(span(cls := "muted", "no schedule"))(c => span(code(c.cron), if c.enabled then " on" else " off"))
          else
            div(cls := "form-row",
              input(typ := "text", cls := "field goal-cron", aria.label := s"${k.wire} schedule (cron, UTC)",
                placeholder := "cron in UTC, or @daily, @weekly, @monthly",
                controlled(value <-- cron.signal, onInput.mapToValue --> cron.writer)),
              label(cls := "goal-on", input(typ := "checkbox", controlled(checked <-- on.signal, onClick.mapToChecked --> on.writer)), " on"),
              input(typ := "text", cls := "field run-budget", aria.label := "cost cap per run in USD",
                placeholder := "cap USD (optional)", controlled(value <-- budget.signal, onInput.mapToValue --> budget.writer)),
              button(typ := "button", cls := "btn btn--primary", "save", disabled <-- busy, onClick --> (_ =>
                write(s"${k.wire} schedule")(ApiClient.setSchedule(slug, k, ScheduleSet(cron.now(), on.now(), RunsView.budget(budget.now())))))),
              Option.when(current.isDefined)(button(typ := "button", cls := "btn", "remove", disabled <-- busy, onClick --> (_ =>
                write(s"${k.wire} schedule")(ApiClient.removeSchedule(slug, k))))))
        })
    div(
      cls := "goal-section",
      h4("Schedules"),
      p(cls := "muted", "They run only while the server's embedded agents are on; the daily and monthly cost caps are checked before each start."),
      ul(cls := "goal-schedules", ScheduleKind.values.toList.map(editor))
    )

  private def runsSection(runs: Signal[List[RunInfo]]): HtmlElement =
    div(
      cls := "goal-section",
      child <-- runs.map { rs =>
        if rs.isEmpty then emptyNode
        else div(h4("Runs"), ul(cls := "goal-runs", rs.map(r =>
          li(span(cls := s"job-state state-${r.status}", r.status), " ",
            button(typ := "button", cls := "link-btn", s"${r.pipeline} ${r.target}", onClick --> (_ => WindowManager.open(WindowKey.Run(r.id)))),
            span(cls := "muted", s" · ${r.createdAt}")))))
      }
    )
