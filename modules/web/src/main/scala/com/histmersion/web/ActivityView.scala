package com.histmersion.web

import com.histmersion.api.*
import com.histmersion.api.Activity.{Entry, EntryKind}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** Research › Activity: ONE time-ordered list of agent runs, background jobs, worklog lines and the person's
  * own decisions ([[Activity]], merged and paged by the server), filtered by chips, each entry linked to what
  * it touched. Runs and long jobs get Pause / Resume / Cancel in place. Also the overview's short list and a
  * goal's history. */
object ActivityView:

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** The row's first words: what is going on, or what it was and when (`hm` = the clock time it shows). */
  def when(e: Entry, hm: String): String = e.entryKind match
    case Some(EntryKind.Run | EntryKind.Job) =>
      e.state match
        case "running"             => "Running"
        case "queued"              => "Waiting its turn"
        case "waiting"             => "Waits for you"
        case "paused"              => s"Paused · $hm"
        case "failed"              => s"Problem · $hm"
        case "cancelled"           => s"Cancelled · $hm"
        case _                     => s"Done · $hm"
    case Some(EntryKind.Decision) => s"You decided · $hm"
    case _                        => s"Note · $hm"

  /** Does the row read as a problem (its first words in the attention colour)? */
  def problem(e: Entry): Boolean = e.state == "failed"

  /** The actions a row offers: an agent run or a long background job that is going (pause, cancel), paused
    * (resume, cancel) or waiting its turn (cancel). */
  def actions(e: Entry): List[String] =
    val controllable = e.entryKind.contains(EntryKind.Run) ||
      (e.entryKind.contains(EntryKind.Job) && JobActions.Stoppable.contains(e.what))
    if !controllable || e.job.isEmpty then Nil
    else e.state match
      case "running"            => List("pause", "cancel")
      case "paused"             => List("resume", "cancel")
      case "queued" | "waiting" => List("cancel")
      case _                    => Nil

  /** What the row says it is, in words: agent work, a background job, a note, a decision. */
  def kindOf(e: Entry): String = e.entryKind.fold(Wording.humanize(e.kind))(Wording.activityKind)

  /** The query string of a page of the list: its goal, kept from one chip to the next. */
  def search(goal: Option[String]): String = goal.fold("")(g => s"?goal=${js.URIUtils.encodeURIComponent(g)}")

  /** A `name=value` of a page's query string (`?goal=…`), decoded; `None` when absent or blank. */
  def param(search: String, name: String): Option[String] =
    Option(search).getOrElse("").stripPrefix("?").split('&').toList.map(_.split("=", 2)).collectFirst {
      case Array(k, v) if k == name && v.nonEmpty => try js.URIUtils.decodeURIComponent(v.replace('+', ' ')) catch case _: Throwable => v
    }.map(_.trim).filter(_.nonEmpty)

  private val Months = Vector("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

  /** `12:40` today, `29 Oct` before, in the reader's own time zone. */
  private def clock(iso: String): String =
    val d = new js.Date(iso)
    if d.getTime().isNaN then iso.take(16)
    else
      def two(n: Double) = f"${n.toInt}%02d"
      if d.toDateString() == new js.Date().toDateString() then s"${two(d.getHours())}:${two(d.getMinutes())}"
      else s"${d.getDate().toInt} ${Months(d.getMonth().toInt)}"

  // ── one row ─────────────────────────────────────────────────────────────────────────────────────

  private def goalLink(g: String): HtmlElement =
    RouteLink(Route.Goal(g), cls := "goal-ref", Wording.humanize(g))

  /** What an entry touched, as links. */
  private def refs(e: Entry): List[Modifier[HtmlElement]] =
    val kind = e.entryKind
    List(
      Some(span(kindOf(e))),
      e.goal.map(g => span(" for goal ", goalLink(g))),
      kind.filter(_ == EntryKind.Run).flatMap(_ => e.job).map(id => span(" · ", RouteLink(Route.Run(id), "see what it did"))),
      kind.filter(_ == EntryKind.Job).flatMap(_ => e.job).map(id => span(" · ", RouteLink(Route.Job(id), "the job"))),
      kind.filter(k => k == EntryKind.Log || k == EntryKind.Decision).flatMap(_ => e.job).map(id =>
        span(" · by ", RouteLink(Route.Job(id), if e.by.contains(Activity.Writer.Agent.wire) || kind.contains(EntryKind.Decision)
          then s"agent work ${id.take(8)}" else s"job ${id.take(8)}"))),
      e.citekey.filterNot(_ => kind.contains(EntryKind.Decision)).map(ck => span(" · ", SourceLink(ck))),
      e.article.map(slug => span(" · ", com.histmersion.web.ArticleLink(slug, slug))),
      e.queueItem.filter(_ => kind.contains(EntryKind.Job)).map(id =>
        span(" · for ", QueueItemLink(Queue.Kind.Acquire, id, s"“To acquire” item $id"))),
      e.approval.map(id => span(" · ", RouteLink(Route.Research(ResearchSection.Decisions), s"decision #$id")))
    ).flatten

  private def headline(e: Entry): Modifier[HtmlElement] = e.entryKind match
    case Some(EntryKind.Decision) =>
      val what = Agents.ApprovalKind.parse(e.what).fold(Wording.humanize(e.what))(Wording.approvalKind)
      val outcome = Agents.ApprovalState.parse(e.state).fold(e.state)(Wording.approvalState).toLowerCase
      List[Modifier[HtmlElement]](span(s"$what — $outcome"), e.citekey.map(ck => span(" · ", SourceLink(ck))),
        Option(e.text).filter(t => t.nonEmpty && e.citekey.isEmpty).map(t => span(s" · $t")))
    case Some(EntryKind.Log) => span(cls := "activity-text", e.text.take(400))
    case _ =>
      val title = ActivityMarkdown.headline(e)
      val progress = Option(e.text).filter(t => t.nonEmpty && !title.contains(t))
      List[Modifier[HtmlElement]](strong(title), progress.map(p => span(cls := "muted", s" · ${p.take(160)}")))

  /** One entry: when, what, what it touched, and its controls. `changed` reloads the list after an action. */
  def row(e: Entry, changed: () => Unit): HtmlElement =
    val busy = Var(false)
    val failure = Var(Option.empty[String])
    def act(action: String): Unit =
      if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
      else
        busy.set(true)
        e.job.foreach(id => ApiClient.jobControl(id, action).onComplete { r =>
          busy.set(false)
          failure.set(r.failed.toOption.map(_.getMessage))
          changed()
        })
    li(
      cls := s"activity-row activity-${e.kind}",
      cls("is-problem") := problem(e),
      dataAttr("key") := e.key,
      dataAttr("state") := e.state,
      span(cls := "activity-when", when(e, clock(e.endedAt.getOrElse(e.at)))),
      div(cls := "activity-main",
        div(cls := "activity-what", headline(e)),
        e.error.filter(_.nonEmpty).map(err => div(cls := "activity-error", err.take(300))),
        div(cls := "activity-refs muted", refs(e)),
        child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m)))),
      actions(e) match
        case Nil => emptyNode
        case as  => div(cls := "btn-group activity-actions", as.map { a =>
          button(typ := "button", cls := (if a == "cancel" then "btn btn--ghost" else "btn"),
            disabled <-- busy.signal, Wording.capitalize(a), aria.label := s"${Wording.capitalize(a)}: ${ActivityMarkdown.headline(e).take(80)}",
            onClick --> (_ => act(a)))
        })
    )

  // ── the lists ───────────────────────────────────────────────────────────────────────────────────

  /** A list of one query, its first page loaded on mount and every 15 s while shown (unless older pages
    * were asked for); "Show older" appends the next page. */
  private final class Feed(show: Activity.Show, goal: Option[String], limit: Int):
    val entries = Var(List.empty[Entry])
    val next    = Var(Option.empty[String])
    val counts  = Var(Activity.Counts())
    val loaded  = Var(false)
    val failure = Var(Option.empty[String])
    private var older = false

    def first(): Unit =
      ApiClient.activity(Some(show.wire), None, goal, Some(limit), None).onComplete { r =>
        loaded.set(true)
        r.fold(e => failure.set(Some(e.getMessage)), p => {
          failure.set(None); entries.set(p.entries); next.set(p.next); counts.set(p.counts); older = false
        })
      }

    def more(): Unit = next.now().foreach { c =>
      ApiClient.activity(Some(show.wire), None, goal, Some(limit), Some(c)).onComplete(_.foreach { p =>
        older = true
        entries.update(_ ++ p.entries); next.set(p.next)
      })
    }

    def tick(): Unit = if !older then first()

  private def list(feed: Feed, empty: String): HtmlElement =
    div(
      child.maybe <-- feed.failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", s"Could not read the activity: $m"))),
      child <-- feed.entries.signal.combineWith(feed.loaded.signal).map { (es, ok) =>
        if !ok then p(cls := "muted", "Loading…")
        else if es.isEmpty then p(cls := "muted activity-empty", empty)
        else ul(cls := "activity-list", es.map(row(_, () => feed.first())))
      },
      child.maybe <-- feed.next.signal.map(_.map(_ =>
        button(typ := "button", cls := "btn btn--ghost activity-older", "Show older", onClick --> (_ => feed.more()))))
    )

  private def polled(feed: Feed): Modifier[HtmlElement] = List(
    onMountCallback(_ => feed.first()),
    EventStream.periodic(15000).drop(1) --> (_ => feed.tick()))

  /** The chips: each a link to its page, the goal kept; the running and the problems counted. */
  def chips(ui: UiConfig, on: Option[ActivityTab], goal: Option[String], counts: Signal[Activity.Counts]): HtmlElement =
    navTag(cls := "chips activity-chips", aria.label := "Show",
      ActivityTab.values.toList.filter(t => t.isChip && (ui.agentsEnabled || !t.agents)).map { t =>
        val selected = on.contains(t)
        a(cls := "fchip", cls("on") := selected, href := s"${Route.Activity(t).path}${search(goal)}",
          htmlAttr("aria-current", com.raquo.laminar.codecs.StringAsIsCodec) := (if selected then "page" else "false"),
          onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); Router.go(Route.Activity(t), search = search(goal)) } },
          t.label,
          child.text <-- counts.map(c => t match
            case ActivityTab.Running  => s" · ${c.running}"
            case ActivityTab.Problems => if c.problemsWeek > 0 then s" · ${c.problemsWeek} this week" else ""
            case _                    => ""))
      })

  /** The whole page: the chips, the list, and the detailed lists it sums up. */
  def pane(ui: UiConfig, tab: ActivityTab, goal: Option[String]): HtmlElement =
    val feed = Feed(tab.show.getOrElse(Activity.Show.Everything), goal, Activity.DefaultLimit)
    div(cls := "activity-pane",
      polled(feed),
      chips(ui, Some(tab), goal, feed.counts.signal),
      goal.map(g => p(cls := "muted activity-goal", "Only what concerns the goal ", goalLink(g), " · ",
        RouteLink(Route.Activity(tab), "show everything"))),
      list(feed, "Nothing here yet."),
      details(ui))

  /** The detailed lists, for when one kind of work must be seen in full. */
  private def details(ui: UiConfig): HtmlElement =
    p(cls := "muted activity-details", "In detail: ",
      ActivityTab.values.toList.filter(t => !t.isChip && (ui.agentsEnabled || !t.agents)).flatMap(t =>
        List(span(" · "), RouteLink(Route.Activity(t), t.label))).drop(1))

  /** The overview's few latest entries, linking to the whole list. */
  def short(limit: Int): HtmlElement =
    val feed = Feed(Activity.Show.Everything, None, limit)
    div(cls := "activity-short", polled(feed), list(feed, "Nothing has happened yet."))

  /** A goal's slice of the list. */
  def history(goal: String, limit: Int): HtmlElement =
    val feed = Feed(Activity.Show.Everything, Some(goal), limit)
    div(cls := "activity-history", polled(feed), list(feed, "Nothing has happened for this goal yet."))

  /** The overview's chips: each a link to the whole list, shown that way. */
  def shortChips(ui: UiConfig): HtmlElement =
    val counts = Var(Activity.Counts())
    div(onMountCallback(_ => ApiClient.activity(None, None, None, Some(1), None).foreach(p => counts.set(p.counts))),
      chips(ui, None, None, counts.signal))
