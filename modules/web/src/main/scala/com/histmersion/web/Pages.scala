package com.histmersion
package web

import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.automation.managers.ManagerInfo
import com.histmersion.api.automation.activity.Counts
import com.histmersion.api.automation.activity.Entry

import com.histmersion.api.Wording
import com.histmersion.api.instance.UiNotices.Board
import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.StringAsIsCodec

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.automation.Queue
import com.histmersion.api.instance.{UiConfig, UiNotices}
import com.histmersion.api.jobs.JobInfo
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.UiLabels
import com.histmersion.web.setup.SetupPage
import com.histmersion.api.automation.Goal
import com.histmersion.web.library.LibraryPage
import com.histmersion.web.library.AddSources
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Activity
import com.histmersion.api.automation.AgentJobKinds
import com.histmersion.web.settings.SettingsPages
import com.histmersion.lint.{tolerated, LintRule}

/** The pages of Library, Research and Settings, and of single items (a run, a goal, a place…), shown over Explore —
  * which stays as it is underneath, hidden, so coming back finds the map where it was. Each section shows the view that
  * used to be a toolbar window; Research's and Settings' sections are in a side navigation on the left.
  */
object Pages:
  type EventId = EventId.T
  object EventId extends Newtype[String, Pure]
  type JobId = JobId.T
  object JobId extends Newtype[String, Pure]
  type PlaceId = PlaceId.T
  object PlaceId extends Newtype[String, Pure]
  type UrlQuery = UrlQuery.T
  object UrlQuery extends Newtype[String, Pure]

  private val currentAttr = htmlAttr("aria-current", StringAsIsCodec)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def element(ui: UiConfig): HtmlElement =
    // a Research page's query (`?goal=`) is part of what it shows
    val shown = Router.route
      .combineWith(Router.missingSignal, Router.search)
      .map((r, missing, s) => (r, missing, if r.place == PlaceTab.Research then s else UrlQuery("")))
      .distinct
    div(
      cls := "place-page",
      cls("is-hidden") <-- shown.map((r, missing, _) => r == Route.Explore && !missing),
      child <-- shown.map((r, missing, s) => if missing then notFound else page(ui, r, s))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def page(ui: UiConfig, r: Route, search: UrlQuery): HtmlElement = r match
    case Route.Explore     => div()
    case Route.Library(s)  => libraryPage(ui, s)
    case Route.Settings(s) =>
      layout(UiLabels.Text("Settings sections"), settingsSide(s), Some(s.label), settings(ui, s))
    case Route.Setup                        => SetupPage.element(ui)
    case Route.Place(id)                    => single(placePage(id))
    case Route.Event(id)                    => single(eventPage(id))
    case Route.Source(_) | Route.Article(_) => single(p(cls := "muted", "Loading…"))
    case _                                  =>
      layout(
        UiLabels.Text("Research sections"),
        researchSide(ui, r),
        None,
        research(ui, r, ActivityView.param(search, ActivityView.ParamName("goal")).map(g => Goal.Slug(g.value)))
      )

  // ── the frame ───────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def layout(
      navName: UiLabels.Text,
      side: List[Modifier[HtmlElement]],
      heading: Option[UiLabels.Text],
      body: HtmlElement
  ): HtmlElement =
    div(
      cls := "page-layout",
      navTag(cls := "side-nav", aria.label := navName.value, side),
      mainTag(
        cls := "page-main wm-content",
        idAttr := "page-main",
        heading.map(h => h1(cls := "page-title", h)),
        body
      )
    )

  private def single(body: HtmlElement): HtmlElement =
    div(cls := "page-layout page-single", mainTag(cls := "page-main wm-content", idAttr := "page-main", body))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def sideLink(r: Route, label: UiLabels.Text, on: Boolean, mods: Modifier[HtmlElement]*): HtmlElement =
    RouteLink(
      r,
      cls := "side-link",
      cls("on") := on,
      currentAttr := (if on then "page" else "false"),
      span(cls := "side-label", label),
      mods
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def group(name: UiLabels.Text): HtmlElement = div(cls := "side-group", name)

  /** A watched list's link: bold while it has something new, with the count of what waits on someone. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def notice(b: Board): Modifier[HtmlElement] =
    val c = Notices.counter(b)
    List(
      cls("has-news") <-- c.map(_.exists(_.fresh.fresh)),
      child.maybe <-- c
        .map(_.flatMap(x => UiNotices.badge(x.count)))
        .distinct
        .map(
          _.map(n =>
            span(
              cls := s"side-count${if b == Board.Approvals then " side-count--attn" else ""}",
              aria.hidden := true,
              n
            )
          )
        ),
      child.maybe <-- c
        .map(Notices.spoken)
        .distinct
        .map(s => Option.when(s.value.nonEmpty)(span(cls := "sr-only", shown", $s")))
    )

  // ── Library ─────────────────────────────────────────────────────────────────────────────────────

  /** Library's lists beside the filter panel (which lies over this page's left edge), or a way of adding sources.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def libraryPage(ui: UiConfig, s: LibrarySection): HtmlElement =
    if s.isList then
      div(
        cls := "page-layout library-layout",
        mainTag(
          cls := "page-main library-main",
          idAttr := "page-main",
          h1(cls := "sr-only", "Library"),
          LibraryPage.element(ui, s)
        )
      )
    else single(AddSources.page(s))

  // ── Research ────────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def researchSide(ui: UiConfig, r: Route): List[Modifier[HtmlElement]] =
    val on = r.researchSection
    def item(s: ResearchSection, mods: Modifier[HtmlElement]*) =
      val target = if s == ResearchSection.Activity then Route.Activity(ActivityTab.Everything) else Route.Research(s)
      sideLink(target, s.label, on.contains(s), mods)
    ResearchSection.values.toList.filter(s => ui.agentsEnabled.agentsEnabled || !s.agents.enabled).map { s =>
      s match
        case ResearchSection.Decisions => item(s, notice(Board.Approvals))
        case ResearchSection.Goals     => item(s, notice(Board.Goals))
        case ResearchSection.Backlog   => item(s, notice(Board.Tasks))
        case ResearchSection.Acquire   => item(s, notice(Board.Acquire))
        case ResearchSection.Activity  => item(s, notice(Board.Log))
        case _                         => item(s)
    } :+ p(
      cls := "side-note muted",
      "The backlog holds the agents' next steps. “To acquire” holds books and papers only you can get."
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def research(ui: UiConfig, r: Route, goal: Option[Goal.Slug]): HtmlElement = r match
    case Route.Research(s) =>
      val body = s match
        case ResearchSection.Overview  => ResearchOverview.pane(ui)
        case ResearchSection.State     => ResearchState.pane
        case ResearchSection.Decisions =>
          if ui.agentsEnabled.agentsEnabled then ApprovalsView.pane
          else agentsOff(UiLabels.Text("Nothing waits for a decision: the research agents are off on this server."))
        case ResearchSection.Goals   => GoalsView.pane
        case ResearchSection.Backlog => QueueView.pane(api.automation.queue.Docket.Tasks, goal)
        case ResearchSection.Acquire => QueueView.pane(api.automation.queue.Docket.Acquire, goal)
        case ResearchSection.Models  =>
          if ui.agentsEnabled.agentsEnabled then ModelsView.pane
          else agentsOff(UiLabels.Text("No AI models are used: the research agents are off on this server."))
        case ResearchSection.Activity => RunsView.pane
      section(Router.title(r), body)
    case Route.Activity(t) => section(UiLabels.Text("Activity"), activity(ui, t, goal))
    case Route.Goal(slug)  => item(r, GoalPage.pane(ui, slug))
    case Route.Run(id)     => item(r, RunsView.runPane(RunInfo.Id(id.value)))
    case Route.Manager(id) => item(r, ManagersView.managerPane(ManagerInfo.Id(id.value)))
    case Route.Job(id)     => item(r, jobPage(id))
    case _                 => notFound

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def section(heading: UiLabels.Text, body: HtmlElement): HtmlElement =
    div(h1(cls := "page-title", heading), body)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def agentsOff(text: UiLabels.Text): HtmlElement =
    p(cls := "muted", text, " Their settings are ", code("agents.enabled"), " in the server's configuration.")

  /** The merged list through one of its chips — or one of the detailed lists it sums up, under the chips and a way back
    * to the list.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def activity(ui: UiConfig, t: ActivityTab, goal: Option[Goal.Slug]): HtmlElement =
    if t.isChip then ActivityView.pane(ui, t, goal)
    else
      div(
        cls := "activity-detail",
        ActivityView.chips(ui, None, None, Val(Counts())),
        p(
          cls := "muted",
          RouteLink(Route.Activity(ActivityTab.Everything), "← The whole activity list"),
          s" · ${t.label}"
        ),
        t match
          case ActivityTab.Runs =>
            if ui.agentsEnabled.agentsEnabled then RunsView.pane
            else agentsOff(UiLabels.Text("No agent runs: the research agents are off on this server."))
          case ActivityTab.Worklog => QueueView.pane(api.automation.queue.Docket.Log)
          case _                   => JobsView.pane
      )

  /** One item's page: its name, "pin as window" (it floats over whatever is shown next; the page goes back to its
    * list), and its view.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def item(r: Route, body: HtmlElement): HtmlElement =
    val heading = Var(Router.title(r))
    div(
      cls := "item-page",
      Router.titles.filter(_._1 == r).map(_._2) --> heading.writer,
      div(
        cls := "item-head",
        h1(cls := "page-title", child.text <-- heading.signal.map(_.value)),
        Route
          .window(r)
          .map(key =>
            button(
              typ := "button",
              cls := "btn btn--ghost pin-btn",
              title := "Keep it open in a window over the other pages",
              "Pin as window",
              onClick --> { _ =>
                WindowManager.open(key)
                Router.go(parent(r))
              }
            )
          )
      ),
      body
    )

  /** The list an item's page belongs to. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def parent(r: Route): Route = r match
    case Route.Goal(_)    => Route.Research(ResearchSection.Goals)
    case Route.Manager(_) => Route.Research(ResearchSection.Overview)
    case _                => Route.Activity(ActivityTab.Everything)

  /** A job's page: an agent run is its run's page; any other job, its row of the jobs list. */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def jobPage(id: JobId): HtmlElement =
    val job = Var(Option.empty[Either[String, JobInfo]])
    div(
      onMountCallback(_ =>
        ApiClient.job(JobInfo.Id(id.value)).onComplete(r => job.set(Some(r.toEither.left.map(_.getMessage))))
      ),
      child <-- job.signal.map {
        case None          => p(cls := "muted", "Loading…")
        case Some(Left(m)) => p(cls := "form-error", role := "alert", s"Could not read job $id: $m")
        case Some(Right(j)) if j.kind == AgentJobKinds.Agent.kind =>
          Router.go(Route.Run(Route.Run.Id(j.id.value)), push = Router.Push.Replace)
          p(cls := "muted", "Loading…")
        case Some(Right(j)) =>
          Router.retitle(Route.Job(id), UiLabels.Text(Wording.jobKindOf(j.kind)))
          div(ul(cls := "job-list", JobsView.row(j, mine = false)), jobControls(j), QueueView.ofJob(j.id))
      },
      p(RouteLink(Route.Activity(ActivityTab.JobList), "All background jobs"))
    )

  /** Pause, resume and cancel of a long background job, on its page. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def jobControls(j: JobInfo): HtmlElement =
    val e = Entry(
      Entry.Key(s"job:${j.id.value}"),
      Entry.Kind("job"),
      Entry.At(j.createdAt.value),
      state = Entry.State(j.status.value),
      what = Entry.What(j.kind.value),
      job = Some(j.id)
    )
    ActivityView.actions(e) match
      case Nil => div()
      case _   =>
        ul(
          cls := "activity-list job-controls",
          ActivityView.row(e, () => Router.go(Route.Job(Route.Job.Id(j.id.value)), push = Router.Push.Replace))
        )

  // ── Settings ────────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def settingsSide(on: SettingsSection): List[Modifier[HtmlElement]] =
    val (common, rare) = SettingsSection.values.toList.partition(s => !s.rare.rare)
    common.map(s => sideLink(Route.Settings(s), s.label, s == on)) ++ List(group(UiLabels.Text("Rarely needed"))) ++
      rare.map(s => sideLink(Route.Settings(s), s.label, s == on)) ++
      List(RouteLink(Route.Setup, cls := "side-link setup-again", span(cls := "side-label", "Run the setup again")))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def settings(ui: UiConfig, s: SettingsSection): HtmlElement =
    SettingsPages.page(ui, s)

  // ── places and events ───────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def cardPage(target: Preview.Target, route: Route, mapLink: HtmlElement): HtmlElement =
    val card = Var(Option.empty[Either[String, Preview.Card]])
    div(
      cls := "item-page card-page",
      onMountCallback(_ => Preview.fetch(target).onComplete(r => card.set(Some(r.toEither.left.map(_.getMessage))))),
      div(cls := "item-head", mapLink),
      child <-- card.signal.map {
        case None           => p(cls := "muted", "Loading…")
        case Some(Left(m))  => p(cls := "form-error", role := "alert", s"Could not read it: $m")
        case Some(Right(c)) =>
          Router.retitle(route, UiLabels.Text(c.title.value))
          div(cls := "cite-tooltip place-tip card-page-card", Preview.cardView(c))
      }
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def placePage(id: PlaceId): HtmlElement =
    cardPage(
      Preview.Target.Place(id, None),
      Route.Place(id),
      a(
        cls := "btn",
        href := s"${Route.Explore.path}#placeId=${scala.scalajs.js.URIUtils.encodeURIComponent(id.value)}",
        "Show on the map",
        onClick --> { ev =>
          if Router.plain(ev) then
            ev.preventDefault()
            UiState.set(UiState.placeIds := List(id.value))()
            PlacesInput.sync()
            Router.go(Route.Explore)
        }
      )
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def eventPage(id: EventId): HtmlElement =
    cardPage(Preview.Target.Event(id), Route.Event(id), RouteLink(Route.Explore, cls := "btn", "Back to the map"))

  private def notFound: HtmlElement =
    single(
      div(
        h1(cls := "page-title", "No such page"),
        p(
          "This address names no page of Histmersion. ",
          RouteLink(Route.Explore, "Explore the map"),
          " or ",
          RouteLink(Route.Library(LibrarySection.Sources), "browse the library"),
          "."
        )
      )
    )
