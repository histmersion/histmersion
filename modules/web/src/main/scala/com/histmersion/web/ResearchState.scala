package com.histmersion
package web

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.Wording
import com.histmersion.api.automation.Queue
import com.histmersion.api.research.{OpenWork, ResearchApi}
import com.histmersion.api.research.OpenWork.{OpenItem, Verdict, Who}
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import hearth.kindlings.optics.syntax.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

final class ResearchState:
  import ResearchState.*

  private val state: Request[OpenWork.State] = Request()
  private val words: Var[String] = Var("")
  private val histories: Var[Map[Queue.QueueItem.Id, Request[OpenWork.History]]] = Var(Map.empty)
  private val expanded: Var[Set[Queue.QueueItem.Id]] = Var(Set.empty)
  private val handing: Request[Queue.QueueItem] = Request()

  private def load(): Unit = state.ask(ApiClient.call(ResearchApi.state)(OpenWork.Scope()))

  private val loaded: Signal[Option[OpenWork.State]] = state.signal.map(_.shown)

  private val filtered: Signal[Option[OpenWork.State]] =
    loaded.combineWith(words.signal).map((s, w) => s.map(shown(_, w)))

  private def historyOf(id: Queue.QueueItem.Id): Request[OpenWork.History] =
    histories
      .now()
      .getOrElse(
        id, {
          val r = Request[OpenWork.History]()
          r.ask(ApiClient.call(ResearchApi.acquisition)(id))
          histories.update(_.updated(id, r))
          r
        }
      )

  private def toggle(id: Queue.QueueItem.Id): Unit =
    if expanded.now().contains(id) then expanded.update(_ - id)
    else
      val _ = historyOf(id)
      expanded.update(_ + id)

  private def hand(t: OpenItem.Task): Unit =
    if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
    else
      handing.askThen(ApiClient.queuePatch(t.item, Queue.QueuePatch(actor = Some(handedTo(t))))) {
        case Loaded.Ready(_) => load()
        case _               => ()
      }

  private def history(id: Queue.QueueItem.Id): HtmlElement =
    val r = historyOf(id)
    div(
      cls := "state-history",
      dataAttr("history") := s"$id",
      ErrorLine.of(r.signal),
      child <-- r.signal.map(_.shown).map {
        case None    => p(cls := "muted", "Loading…")
        case Some(h) =>
          if h.attempts.isEmpty then p(cls := "muted", "Not checked yet.")
          else
            ul(
              cls := "state-attempts",
              h.attempts.map(a =>
                li(
                  cls := "state-attempt",
                  div(s"${a.at.shown} · ${a.trigger.wording} · ${a.outcome.wording}"),
                  a.oa.map(o => o.line).filter(_.nonEmpty).map(l => div(cls := "muted", l)),
                  ul(a.answers.map(x => li(cls := "state-answer", s"${x.line}: ${x.why.sentence}")))
                )
              )
            )
      }
    )

  private def expander(id: Queue.QueueItem.Id): HtmlElement =
    button(
      typ := "button",
      cls := "btn btn--ghost state-why",
      aria.expanded <-- expanded.signal.map(_.contains(id)),
      child.text <-- expanded.signal.map(e =>
        if e.contains(id) then "Hide the attempts" else "What the providers said"
      ),
      onClick --> (_ => toggle(id))
    )

  private def opened(id: Queue.QueueItem.Id): Modifier[HtmlElement] =
    child.maybe <-- expanded.signal.map(e => Option.when(e.contains(id))(history(id)))

  private def body(i: OpenItem): List[Modifier[HtmlElement]] = i match
    case OpenItem.Decision(a, _) =>
      List(
        div(cls := "state-title", s"${Wording.capitalize(ApprovalsView.describe(a))}?"),
        ApprovalsView.evidenceLine(a).map(e => div(cls := "muted state-reason", s"Reason given: ${e.take(240)}")),
        div(
          cls := "btn-group state-actions",
          ResearchOverview.evidence(a).map((label, to) => L.a(cls := "btn state-evidence", href := s"$to", s"$label"))
        )
      )
    case a: OpenItem.Acquisition =>
      List(
        div(cls := "state-title", firstLine(a.text)),
        div(cls := "muted state-reason", a.verdict.wording),
        div(
          cls := "btn-group state-actions",
          a.verdict match
            case Verdict.ManualDownload(Some(url)) =>
              L.a(cls := "btn state-evidence", href := s"$url", target := "_blank", "Open the copy")
            case _ => emptyNode
          ,
          expander(a.item)
        ),
        opened(a.item)
      )
    case t: OpenItem.Task =>
      List(
        div(cls := "state-title", firstLine(t.text)),
        div(
          cls := "btn-group state-actions",
          button(
            typ := "button",
            cls := "btn state-hand",
            disabled <-- handing.signal.map(_.isLoading),
            if t.actor == Who.Owner then "Hand to the agents" else "Take it back",
            onClick --> (_ => hand(t))
          ),
          RouteLink(Route.Research(ResearchSection.Backlog), cls := "btn state-evidence", "See the backlog")
        )
      )
    case r: OpenItem.PausedRun =>
      List(
        div(cls := "state-title", s"Agent work ${r.pipeline} is paused"),
        r.cause.map(c => div(cls := "muted state-reason", c.sentence)),
        r.resumes.map(t => div(cls := "muted state-reason", s"It resumes by itself at ${t.shown}")),
        div(
          cls := "btn-group state-actions",
          RouteLink(Route.Run(Route.RunId.of(r.run)), cls := "btn state-evidence", "See the run")
        )
      )

  private def row(i: OpenItem): HtmlElement =
    val head: List[Modifier[HtmlElement]] = List(
      cls := "row state-row",
      dataAttr("item") := key(i),
      dataAttr("kind") := i.kind.label
    )
    val note = Option.when(i.creatorNotRecorded)(span(cls := "state-tag", "creator not recorded")).toList
    div((head ++ note ++ body(i))*)

  private def group(title: String, slot: String, items: Signal[Vector[OpenItem]]): HtmlElement =
    div(
      cls := "state-group",
      cls("is-empty") <-- items.map(_.isEmpty),
      dataAttr("group") := slot,
      h3(cls := "state-group-head", title, " ", span(cls := "muted", child.text <-- items.map(is => s"${is.size}"))),
      children <-- items.map(_.take(PerGroup)).split(key)((_, first, _) => row(first)),
      child.maybe <-- items.map(is =>
        Option.when(is.size > PerGroup)(p(cls := "muted row", s"and ${is.size - PerGroup} more: type to narrow"))
      )
    )

  private def waiting: HtmlElement =
    val owner = filtered.map(_.fold(Vector.empty[(OpenWork.NextStep, Vector[OpenItem])])(_.owner))
    sectionTag(
      cls := "card state-waiting",
      aria.label := "Waiting for you",
      div(cls := "ch", h2("Waiting for you")),
      child.maybe <-- owner.map(o => Option.when(o.isEmpty)(p(cls := "muted row", "Nothing waits for you."))),
      OpenWork.NextStep.values.toList.map(s =>
        group(s.wording, s.label, owner.map(_.find(_._1 == s).fold(Vector.empty)(_._2)))
      )
    )

  private def theirs: HtmlElement =
    val agents = filtered.map(_.fold(Vector.empty[(OpenWork.When, Vector[OpenItem])])(_.agents))
    sectionTag(
      cls := "card state-agents",
      aria.label := "Agents will handle",
      div(cls := "ch", h2("Agents will handle")),
      child.maybe <-- agents.map(o => Option.when(o.isEmpty)(p(cls := "muted row", "Nothing for the agents."))),
      OpenWork.When.values.toList.map(w =>
        group(w.wording, w.label, agents.map(_.find(_._1 == w).fold(Vector.empty)(_._2)))
      )
    )

  private def table: HtmlElement =
    val rows = filtered.map(_.fold(Vector.empty)(acquisitions))
    sectionTag(
      cls := "card state-acquisitions",
      aria.label := "Acquisitions",
      div(cls := "ch", h2("Acquisitions"), span(cls := "muted", child.text <-- rows.map(r => s"${r.size}"))),
      L.table(
        cls := "state-table",
        thead(tr(th("Work"), th("Verdict"), th("Last check"), th("Tries"), th())),
        tbody(
          children <-- rows.map(_.take(Rows)).split(_.item) { (id, first, _) =>
            tr(
              cls := "state-acq",
              dataAttr("item") := s"$id",
              dataAttr("verdict") := first.verdict.label,
              td(firstLine(first.text)),
              td(cls := "state-verdict", first.verdict.label),
              td(first.last.fold("never")(_.shown)),
              td(s"${first.tries}"),
              td(expander(id), opened(id))
            )
          }
        )
      ),
      child.maybe <-- rows.map(r =>
        Option.when(r.size > Rows)(p(cls := "muted row", s"Showing $Rows of ${r.size}: type to narrow"))
      )
    )

  def element: HtmlElement =
    div(
      cls := "research-state",
      onMountCallback(_ => load()),
      div(
        cls := "state-head",
        h2(cls := "state-totals", child.text <-- loaded.map(_.fold("Loading…")(s => s.totals.wording))),
        input(
          typ := "search",
          cls := "field state-filter",
          placeholder := "Filter by text",
          aria.label := "Filter the open items",
          onInput.mapToValue --> words
        )
      ),
      ErrorLine.of(state.signal),
      ErrorLine.of(handing.signal),
      waiting,
      theirs,
      table
    )

object ResearchState:
  private val PerGroup = 50
  private val Rows = 200

  def pane: HtmlElement = ResearchState().element

  def key(i: OpenItem): String = i match
    case d: OpenItem.Decision    => s"decision-${d.approval.id}"
    case a: OpenItem.Acquisition => s"acquisition-${a.item}"
    case t: OpenItem.Task        => s"task-${t.item}"
    case r: OpenItem.PausedRun   => s"run-${r.run}"

  def shown(s: OpenWork.State, words: String): OpenWork.State = s.modify(_.items).using(_.filter(_.mentions(words)))

  def acquisitions(s: OpenWork.State): Vector[OpenItem.Acquisition] =
    s.items.collect { case a: OpenItem.Acquisition => a }

  def handedTo(t: OpenItem.Task): Queue.Creation.Actor =
    if t.actor == Who.Owner then Queue.Creation.Agents else Queue.Creation.Person

  def firstLine(text: Queue.QueueItem.Text): String = s"$text".linesIterator.nextOption().getOrElse("")
