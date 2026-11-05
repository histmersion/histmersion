package com.histmersion.web

import com.histmersion.api.{Plural, UiLabels, Wording}
import com.histmersion.api.automation.Goal.Plans.{GoalView, LinkKind, ScheduleKind}
import com.histmersion.api.automation.Managers.{ManagerInfo, ManagerItem}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import com.histmersion.api.automation.{Agents, Managers, Queue}
import com.histmersion.api.instance.UiConfig

/** One research goal's page: its question, state and schedule ("checked for new publications 3 days ago · next check
  * …"), its actions (check for new publications now, pause the goal, edit), its plan — each step linked to the agent
  * work that did it —, what it produced (articles, sources brought in, open backlog items, items waiting to be
  * acquired) and its history (the goal's slice of Activity).
  */
object GoalPage:

  /** How many of the goal's steps the plan lists, and how many entries its history. */
  type DaysDayMs = DaysDayMs.T
  object DaysDayMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProducedCountLinkN = ProducedCountLinkN.T
  object ProducedCountLinkN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProducedCountLinkOne = ProducedCountLinkOne.T
  object ProducedCountLinkOne extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProducedCountLinkMany = ProducedCountLinkMany.T
  object ProducedCountLinkMany extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProducedCountLinkTo = ProducedCountLinkTo.T
  object ProducedCountLinkTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type StepNameResult = StepNameResult.T
  object StepNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysResult = DaysResult.T
  object DaysResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysThenMs = DaysThenMs.T
  object DaysThenMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysNowMs = DaysNowMs.T
  object DaysNowMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScheduleResult = ScheduleResult.T
  object ScheduleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScheduleLastCheck = ScheduleLastCheck.T
  object ScheduleLastCheck extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScheduleNowMs = ScheduleNowMs.T
  object ScheduleNowMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PausedResult = PausedResult.T
  object PausedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListOfResult = ListOfResult.T
  object ListOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListOfGoal = ListOfGoal.T
  object ListOfGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LibraryOfResult = LibraryOfResult.T
  object LibraryOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LibraryOfGoal = LibraryOfGoal.T
  object LibraryOfGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapOfResult = MapOfResult.T
  object MapOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapOfGoal = MapOfGoal.T
  object MapOfGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaneSlug = PaneSlug.T
  object PaneSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderNote = HeaderNote.T
  object HeaderNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepN = StepN.T
  object StepN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProducedBacklog = ProducedBacklog.T
  object ProducedBacklog extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProducedAcquire = ProducedAcquire.T
  object ProducedAcquire extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProducedSlug = ProducedSlug.T
  object ProducedSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowFilteredSlug = ShowFilteredSlug.T
  object ShowFilteredSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val StepLimit = 12
  val HistoryLimit = 8

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** A step's mark: done, going now, or still to do (its position). */
  enum Mark:
    case Done, Going, Todo, Stopped

  def markOf(i: ManagerItem): Mark = i.status match
    case "done"                             => Mark.Done
    case "active"                           => Mark.Going
    case "failed" | "stopped" | "cancelled" => Mark.Stopped
    case _                                  => Mark.Todo

  /** A step's name: what kind of work it is, on what. */
  def stepName(i: ManagerItem): com.histmersion.web.GoalPage.StepNameResult =
    val what = UiLabels.label(i.kind)
    if i.target.isEmpty || i.target == i.managerId then what else s"$what — ${Wording.value(i.target)}"

  /** The steps the plan shows: going now first, then still to do, then done, newest first in each. */
  def steps(items: List[ManagerItem]): List[ManagerItem] =
    def rank(i: ManagerItem) = markOf(i) match
      case Mark.Going   => 0
      case Mark.Todo    => 1
      case Mark.Stopped => 2
      case Mark.Done    => 3
    items.sortBy(i => (rank(i), -js.Date.parse(Option(i.updatedAt).filter(_.nonEmpty).getOrElse(i.createdAt))))

  /** `3 days ago`, `today`, `in 2 days`, from two instants in epoch ms: calendar days in the reader's time zone. */
  def days(thenMs: com.histmersion.web.GoalPage.DaysThenMs, nowMs: com.histmersion.web.GoalPage.DaysNowMs): com.histmersion.web.GoalPage.DaysResult =
    def day(ms: com.histmersion.web.GoalPage.DaysDayMs) = ((ms - new js.Date(ms).getTimezoneOffset() * 60000) / 86400000).floor.toLong
    val d = day(nowMs) - day(thenMs)
    if d == 0 then "today"
    else if d > 0 then (if d == 1 then "yesterday" else s"$d days ago")
    else if d == -1 then "tomorrow"
    else s"in ${-d} days"

  /** When it was last checked for new publications and when next: the currency schedule, else the watcher. */
  def schedule(v: GoalView, watcher: Option[ManagerInfo], lastCheck: Option[com.histmersion.web.GoalPage.ScheduleLastCheck], nowMs: com.histmersion.web.GoalPage.ScheduleNowMs): Option[com.histmersion.web.GoalPage.ScheduleResult] =
    val currency = v.schedules.find(_.kind == ScheduleKind.Currency)
    val last = currency.flatMap(_.lastRunAt).orElse(lastCheck)
    val next = currency.flatMap(_.nextRunAt).orElse(watcher.flatMap(_.nextTickAt))
    val parts = last.map(t => s"checked for new publications ${days(js.Date.parse(t), nowMs)}").toList ++
      next.map(t => s"next check ${days(js.Date.parse(t), nowMs)}").toList
    Option.when(parts.nonEmpty)(parts.mkString(" · "))

  /** The goal's managers paused (all of them): what "Pause this goal" undoes. */
  def paused(ms: List[ManagerInfo]): com.histmersion.web.GoalPage.PausedResult = ms.nonEmpty && ms.forall(_.state == Managers.State.Paused.wire)

  /** The address of a list page that shows only this goal's items. */
  def listOf(section: ResearchSection, goal: com.histmersion.web.GoalPage.ListOfGoal): com.histmersion.web.GoalPage.ListOfResult =
    s"${Route.Research(section).path}?goal=${js.URIUtils.encodeURIComponent(goal)}"

  /** Library and the map, filtered to the goal's sources. */
  def libraryOf(goal: com.histmersion.web.GoalPage.LibraryOfGoal): com.histmersion.web.GoalPage.LibraryOfResult =
    s"${Route.Library(LibrarySection.Sources).path}#goal=${js.URIUtils.encodeURIComponent(goal)}"
  def mapOf(goal: com.histmersion.web.GoalPage.MapOfGoal): com.histmersion.web.GoalPage.MapOfResult = s"${Route.Explore.path}#goal=${js.URIUtils.encodeURIComponent(goal)}"

  // ── the page ────────────────────────────────────────────────────────────────────────────────────

  private def write(f: () => Unit): Unit = if Auth.writableNow then f() else WindowManager.open(WindowKey.ApiKey)

  def pane(ui: UiConfig, slug: com.histmersion.web.GoalPage.PaneSlug): HtmlElement =
    val view = Var(Option.empty[Either[String, GoalView]])
    val managers = Var(List.empty[ManagerInfo])
    val items = Var(List.empty[ManagerItem])
    val backlog = Var(Option.empty[Int])
    val acquire = Var(Option.empty[Int])
    val note = Var(Option.empty[String])
    val editing = Var(false)

    def load(): Unit =
      ApiClient.goalView(slug).onComplete(r => view.set(Some(r.toEither.left.map(_.getMessage))))
      ApiClient.queueForGoal(Queue.Kind.Tasks.label, slug).foreach(l => backlog.set(Some(l.items.size)))
      ApiClient.queueForGoal(Queue.Kind.Acquire.label, slug).foreach(l => acquire.set(Some(l.items.size)))
      if ui.agentsEnabled then
        ApiClient.managers.foreach { all =>
          val mine = all.filter(_.goal.contains(slug))
          managers.set(mine)
          scala.concurrent.Future
            .sequence(mine.map(m => ApiClient.managerItems(m.id, None, 0, StepLimit)))
            .foreach(pages => items.set(pages.flatMap(_.items)))
        }

    def checkNow(): Unit = write { () =>
      ApiClient.startRun(Agents.RunRequest(Agents.Pipeline.CurrencyCheck.wire, slug)).onComplete {
        case scala.util.Success(r) =>
          note.set(Some(s"Checking for new publications: agent work ${r.id.take(8)} started.")); load()
        case scala.util.Failure(e) => note.set(Some(s"Could not start the check: ${e.getMessage}"))
      }
    }

    def pauseOrResume(): Unit = write { () =>
      val resume = paused(managers.now())
      scala.concurrent.Future
        .sequence(managers.now().map(m => ApiClient.managerAction(m.id, if resume then "resume" else "pause")))
        .onComplete { r =>
          note.set(
            r.failed.toOption
              .map(e => s"Could not ${if resume then "resume" else "pause"} the goal: ${e.getMessage}")
              .orElse(Some(if resume then "The goal goes on."
              else "The goal is paused: its agents start nothing new for it."))
          )
          load()
        }
    }

    div(
      cls := "goal-page",
      onMountCallback(_ => load()),
      child <-- view.signal.map {
        case None           => p(cls := "muted", "Loading…")
        case Some(Left(m))  => p(cls := "form-error", role := "alert", s"Could not read the goal $slug: $m")
        case Some(Right(v)) =>
          Router.retitle(Route.Goal(slug), v.goal.title)
          div(
            header(
              v,
              managers.signal,
              items.signal,
              ui,
              note.signal,
              () => checkNow(),
              () => pauseOrResume(),
              () => editing.update(!_)
            ),
            child.maybe <-- editing.signal.map(on =>
              Option.when(on)(
                sectionTag(cls := "card goal-edit", aria.label := "Edit the goal", GoalsView.goalPane(slug))
              )
            ),
            div(
              cls := "goal-columns",
              plan(v, items.signal, ui),
              div(
                cls := "goal-side",
                produced(v, backlog.signal, acquire.signal, slug),
                sectionTag(
                  cls := "card goal-history",
                  aria.label := "History",
                  div(
                    cls := "ch",
                    h2("History"),
                    span(cls := "muted", "agent work, jobs and notes for this goal, in one list"),
                    a(
                      cls := "ch-link",
                      href := s"${Route.Activity(ActivityTab.Everything).path}${ActivityView.search(Some(slug))}",
                      "Full history",
                      onClick --> { ev =>
                        if Router.plain(ev) then {
                          ev.preventDefault()
                          Router.go(Route.Activity(ActivityTab.Everything), search = ActivityView.search(Some(slug)))
                        }
                      }
                    )
                  ),
                  ActivityView.history(slug, HistoryLimit)
                )
              )
            )
          )
      }
    )

  private def header(
      v: GoalView,
      managers: Signal[List[ManagerInfo]],
      items: Signal[List[ManagerItem]],
      ui: UiConfig,
      note: Signal[Option[com.histmersion.web.GoalPage.HeaderNote]],
      checkNow: () => Unit,
      pauseOrResume: () => Unit,
      edit: () => Unit
  ): HtmlElement =
    val g = v.goal
    div(
      cls := "goal-head",
      div(
        cls := "goal-head-text",
        div(
          cls := "muted goal-crumbs",
          RouteLink(Route.Research(ResearchSection.Goals), "Goals"),
          s" › ${g.title}",
          g.parentSlug.map(p => span(" · part of ", RouteLink(Route.Goal(p), Wording.humanize(p))))
        ),
        g.notes.filter(_.trim.nonEmpty).map(n => p(cls := "goal-question", n)),
        div(
          cls := "muted goal-state",
          GoalsView.badge(g.status),
          child.text <-- managers.combineWith(items).map { (ms, is) =>
            val pr = ResearchOverview.progressOf(g.slug, ms)
            val watcher = ms.find(_.id == Managers.watchId(g.slug))
            val lastCheck =
              is.filter(i => i.kind == Managers.CurrencyCheck && i.status == "done").map(_.updatedAt).maxOption
            val parts = List(
              if paused(ms) then Some("Paused") else None,
              Option.when(pr.total > 0)(s"${pr.done} of ${pr.total} steps done"),
              schedule(v, watcher, lastCheck, js.Date.now())
            ).flatten
            if parts.isEmpty then "" else parts.mkString(" · ", " · ", "")
          }
        ),
        child.maybe <-- note.map(_.map(n => p(cls := "muted goal-note", role := "status", n)))
      ),
      div(
        cls := "btn-group goal-actions",
        Option.when(ui.agentsEnabled)(
          button(
            typ := "button",
            cls := "btn goal-check",
            "Check for new publications now",
            onClick --> (_ => checkNow())
          )
        ),
        Option.when(ui.agentsEnabled)(
          button(
            typ := "button",
            cls := "btn goal-pause",
            disabled <-- managers.map(_.isEmpty),
            title <-- managers.map(ms => if ms.isEmpty then "This goal has no agents working for it" else ""),
            child.text <-- managers.map(ms => if paused(ms) then "Resume this goal" else "Pause this goal"),
            onClick --> (_ => pauseOrResume())
          )
        ),
        button(typ := "button", cls := "btn btn--ghost goal-edit-btn", "Edit", onClick --> (_ => edit()))
      )
    )

  private def plan(v: GoalView, items: Signal[List[ManagerItem]], ui: UiConfig): HtmlElement =
    sectionTag(
      cls := "card goal-plan",
      aria.label := "Plan",
      div(cls := "ch", h2("Plan"), span(cls := "muted", "each step links to the agent work that did it")),
      child <-- items.map { is =>
        if is.isEmpty then
          div(
            cls := "row muted",
            if !ui.agentsEnabled then "Research agents are off on this server: no step runs."
            else
              v.plan.fold("No steps yet: the goal's agents have not planned its work.")(pl =>
                s"Approved plan: ${Plural.count(pl.subGoals.size, "sub-goal")}, ${Plural.count(pl.queries.size, "query", "queries")}, " +
                  s"${Plural.count(pl.works.size, "work")} to read."
              )
          )
        else ol(cls := "goal-steps-list", steps(is).take(StepLimit).zipWithIndex.map((i, n) => step(i, n + 1)))
      }
    )

  private def step(i: ManagerItem, n: com.histmersion.web.GoalPage.StepN): HtmlElement =
    val mark = markOf(i)
    val busy = Var(false)
    li(
      cls := s"step step-${mark.toString.toLowerCase}",
      dataAttr("item") := i.id.toString,
      span(
        cls := s"step-mark step-mark-${mark.toString.toLowerCase}",
        aria.hidden := true,
        mark match
          case Mark.Done    => "✓"
          case Mark.Going   => "●"
          case Mark.Stopped => "!"
          case Mark.Todo    => n.toString
      ),
      div(
        cls := "step-main",
        strong(stepName(i)),
        div(
          cls := "muted",
          mark match
            case Mark.Done    => span("Done", i.outcome.filter(_.nonEmpty).map(o => s" · ${o.take(160)}"))
            case Mark.Going   => span("Running now")
            case Mark.Stopped => span("Stopped", i.outcome.filter(_.nonEmpty).map(o => s" · ${o.take(160)}"))
            case Mark.Todo    =>
              span(
                i.hold.map(h => s"Waiting: ${Option(h.reason).filter(_.nonEmpty).getOrElse("held")}").getOrElse("To do")
              ),
          i.runId.map(id => span(" · ", RouteLink(Route.Run(id), "see what it did")))
        )
      ),
      if mark == Mark.Going && i.runId.isDefined then
        button(
          typ := "button",
          cls := "btn btn--sm",
          "Pause",
          disabled <-- busy.signal,
          onClick --> (_ =>
            write { () =>
              busy.set(true)
              i.runId.foreach(id => ApiClient.jobControl(id, "pause").onComplete(_ => busy.set(false)))
            }
          )
        )
      else
        span(
          cls := "muted step-state",
          mark match
            case Mark.Done    => "done"
            case Mark.Going   => "running"
            case Mark.Stopped => "stopped"
            case Mark.Todo    => if i.hold.isDefined then "blocked" else "to do"
        )
    )

  private def produced(
      v: GoalView,
      backlog: Signal[Option[com.histmersion.web.GoalPage.ProducedBacklog]],
      acquire: Signal[Option[com.histmersion.web.GoalPage.ProducedAcquire]],
      slug: com.histmersion.web.GoalPage.ProducedSlug
  ): HtmlElement =
    val articles = v.links.filter(_.kind == LinkKind.Article)
    val sources = v.links.count(_.kind == LinkKind.Source)
    def countLink(n: Signal[Option[com.histmersion.web.GoalPage.ProducedCountLinkN]], one: com.histmersion.web.GoalPage.ProducedCountLinkOne, many: com.histmersion.web.GoalPage.ProducedCountLinkMany, to: com.histmersion.web.GoalPage.ProducedCountLinkTo) =
      a(
        href := to,
        child.text <-- n.map(_.fold("…")(c => if c == 1 then s"1 $one" else s"$c $many")),
        onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); Router.goHref(to) } }
      )
    sectionTag(
      cls := "card goal-produced",
      aria.label := "What this goal produced",
      div(cls := "ch", h2("What it produced")),
      div(
        cls := "li",
        span(cls := "li-what", "Articles"),
        if articles.isEmpty then span(cls := "muted", "none yet")
        else
          span(
            articles
              .take(4)
              .flatMap(l =>
                List[HtmlElement](span(" · "), com.histmersion.web.ArticleLink(l.ref, l.title.getOrElse(l.ref)))
              )
              .drop(1),
            Option.when(articles.size > 4)(span(cls := "muted", s" and ${articles.size - 4} more"))
          )
      ),
      div(
        cls := "li",
        span(cls := "li-what", "Sources brought in"),
        span(
          a(
            href := libraryOf(slug),
            Plural.count(sources, "source"),
            onClick --> { ev =>
              if Router.plain(ev) then {
                ev.preventDefault(); showFiltered(slug, Route.Library(LibrarySection.Sources))
              }
            }
          ),
          " · ",
          a(
            href := mapOf(slug),
            "on the map",
            onClick --> { ev =>
              if Router.plain(ev) then { ev.preventDefault(); showFiltered(slug, Route.Explore) }
            }
          )
        )
      ),
      div(
        cls := "li",
        span(cls := "li-what", "Open backlog items"),
        countLink(backlog, "item", "items", listOf(ResearchSection.Backlog, slug))
      ),
      div(
        cls := "li",
        span(cls := "li-what", "Waiting for you to acquire"),
        countLink(acquire, "item", "items", listOf(ResearchSection.Acquire, slug))
      )
    )

  /** Library or the map, showing only this goal's sources. */
  private def showFiltered(slug: com.histmersion.web.GoalPage.ShowFilteredSlug, to: Route): Unit =
    UiState.set(UiState.goal := Some(slug))(push = true)
    Router.go(to)
