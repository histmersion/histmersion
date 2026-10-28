package com.histmersion.web

import com.raquo.laminar.api.L.*

/** The app's tools, at the end of the filter bar (and, on a phone, in the map & tools drawer): each
  * opens its window and shows it is open, the same buttons as the map's layer switches. They come in
  * three groups, in this order, each set off by one separator (`.toolbar` in the stylesheet):
  *   1. what is to be done — the research goals, then the backlog that carries them out (and, where
  *      the server runs agents, their runs and the approvals waiting on a person);
  *   2. acquisition — the list of what to get, upload, and merging another collection in;
  *   3. the system itself — jobs, the worklog, the key (when the server wants one), about.
  * The DOM order is the visual order, so the keyboard walks the groups as they are seen. */
object Toolbar:

  /** One of the three groups: its name (read out to a screen reader) and its tools. */
  enum Group(val label: String):
    case Plan     extends Group("what is to be done")
    case Acquire  extends Group("acquisition")
    case System   extends Group("about the system")

  /** A tool: the window it opens, and when it is shown at all. */
  enum Shown:
    case Always, WithAgents, WithKey

  final case class Tool(icon: Icon, label: String, key: WindowKey, shown: Shown = Shown.Always)

  /** The toolbar, as data: the groups in order, each with its tools in order. Pure. */
  val groups: List[(Group, List[Tool])] = List(
    Group.Plan -> List(
      Tool(Icon.Goals, "Research goals", WindowKey.Goals),
      Tool(Icon.Tasks, "Backlog", WindowKey.Tasks),
      Tool(Icon.Runs, "Agent runs", WindowKey.Runs, Shown.WithAgents),
      Tool(Icon.Managers, "Agent managers", WindowKey.Managers, Shown.WithAgents),
      Tool(Icon.Approvals, "Approvals", WindowKey.Approvals, Shown.WithAgents)),
    Group.Acquire -> List(
      Tool(Icon.Acquire, "To acquire", WindowKey.Acquire),
      Tool(Icon.Upload, "Upload", WindowKey.Upload),
      Tool(Icon.Merge, "Merge another collection", WindowKey.Merge)),
    Group.System -> List(
      Tool(Icon.Jobs, "Jobs", WindowKey.Jobs),
      Tool(Icon.Log, "Worklog", WindowKey.Log),
      Tool(Icon.Key, "API key", WindowKey.ApiKey, Shown.WithKey),
      Tool(Icon.Info, "About", WindowKey.About))
  )

  /** The labels a reader sees, group by group, given what the server offers. Pure. */
  def visible(agents: Boolean, keyRequired: Boolean): List[List[String]] =
    groups.map((_, tools) => tools.filter(t => isShown(t.shown, agents, keyRequired)).map(_.label))

  private def isShown(s: Shown, agents: Boolean, keyRequired: Boolean): Boolean = s match
    case Shown.Always     => true
    case Shown.WithAgents => agents
    case Shown.WithKey    => keyRequired

  private def toolButton(t: Tool): HtmlElement =
    val b = WindowToggle(t.icon, t.label, t.key)
    Notices.boardOf(t.key).foreach(board => b.amend(notice(board)))
    if t.key != WindowKey.ApiKey then b
    else b.amend(
      cls("key-held") <-- UiState.apiKey.signal.map(_.isDefined),
      title <-- UiState.apiKey.signal.map(k =>
        if k.isDefined then "API key: set — changes are sent with it" else "API key: needed to make changes"))

  /** A watched window's button ([[Notices]]): bold while its window has something new, with the
    * count of what waits on someone (open approvals, things to acquire, the open backlog). */
  private def notice(board: com.histmersion.api.UiNotices.Board): Modifier[HtmlElement] =
    val c = Notices.counter(board)
    List(
      cls("has-news") <-- c.map(_.exists(_.fresh)),
      child.maybe <-- c.map(_.flatMap(x => com.histmersion.api.UiNotices.badge(x.count))).distinct
        .map(_.map(n => span(cls := "btn-count", aria.hidden := true, n))),
      child.maybe <-- c.map(Notices.spoken).distinct.map(s => Option.when(s.nonEmpty)(span(cls := "sr-only", s", $s"))))

  def element: HtmlElement =
    div(
      cls := "toolbar tool-btns",
      groups.map { (g, tools) =>
        div(
          cls := "btn-group",
          role := "group",
          aria.label := g.label,
          tools.map { t =>
            (t.shown match
              case Shown.Always     => toolButton(t)
              // the embedded agents' windows only where the server runs agents (agents.enabled)
              case Shown.WithAgents => child.maybe <-- RunsView.enabled.signal.map(on => Option.when(on)(toolButton(t)))
              case Shown.WithKey    => child.maybe <-- Auth.requiredSignal.map(r => Option.when(r)(toolButton(t)))
            ): Modifier[HtmlElement]
          }
        )
      }
    )
