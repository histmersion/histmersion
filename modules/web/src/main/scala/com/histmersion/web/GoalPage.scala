package com.histmersion
package web

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.{Plural, UiLabels, Wording}
import com.histmersion.api.automation.Goal.Plans.{GoalView, LinkKind, ScheduleKind}
import com.histmersion.api.automation.Managers.{ManagerInfo, ManagerItem}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import com.histmersion.api.automation.{Agents, Managers, ManagersApi, Queue}
import com.histmersion.api.instance.UiConfig
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Goal
import com.histmersion.api.RowCount
import com.histmersion.api.Offset
import com.histmersion.api.Paging
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.Api
import com.histmersion.api.research.Article

/** One research goal's page: its question, state and schedule ("checked for new publications 3 days ago · next check
  * …"), its actions (check for new publications now, pause the goal, edit), its plan — each step linked to the agent
  * work that did it —, what it produced (articles, sources brought in, open backlog items, items waiting to be
  * acquired) and its history (the goal's slice of Activity).
  */
object GoalPage:
  type DaysText = DaysText.T
  object DaysText extends Newtype[String, Pure]
  type LastCheck = LastCheck.T
  object LastCheck extends Newtype[String, Pure]
  type LinkTarget = LinkTarget.T
  object LinkTarget extends Newtype[String, Pure]
  type Millis = Millis.T
  object Millis extends Newtype[Double, Pure]
  type ScheduleText = ScheduleText.T
  object ScheduleText extends Newtype[String, Pure]

  /** How many of the goal's steps the plan lists, and how many entries its history. */

  type ScheduleNowMs = Millis
  val ScheduleNowMs: Millis.type = Millis
  type HeaderNote = HeaderNote.T
  object HeaderNote extends Newtype[String, Pure]

  val StepLimit = 12
  val HistoryLimit = 8

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** A step's mark: done, going now, or still to do (its position). */
  enum Mark derives CanEqual:
    case Done, Going, Todo, Stopped

  def markOf(i: ManagerItem): Mark = i.status.value match
    case "done"                             => Mark.Done
    case "active"                           => Mark.Going
    case "failed" | "stopped" | "cancelled" => Mark.Stopped
    case _                                  => Mark.Todo

  /** A step's name: what kind of work it is, on what. */
  def stepName(i: ManagerItem): Agents.StepInfo.Name =
    val what = UiLabels.label(Wording.Id(i.kind.value)).value
    Agents.StepInfo.Name(
      if i.target.value.isEmpty || i.target.value == i.managerId.value then what
      else s"$what — ${Wording.value(i.target.value)}"
    )

  /** The steps the plan shows: going now first, then still to do, then done, newest first in each. */
  def steps(items: List[ManagerItem]): List[ManagerItem] =
    def rank(i: ManagerItem) = markOf(i) match
      case Mark.Going   => 0
      case Mark.Todo    => 1
      case Mark.Stopped => 2
      case Mark.Done    => 3
    items.sortBy(i =>
      (rank(i), -js.Date.parse(Option(i.updatedAt.value).filter(_.nonEmpty).getOrElse(i.createdAt.value)))
    )

  /** `3 days ago`, `today`, `in 2 days`, from two instants in epoch ms: calendar days in the reader's time zone. */
  def days(thenMs: Millis, nowMs: Millis): DaysText =
    def day(ms: Millis) = ((ms.value - new js.Date(ms.value).getTimezoneOffset() * 60000) / 86400000).floor.toLong
    val d = day(nowMs) - day(thenMs)
    if d == 0 then DaysText("today")
    else if d > 0 then (if d == 1 then DaysText("yesterday") else DaysText(s"$d days ago"))
    else if d == -1 then DaysText("tomorrow")
    else DaysText(s"in ${-d} days")

  /** When it was last checked for new publications and when next: the currency schedule, else the watcher. */
  def schedule(
      v: GoalView,
      watcher: Option[ManagerInfo],
      lastCheck: Option[LastCheck],
      nowMs: ScheduleNowMs
  ): Option[ScheduleText] =
    val currency = v.schedules.find(_.kind == ScheduleKind.Currency)
    val last = currency.flatMap(_.lastRunAt).map(_.value).orElse(lastCheck.map(_.value))
    val next = currency.flatMap(_.nextRunAt).map(_.value).orElse(watcher.flatMap(_.nextTickAt).map(_.value))
    val parts = last.map(t => s"checked for new publications ${days(Millis(js.Date.parse(t)), nowMs)}").toList ++
      next.map(t => s"next check ${days(Millis(js.Date.parse(t)), nowMs)}").toList
    Option.when(parts.nonEmpty)(ScheduleText(parts.mkString(" · ")))

  /** The goal's managers paused (all of them): what "Pause this goal" undoes. */
  def paused(ms: List[ManagerInfo]): Boolean = ms.nonEmpty && ms.forall(_.state == Managers.State.Paused.wire)

  /** The address of a list page that shows only this goal's items. */
  def listOf(section: ResearchSection, goal: Goal.Slug): LinkTarget =
    LinkTarget(s"${Route.Research(section).path}?goal=${js.URIUtils.encodeURIComponent(goal.value)}")

  /** Library and the map, filtered to the goal's sources. */
  def libraryOf(goal: Goal.Slug): LinkTarget =
    LinkTarget(s"${Route.Library(LibrarySection.Sources).path}#goal=${js.URIUtils.encodeURIComponent(goal.value)}")
  def mapOf(goal: Goal.Slug): LinkTarget = LinkTarget(
    s"${Route.Explore.path}#goal=${js.URIUtils.encodeURIComponent(goal.value)}"
  )

  // ── the page ────────────────────────────────────────────────────────────────────────────────────

  private def write(f: () => Unit): Unit = if Auth.writableNow then f() else WindowManager.open(WindowKey.ApiKey)

  def pane(ui: UiConfig, slug: Goal.Slug): HtmlElement =
    val view = Var(Option.empty[Either[String, GoalView]])
    val managers = Var(List.empty[ManagerInfo])
    val items = Var(List.empty[ManagerItem])
    val backlog = Var(Option.empty[RowCount])
    val acquire = Var(Option.empty[RowCount])
    val note = Var(Option.empty[HeaderNote])
    val editing = Var(false)

    def load(): Unit =
      ApiClient.goalView(slug).onComplete(r => view.set(Some(r.toEither.left.map(_.getMessage))))
      ApiClient.queueForGoal(Queue.Kind.Tasks.label, slug).foreach(l => backlog.set(Some(RowCount(l.items.size))))
      ApiClient.queueForGoal(Queue.Kind.Acquire.label, slug).foreach(l => acquire.set(Some(RowCount(l.items.size))))
      if ui.agentsEnabled.value then
        ApiClient.managers.foreach { all =>
          val mine = all.filter(_.goal.contains(slug))
          managers.set(mine)
          scala.concurrent.Future
            .sequence(mine.map(m => ApiClient.managerItems(m.id, None, Offset(0), Paging.Limit(StepLimit))))
            .foreach(pages => items.set(pages.flatMap(_.items)))
        }

    def checkNow(): Unit = write { () =>
      ApiClient
        .startRun(
          Agents.RunRequest(
            Agents.RunRequest.Pipeline(Agents.Pipeline.CurrencyCheck.wire.value),
            Agents.RunRequest.Target(slug.value)
          )
        )
        .onComplete {
          case scala.util.Success(r) =>
            note.set(Some(HeaderNote(s"Checking for new publications: agent work ${r.id.value.take(8)} started.")));
            load()
          case scala.util.Failure(e) => note.set(Some(HeaderNote(s"Could not start the check: ${e.getMessage}")))
        }
    }

    def pauseOrResume(): Unit = write { () =>
      val resume = paused(managers.now())
      scala.concurrent.Future
        .sequence(
          managers
            .now()
            .map(m =>
              ApiClient.managerAction(
                m.id,
                if resume then ManagersApi.Action("resume") else ManagersApi.Action("pause")
              )
            )
        )
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
                  ActivityView.history(slug, Paging.Limit(HistoryLimit))
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
      note: Signal[Option[HeaderNote]],
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
            val ref = Goal.Ref(g.slug.value)
            val pr = ResearchOverview.progressOf(g.slug, ms)
            val watcher = ms.find(_.id == Managers.watchId(ref))
            val lastCheck =
              is.filter(i => i.kind.value == Managers.CurrencyCheck && i.status.value == "done")
                .map(_.updatedAt.value)
                .maxOption
                .map(LastCheck(_))
            val parts = List(
              if paused(ms) then Some("Paused") else None,
              Option.when(pr.total.value > 0)(s"${pr.done} of ${pr.total} steps done"),
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
                s"Approved plan: ${Plural.count(Plural.CountN(pl.subGoals.size), Plural.CountSingular("sub-goal"))}, ${Plural.count(Plural.CountN(pl.queries.size), Plural.CountSingular("query"), Plural.CountPlural("queries"))}, " +
                  s"${Plural.count(Plural.CountN(pl.works.size), Plural.CountSingular("work"))} to read."
              )
          )
        else
          ol(
            cls := "goal-steps-list",
            steps(is).take(StepLimit).zipWithIndex.map((i, n) => step(i, Agents.StepInfo.Seq(n + 1)))
          )
      }
    )

  private def step(i: ManagerItem, n: Agents.StepInfo.Seq): HtmlElement =
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
            case Mark.Stopped =>
              span("Stopped", i.outcome.filter(_.value.nonEmpty).map(o => s" · ${o.value.take(160)}"))
            case Mark.Todo =>
              span(
                i.hold
                  .map(h => s"Waiting: ${Option(s"${h.reason}").filter(_.nonEmpty).getOrElse("held")}")
                  .getOrElse("To do")
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
              i.runId.foreach(id =>
                ApiClient
                  .jobControl(SpatialHierarchyResult.Job.Id(id.value), Api.JobControl.Action("pause"))
                  .onComplete(_ => busy.set(false))
              )
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
      backlog: Signal[Option[RowCount]],
      acquire: Signal[Option[RowCount]],
      slug: Goal.Slug
  ): HtmlElement =
    val articles = v.links.filter(_.kind == LinkKind.Article)
    val sources = v.links.count(_.kind == LinkKind.Source)
    def countLink(n: Signal[Option[RowCount]], one: Plural.Noun.Singular, many: Plural.Noun.Plural, to: LinkTarget) =
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
                List[HtmlElement](
                  span(" · "),
                  ArticleLink(Article.Slug(l.ref.value), l.title.map(_.value).getOrElse(l.ref.value))
                )
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
            Plural.count(Plural.CountN(sources), Plural.CountSingular("source")).value,
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
        countLink(
          backlog,
          Plural.Noun.Singular("item"),
          Plural.Noun.Plural("items"),
          listOf(ResearchSection.Backlog, slug)
        )
      ),
      div(
        cls := "li",
        span(cls := "li-what", "Waiting for you to acquire"),
        countLink(
          acquire,
          Plural.Noun.Singular("item"),
          Plural.Noun.Plural("items"),
          listOf(ResearchSection.Acquire, slug)
        )
      )
    )

  /** Library or the map, showing only this goal's sources. */
  private def showFiltered(slug: Goal.Slug, to: Route): Unit =
    UiState.set(UiState.goal := Some(slug.value))(push = UiState.SetPush(true))
    Router.go(to)
