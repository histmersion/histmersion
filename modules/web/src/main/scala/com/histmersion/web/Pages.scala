package com.histmersion.web

import com.histmersion.api.*
import com.histmersion.api.UiNotices.Board
import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.StringAsIsCodec

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The pages of Library, Research and Settings, and of single items (a run, a goal, a place…), shown
  * over Explore — which stays as it is underneath, hidden, so coming back finds the map where it was.
  * Each section shows the view that used to be a toolbar window; Research's and Settings' sections are
  * in a side navigation on the left. */
object Pages:

  private val currentAttr = htmlAttr("aria-current", StringAsIsCodec)

  def element(ui: UiConfig): HtmlElement =
    val shown = Router.route.combineWith(Router.missingSignal).distinct
    div(
      cls := "place-page",
      cls("is-hidden") <-- shown.map((r, missing) => r == Route.Explore && !missing),
      child <-- shown.map((r, missing) => if missing then notFound else page(ui, r))
    )

  private def page(ui: UiConfig, r: Route): HtmlElement = r match
    case Route.Explore                => div()
    case Route.Library(s)             => libraryPage(ui, s)
    case Route.Settings(s)            => layout("Settings sections", settingsSide(s), Some(s.label), settings(ui, s))
    case Route.Place(id)              => single(placePage(id))
    case Route.Event(id)              => single(eventPage(id))
    case Route.Source(_) | Route.Article(_) => single(p(cls := "muted", "Loading…"))
    case _                            =>
      layout("Research sections", researchSide(ui, r), None, research(ui, r))

  // ── the frame ───────────────────────────────────────────────────────────────────────────────────

  private def layout(navName: String, side: List[Modifier[HtmlElement]], heading: Option[String],
      body: HtmlElement): HtmlElement =
    div(
      cls := "page-layout",
      navTag(cls := "side-nav", aria.label := navName, side),
      mainTag(
        cls := "page-main wm-content",
        idAttr := "page-main",
        heading.map(h => h1(cls := "page-title", h)),
        body
      )
    )

  private def single(body: HtmlElement): HtmlElement =
    div(cls := "page-layout page-single", mainTag(cls := "page-main wm-content", idAttr := "page-main", body))

  private def sideLink(r: Route, label: String, on: Boolean, mods: Modifier[HtmlElement]*): HtmlElement =
    RouteLink(r, cls := "side-link", cls("on") := on, currentAttr := (if on then "page" else "false"),
      span(cls := "side-label", label), mods)

  private def group(name: String): HtmlElement = div(cls := "side-group", name)

  /** A watched list's link: bold while it has something new, with the count of what waits on someone. */
  private def notice(b: Board): Modifier[HtmlElement] =
    val c = Notices.counter(b)
    List(
      cls("has-news") <-- c.map(_.exists(_.fresh)),
      child.maybe <-- c.map(_.flatMap(x => UiNotices.badge(x.count))).distinct
        .map(_.map(n => span(cls := s"side-count${if b == Board.Approvals then " side-count--attn" else ""}",
          aria.hidden := true, n))),
      child.maybe <-- c.map(Notices.spoken).distinct.map(s => Option.when(s.nonEmpty)(span(cls := "sr-only", s", $s"))))

  // ── Library ─────────────────────────────────────────────────────────────────────────────────────

  /** Library's lists beside the filter panel (which lies over this page's left edge), or a way of adding
    * sources. */
  private def libraryPage(ui: UiConfig, s: LibrarySection): HtmlElement =
    if s.isList then div(cls := "page-layout library-layout", mainTag(cls := "page-main library-main", idAttr := "page-main",
      h1(cls := "sr-only", "Library"), library.LibraryPage.element(ui, s)))
    else single(library.AddSources.page(s))

  // ── Research ────────────────────────────────────────────────────────────────────────────────────

  private def researchSide(ui: UiConfig, r: Route): List[Modifier[HtmlElement]] =
    val on = r.researchSection
    def item(s: ResearchSection, mods: Modifier[HtmlElement]*) =
      val to = if s == ResearchSection.Activity then Route.Activity(ActivityTab.Runs) else Route.Research(s)
      val target = if s == ResearchSection.Activity && !ui.agentsEnabled then Route.Activity(ActivityTab.Jobs) else to
      sideLink(target, s.label, on.contains(s), mods)
    ResearchSection.values.toList.filter(s => ui.agentsEnabled || !s.agents).map { s =>
      s match
        case ResearchSection.Decisions => item(s, notice(Board.Approvals))
        case ResearchSection.Goals     => item(s, notice(Board.Goals))
        case ResearchSection.Backlog   => item(s, notice(Board.Tasks))
        case ResearchSection.Acquire   => item(s, notice(Board.Acquire))
        case ResearchSection.Activity  => item(s, notice(Board.Log))
        case _                         => item(s)
    } :+ p(cls := "side-note muted",
      "The backlog holds the agents' next steps. “To acquire” holds books and papers only you can get.")

  private def research(ui: UiConfig, r: Route): HtmlElement = r match
    case Route.Research(s) =>
      val body = s match
        case ResearchSection.Overview  =>
          if ui.agentsEnabled then ManagersView.pane else agentsOff("Research agents are off on this server.")
        case ResearchSection.Decisions => if ui.agentsEnabled then ApprovalsView.pane else agentsOff("Nothing waits for a decision: the research agents are off on this server.")
        case ResearchSection.Goals     => GoalsView.pane
        case ResearchSection.Backlog   => QueueView.pane(Queue.Kind.Tasks)
        case ResearchSection.Acquire   => QueueView.pane(Queue.Kind.Acquire)
        case ResearchSection.Models    => if ui.agentsEnabled then ModelsView.pane else agentsOff("No AI models are used: the research agents are off on this server.")
        case ResearchSection.Activity  => RunsView.pane
      section(Router.title(r), body)
    case Route.Activity(t) => section("Activity", div(activityTabs(ui, t), activity(ui, t)))
    case Route.Goal(slug)  => item(r, GoalsView.goalPane(slug))
    case Route.Run(id)     => item(r, RunsView.runPane(id))
    case Route.Manager(id) => item(r, ManagersView.managerPane(id))
    case Route.Job(id)     => item(r, jobPage(id))
    case _                 => notFound

  private def section(heading: String, body: HtmlElement): HtmlElement = div(h1(cls := "page-title", heading), body)

  private def agentsOff(text: String): HtmlElement =
    p(cls := "muted", text, " Their settings are ", code("agents.enabled"), " in the server's configuration.")

  private def activityTabs(ui: UiConfig, on: ActivityTab): HtmlElement =
    navTag(cls := "page-tabs", aria.label := "Activity",
      ActivityTab.values.toList.filter(t => ui.agentsEnabled || !t.agents).map(t =>
        RouteLink(Route.Activity(t), cls := "page-tab", cls("on") := t == on,
          currentAttr := (if t == on then "page" else "false"), t.label)))

  private def activity(ui: UiConfig, t: ActivityTab): HtmlElement = t match
    case ActivityTab.Runs    => RunsView.pane
    case ActivityTab.Jobs    => JobsView.pane
    case ActivityTab.Worklog => QueueView.pane(Queue.Kind.Log)

  /** One item's page: its name, "pin as window" (it floats over whatever is shown next; the page goes back
    * to its list), and its view. */
  private def item(r: Route, body: HtmlElement): HtmlElement =
    val heading = Var(Router.title(r))
    div(
      cls := "item-page",
      Router.titles.filter(_._1 == r).map(_._2) --> heading.writer,
      div(cls := "item-head",
        h1(cls := "page-title", child.text <-- heading.signal),
        Route.window(r).map(key =>
          button(typ := "button", cls := "btn btn--ghost pin-btn", title := "Keep it open in a window over the other pages",
            "Pin as window",
            onClick --> { _ =>
              WindowManager.open(key)
              Router.go(parent(r))
            }))),
      body
    )

  /** The list an item's page belongs to. */
  private def parent(r: Route): Route = r match
    case Route.Goal(_)    => Route.Research(ResearchSection.Goals)
    case Route.Manager(_) => Route.Research(ResearchSection.Overview)
    case _                => Route.Activity(ActivityTab.Runs)

  /** A job's page: an agent run is its run's page; any other job, its row of the jobs list. */
  private def jobPage(id: String): HtmlElement =
    val job = Var(Option.empty[Either[String, JobInfo]])
    div(
      onMountCallback(_ => ApiClient.job(id).onComplete(r => job.set(Some(r.toEither.left.map(_.getMessage))))),
      child <-- job.signal.map {
        case None                                         => p(cls := "muted", "Loading…")
        case Some(Left(m))                                => p(cls := "form-error", role := "alert", s"Could not read job $id: $m")
        case Some(Right(j)) if j.kind == JobKind.Agent.wire =>
          Router.go(Route.Run(j.id), push = false)
          p(cls := "muted", "Loading…")
        case Some(Right(j))                               =>
          Router.retitle(Route.Job(id), Wording.jobKindOf(j.kind))
          ul(cls := "job-list", JobsView.row(j, mine = false))
      },
      p(RouteLink(Route.Activity(ActivityTab.Jobs), "All background jobs"))
    )

  // ── Settings ────────────────────────────────────────────────────────────────────────────────────

  private def settingsSide(on: SettingsSection): List[Modifier[HtmlElement]] =
    SettingsSection.values.toList.map(s => sideLink(Route.Settings(s), s.label, s == on))

  private def settings(ui: UiConfig, s: SettingsSection): HtmlElement = s match
    case SettingsSection.Data  => div(cls := "tool-pane", MapRegionsView.panel)
    case SettingsSection.Keys  => Auth.keyPane
    case SettingsSection.Credits => CreditsView.pane
    case SettingsSection.About => AboutView.pane

  // ── places and events ───────────────────────────────────────────────────────────────────────────

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
          Router.retitle(route, c.title)
          div(cls := "cite-tooltip place-tip card-page-card", Preview.cardView(c))
      }
    )

  private def placePage(id: String): HtmlElement =
    cardPage(Preview.Target.Place(id, None), Route.Place(id),
      a(cls := "btn", href := s"${Route.Explore.path}#placeId=${scala.scalajs.js.URIUtils.encodeURIComponent(id)}",
        "Show on the map",
        onClick --> { ev =>
          if Router.plain(ev) then
            ev.preventDefault()
            UiState.set(UiState.placeIds := List(id))()
            PlacesInput.sync()
            Router.go(Route.Explore)
        }))

  private def eventPage(id: String): HtmlElement =
    cardPage(Preview.Target.Event(id), Route.Event(id), RouteLink(Route.Explore, cls := "btn", "Back to the map"))

  private def notFound: HtmlElement =
    single(div(h1(cls := "page-title", "No such page"),
      p("This address names no page of Histmersion. ", RouteLink(Route.Explore, "Explore the map"), " or ",
        RouteLink(Route.Library(LibrarySection.Sources), "browse the library"), ".")))
