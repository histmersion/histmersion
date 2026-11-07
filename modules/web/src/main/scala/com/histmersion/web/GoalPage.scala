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
  type AcquireCount = AcquireCount.T
  object AcquireCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogCount = BacklogCount.T
  object BacklogCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysText = DaysText.T
  object DaysText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastCheck = LastCheck.T
  object LastCheck extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkTarget = LinkTarget.T
  object LinkTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Millis = Millis.T
  object Millis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProducedCount = ProducedCount.T
  object ProducedCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScheduleText = ScheduleText.T
  object ScheduleText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** How many of the goal's steps the plan lists, and how many entries its history. */

  type ScheduleNowMs = _root_.com.histmersion.web.GoalPage.Millis
  val ScheduleNowMs: _root_.com.histmersion.web.GoalPage.Millis.type = _root_.com.histmersion.web.GoalPage.Millis
  type PaneSlug = _root_.com.histmersion.api.automation.Goal.Slug
  val PaneSlug: _root_.com.histmersion.api.automation.Goal.Slug.type = _root_.com.histmersion.api.automation.Goal.Slug
  type HeaderNote = HeaderNote.T
  object HeaderNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProducedSlug = _root_.com.histmersion.api.automation.Goal.Slug
  val ProducedSlug: _root_.com.histmersion.api.automation.Goal.Slug.type = _root_.com.histmersion.api.automation.Goal.Slug

  val StepLimit = 12
  val HistoryLimit = 8

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** A step's mark: done, going now, or still to do (its position). */
  enum Mark:
    case Done, Going, Todo, Stopped

  def markOf(i: ManagerItem): Mark = i.status.value match
    case "done"                             => Mark.Done
    case "active"                           => Mark.Going
    case "failed" | "stopped" | "cancelled" => Mark.Stopped
    case _                                  => Mark.Todo

  /** A step's name: what kind of work it is, on what. */
  def stepName(i: ManagerItem): com.histmersion.api.automation.Agents.StepInfo.Name =
    val what = UiLabels.label(Wording.Id(i.kind.value)).value
    com.histmersion.api.automation.Agents.StepInfo.Name(
      if i.target.value.isEmpty || i.target.value == i.managerId.value then what else s"$what — ${Wording.value(i.target.value)}"
    )

  /** The steps the plan shows: going now first, then still to do, then done, newest first in each. */
  def steps(items: List[ManagerItem]): List[ManagerItem] =
    def rank(i: ManagerItem) = markOf(i) match
      case Mark.Going   => 0
      case Mark.Todo    => 1
      case Mark.Stopped => 2
      case Mark.Done    => 3
    items.sortBy(i => (rank(i), -js.Date.parse(Option(i.updatedAt.value).filter(_.nonEmpty).getOrElse(i.createdAt.value))))

  /** `3 days ago`, `today`, `in 2 days`, from two instants in epoch ms: calendar days in the reader's time zone. */
  def days(thenMs: com.histmersion.web.GoalPage.Millis, nowMs: com.histmersion.web.GoalPage.Millis): com.histmersion.web.GoalPage.DaysText =
    def day(ms: com.histmersion.web.GoalPage.Millis) = ((ms.value - new js.Date(ms.value).getTimezoneOffset() * 60000) / 86400000).floor.toLong
    val d = day(nowMs) - day(thenMs)
    if d == 0 then com.histmersion.web.GoalPage.DaysText("today")
    else if d > 0 then (if d == 1 then com.histmersion.web.GoalPage.DaysText("yesterday") else DaysText(s"$d days ago"))
    else if d == -1 then com.histmersion.web.GoalPage.DaysText("tomorrow")
    else DaysText(s"in ${-d} days")

  /** When it was last checked for new publications and when next: the currency schedule, else the watcher. */
  def schedule(v: GoalView, watcher: Option[ManagerInfo], lastCheck: Option[com.histmersion.web.GoalPage.LastCheck], nowMs: com.histmersion.web.GoalPage.ScheduleNowMs): Option[com.histmersion.web.GoalPage.ScheduleText] =
    val currency = v.schedules.find(_.kind == ScheduleKind.Currency)
    val last = currency.flatMap(_.lastRunAt).map(_.value).orElse(lastCheck.map(_.value))
    val next = currency.flatMap(_.nextRunAt).map(_.value).orElse(watcher.flatMap(_.nextTickAt).map(_.value))
    val parts = last.map(t => s"checked for new publications ${days(Millis(js.Date.parse(t)), nowMs)}").toList ++
      next.map(t => s"next check ${days(Millis(js.Date.parse(t)), nowMs)}").toList
    Option.when(parts.nonEmpty)(ScheduleText(parts.mkString(" · ")))

  /** The goal's managers paused (all of them): what "Pause this goal" undoes. */
  def paused(ms: List[ManagerInfo]): Boolean = ms.nonEmpty && ms.forall(_.state == Managers.State.Paused.wire)

  /** The address of a list page that shows only this goal's items. */
  def listOf(section: ResearchSection, goal: com.histmersion.api.automation.Goal.Slug): com.histmersion.web.GoalPage.LinkTarget =
    LinkTarget(s"${Route.Research(section).path}?goal=${js.URIUtils.encodeURIComponent(goal.value)}")

  /** Library and the map, filtered to the goal's sources. */
  def libraryOf(goal: com.histmersion.api.automation.Goal.Slug): com.histmersion.web.GoalPage.LinkTarget =
    LinkTarget(s"${Route.Library(LibrarySection.Sources).path}#goal=${js.URIUtils.encodeURIComponent(goal.value)}")
  def mapOf(goal: com.histmersion.api.automation.Goal.Slug): com.histmersion.web.GoalPage.LinkTarget = LinkTarget(s"${Route.Explore.path}#goal=${js.URIUtils.encodeURIComponent(goal.value)}")

  // ── the page ────────────────────────────────────────────────────────────────────────────────────

  private def write(f: () => Unit): Unit = if Auth.writableNow then f() else WindowManager.open(WindowKey.ApiKey)

  def pane(ui: UiConfig, slug: com.histmersion.web.GoalPage.PaneSlug): HtmlElement =
    val view = Var(Option.empty[Either[String, GoalView]])
    val managers = Var(List.empty[ManagerInfo])
    val items = Var(List.empty[ManagerItem])
    val backlog = Var(Option.empty[BacklogCount])
    val acquire = Var(Option.empty[AcquireCount])
    val note = Var(Option.empty[HeaderNote])
    val editing = Var(false)

    def load(): Unit =
      ApiClient.goalView(ApiClient.GoalSlug(slug.value)).onComplete(r => view.set(Some(r.toEither.left.map(_.getMessage))))
      ApiClient.queueForGoal(ApiClient.QueueName(Queue.Kind.Tasks.label.value), ApiClient.GoalSlug(slug.value)).foreach(l => backlog.set(Some(BacklogCount(l.items.size))))
      ApiClient.queueForGoal(ApiClient.QueueName(Queue.Kind.Acquire.label.value), ApiClient.GoalSlug(slug.value)).foreach(l => acquire.set(Some(AcquireCount(l.items.size))))
      if ui.agentsEnabled.value then
        ApiClient.managers.foreach { all =>
          val mine = all.filter(_.goal.contains(slug))
          managers.set(mine)
          scala.concurrent.Future
            .sequence(mine.map(m => ApiClient.managerItems(ApiClient.ManagerItemsId(m.id.value), None, com.histmersion.api.Offset(0), com.histmersion.api.Paging.Limit(StepLimit))))
            .foreach(pages => items.set(pages.flatMap(_.items)))
        }

    def checkNow(): Unit = write { () =>
      ApiClient.startRun(Agents.RunRequest(Agents.RunRequest.Pipeline(Agents.Pipeline.CurrencyCheck.wire.value), Agents.RunRequest.Target(slug.value))).onComplete {
        case scala.util.Success(r) =>
          note.set(Some(HeaderNote(s"Checking for new publications: agent work ${r.id.value.take(8)} started."))); load()
        case scala.util.Failure(e) => note.set(Some(HeaderNote(s"Could not start the check: ${e.getMessage}")))
      }
    }

    def pauseOrResume(): Unit = write { () =>
      val resume = paused(managers.now())
      scala.concurrent.Future
        .sequence(managers.now().map(m => ApiClient.managerAction(ApiClient.ManagerActionId(m.id.value), if resume then com.histmersion.web.ApiClient.ManagerAction("resume") else com.histmersion.web.ApiClient.ManagerAction("pause"))))
        .onComplete { r =>
          note.set(
            r.failed.toOption
              .map(e => HeaderNote(s"Could not ${if resume then "resume" else "pause"} the goal: ${e.getMessage}"))
              .orElse(Some(HeaderNote(if resume then "The goal goes on."
              else "The goal is paused: its agents start nothing new for it.")))
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
          Router.retitle(Route.Goal(slug), UiLabels.Text(v.goal.title.value))
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
                  ActivityView.history(slug, com.histmersion.api.Paging.Limit(HistoryLimit))
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
          g.parentSlug.map(p => span(" · part of ", RouteLink(Route.Goal(p), Wording.humanize(Wording.Id(p.value)))))
        ),
        g.notes.filter(_.value.trim.nonEmpty).map(n => p(cls := "goal-question", n.value)),
        div(
          cls := "muted goal-state",
          GoalsView.badge(g.status),
          child.text <-- managers.combineWith(items).map { (ms, is) =>
            val ref = com.histmersion.api.automation.Goal.Ref(g.slug.value)
            val pr = ResearchOverview.progressOf(ref, ms)
            val watcher = ms.find(_.id == Managers.watchId(ref))
            val lastCheck =
              is.filter(i => i.kind.value == Managers.CurrencyCheck && i.status.value == "done").map(_.updatedAt.value).maxOption.map(LastCheck(_))
            val parts = List(
              if paused(ms) then Some("Paused") else None,
              Option.when(pr.total > 0)(s"${pr.done} of ${pr.total} steps done"),
              schedule(v, watcher, lastCheck, ScheduleNowMs(js.Date.now())).map(_.value)
            ).flatten
            if parts.isEmpty then "" else parts.mkString(" · ", " · ", "")
          }
        ),
        child.maybe <-- note.map(_.map(n => p(cls := "muted goal-note", role := "status", n.value)))
      ),
      div(
        cls := "btn-group goal-actions",
        Option.when(ui.agentsEnabled.value)(
          button(
            typ := "button",
            cls := "btn goal-check",
            "Check for new publications now",
            onClick --> (_ => checkNow())
          )
        ),
        Option.when(ui.agentsEnabled.value)(
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
            if !ui.agentsEnabled.value then "Research agents are off on this server: no step runs."
            else
              v.plan.fold("No steps yet: the goal's agents have not planned its work.")(pl =>
                s"Approved plan: ${Plural.count(Plural.CountN(pl.subGoals.size), com.histmersion.api.Plural.CountSingular("sub-goal"))}, ${Plural.count(Plural.CountN(pl.queries.size), com.histmersion.api.Plural.CountSingular("query"), com.histmersion.api.Plural.CountPlural("queries"))}, " +
                  s"${Plural.count(Plural.CountN(pl.works.size), Plural.CountSingular("work"))} to read."
              )
          )
        else ol(cls := "goal-steps-list", steps(is).take(StepLimit).zipWithIndex.map((i, n) => step(i, com.histmersion.api.automation.Agents.StepInfo.Seq(n + 1))))
      }
    )

  private def step(i: ManagerItem, n: com.histmersion.api.automation.Agents.StepInfo.Seq): HtmlElement =
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
        strong(stepName(i).value),
        div(
          cls := "muted",
          mark match
            case Mark.Done    => span("Done", i.outcome.filter(_.value.nonEmpty).map(o => s" · ${o.value.take(160)}"))
            case Mark.Going   => span("Running now")
            case Mark.Stopped => span("Stopped", i.outcome.filter(_.value.nonEmpty).map(o => s" · ${o.value.take(160)}"))
            case Mark.Todo    =>
              span(
                i.hold.map(h => s"Waiting: ${Option(h.reason.value).filter(_.nonEmpty).getOrElse("held")}").getOrElse("To do")
              ),
          i.runId.map(id => span(" · ", RouteLink(Route.Run(Route.Run.Id(id.value)), "see what it did")))
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
              i.runId.foreach(id => ApiClient.jobControl(com.histmersion.api.SpatialHierarchyResult.Job.Id(id.value), com.histmersion.api.Api.JobControl.Action("pause")).onComplete(_ => busy.set(false)))
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
      backlog: Signal[Option[com.histmersion.web.GoalPage.BacklogCount]],
      acquire: Signal[Option[com.histmersion.web.GoalPage.AcquireCount]],
      slug: com.histmersion.web.GoalPage.ProducedSlug
  ): HtmlElement =
    val articles = v.links.filter(_.kind == LinkKind.Article)
    val sources = v.links.count(_.kind == LinkKind.Source)
    def countLink(n: Signal[Option[com.histmersion.web.GoalPage.ProducedCount]], one: com.histmersion.api.Plural.Noun.Singular, many: com.histmersion.api.Plural.Noun.Plural, to: com.histmersion.web.GoalPage.LinkTarget) =
      a(
        href := to.value,
        child.text <-- n.map(_.fold("…")(c => if c.value == 1 then s"1 $one" else s"$c $many")),
        onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); Router.goHref(to.value) } }
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
                List[HtmlElement](span(" · "), com.histmersion.web.ArticleLink(com.histmersion.api.research.Article.Slug(l.ref.value), l.title.map(_.value).getOrElse(l.ref.value)))
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
            href := libraryOf(slug).value,
            Plural.count(Plural.CountN(sources), com.histmersion.api.Plural.CountSingular("source")),
            onClick --> { ev =>
              if Router.plain(ev) then {
                ev.preventDefault(); showFiltered(slug, Route.Library(LibrarySection.Sources))
              }
            }
          ),
          " · ",
          a(
            href := mapOf(slug).value,
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
        countLink(backlog, com.histmersion.api.Plural.Noun.Singular("item"), com.histmersion.api.Plural.Noun.Plural("items"), listOf(ResearchSection.Backlog, slug))
      ),
      div(
        cls := "li",
        span(cls := "li-what", "Waiting for you to acquire"),
        countLink(acquire, com.histmersion.api.Plural.Noun.Singular("item"), com.histmersion.api.Plural.Noun.Plural("items"), listOf(ResearchSection.Acquire, slug))
      )
    )

  /** Library or the map, showing only this goal's sources. */
  private def showFiltered(slug: com.histmersion.api.automation.Goal.Slug, to: Route): Unit =
    UiState.set(UiState.goal := Some(slug.value))(push = com.histmersion.web.UiState.SetPush(true))
    Router.go(to)
