package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.core.Agents.{ApprovalInfo, ApprovalKind, ApprovalState, Decision}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The approvals window (docs/research-embedded-agents.md §5.2): changes agent runs PROPOSED — a
  * source's status, a disagreement edge — each with its evidence and the quote code verified, and
  * approve / reject behind the write gate. Approving applies the change through the ordinary source
  * patch. Reloaded when the window opens, after a decision, and when a run it watches settles
  * ([[changed]]); there is nothing to poll for otherwise. */
object ApprovalsView:

  /** How many are listed. */
  val Limit = 100

  /** A run settled (it may have opened approvals): an open window reloads. */
  val changed: EventBus[Unit] = EventBus()

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** What an approval would change, in words. */
  def describe(a: ApprovalInfo): String =
    ApprovalKind.parse(a.kind) match
      case Some(ApprovalKind.StatusChange) =>
        s"status of ${a.citekey}: ${a.current.getOrElse("?")} → ${a.status.getOrElse("?")}"
      case Some(ApprovalKind.Edge) =>
        s"${a.citekey} ${a.edgeKind.map(_.replace('_', ' ')).getOrElse("?")} ${a.target.getOrElse("?")}"
      case None => s"${a.kind} on ${a.citekey}"

  def isOpen(a: ApprovalInfo): Boolean = a.state == ApprovalState.Open.wire

  /** The `state` query for the toggle: the open ones, or every one. */
  def stateQuery(all: Boolean): Option[String] = if all then Some("all") else None

  // ── state and view ──────────────────────────────────────────────────────────────────────────────

  private val items   = Var(List.empty[ApprovalInfo])
  private val loaded  = Var(false)
  private val failure = Var(Option.empty[String])
  private val busy    = Var(Set.empty[Long])
  // `var`: drop an answer to a superseded request
  private var generation: Int = 0

  private def refresh(): Unit =
    generation += 1
    val mine = generation
    ApiClient.approvals(stateQuery(UiState.approvalsAll.now()), Some(Limit)).onComplete { r =>
      if mine == generation then
        r.fold(e => failure.set(Some(e.getMessage)), as => { failure.set(None); items.set(as); loaded.set(true) })
    }

  private def decide(a: ApprovalInfo, d: Decision, note: String): Unit =
    busy.update(_ + a.id)
    ApiClient.decide(a.id, d, Some(note).map(_.trim).filter(_.nonEmpty)).onComplete { r =>
      busy.update(_ - a.id)
      r.failed.foreach(e => failure.set(Some(e.getMessage)))
      refresh()
    }

  def pane: HtmlElement =
    div(
      cls := "tool-pane approvals-pane",
      onMountCallback { _ => loaded.set(false); refresh() },
      changed.events --> (_ => refresh()),
      UiState.approvalsAll.signal.changes --> (_ => refresh()),
      div(cls := "pane-bar",
        LayerToggle(Icon.Approvals, "decided too", UiState.approvalsAll.signal,
          on => UiState.set(UiState.approvalsAll := on)()),
        button(typ := "button", cls := "btn", "refresh", onClick --> (_ => refresh()))),
      p(cls := "muted", "Changes agent runs proposed. Approving applies the change to the source; " +
        "rejecting keeps it on record and changes nothing."),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child <-- items.signal.combineWith(loaded.signal).map { (as, isLoaded) =>
        if !isLoaded then p(cls := "muted", "Loading…")
        else if as.isEmpty then p(cls := "muted", "Nothing waits for a decision.")
        else ul(cls := "job-list approval-list", as.map(row))
      }
    )

  private def row(a: ApprovalInfo): HtmlElement =
    // the note being typed is this row's own until a decision sends it
    val note = Var("")
    li(
      cls := s"job-row approval-row approval-${a.state}",
      span(cls := s"job-state state-${a.state}", a.state),
      div(
        cls := "job-main",
        div(cls := "job-subject approval-change",
          ApprovalKind.parse(a.kind) match
            case Some(ApprovalKind.Edge) =>
              span(SourceLink(a.citekey), s" ${a.edgeKind.map(_.replace('_', ' ')).getOrElse("?")} ",
                a.target.fold(span("?"))(t => SourceLink(t)))
            case _ =>
              span("status of ", SourceLink(a.citekey), s": ${a.current.getOrElse("?")} → ",
                strong(a.status.getOrElse("?")))),
        Option(a.evidence).filter(_.nonEmpty).map(e => p(cls := "approval-evidence", e)),
        a.quote.filter(_.nonEmpty).map(q => blockQuote(cls := "approval-quote", q)),
        a.note.filter(_.nonEmpty).map(n => p(cls := "muted", s"note: $n")),
        a.result.filter(_.nonEmpty).map(r => p(cls := "muted", r)),
        div(cls := "job-meta muted",
          span(s"#${a.id}"),
          a.runId.map(id => span(" · ", button(typ := "button", cls := "link-btn", s"run ${id.take(8)}",
            onClick --> (_ => WindowManager.open(WindowKey.Run(id)))))),
          span(s" · proposed ${a.createdAt}")),
        Option.when(isOpen(a))(div(
          cls := "approval-actions",
          child <-- Auth.writable.map { w =>
            if !w then Auth.keyNeeded("decide")
            else
              div(cls := "form-row",
                input(typ := "text", cls := "text-input", aria.label := "note", placeholder := "note (optional)",
                  controlled(value <-- note.signal, onInput.mapToValue --> note.writer)),
                button(typ := "button", cls := "btn primary", "approve", disabled <-- busy.signal.map(_(a.id)),
                  onClick --> (_ => decide(a, Decision.Approve, note.now()))),
                button(typ := "button", cls := "btn", "reject", disabled <-- busy.signal.map(_(a.id)),
                  onClick --> (_ => decide(a, Decision.Reject, note.now()))))
          }))
      )
    )
