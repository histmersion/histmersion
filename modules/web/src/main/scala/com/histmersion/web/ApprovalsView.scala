package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.core.Agents.{ApprovalInfo, ApprovalKind, ApprovalState, Decision}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The approvals window (docs/research-embedded-agents.md §5.2): changes agent runs PROPOSED — a
  * source's status, a disagreement edge — each with its evidence and the quote code verified, and
  * approve / reject behind the write gate. Approving applies the change through the ordinary source
  * patch. Reloaded when the window opens, after a decision, and when a run it watches settles
  * ([[changed]]); there is nothing to poll for otherwise. A page at a time ([[ListPaging]]): a reload
  * after a decision or a settled run keeps the pages loaded; the toggle and "refresh" start over. */
object ApprovalsView:

  /** How many a page lists. */
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
      case Some(ApprovalKind.GoalPlan) =>
        val p = a.plan
        s"plan for goal ${a.subject.getOrElse("?")}: ${Plural.count(p.fold(0)(_.subGoals.size), "sub-goal")}, " +
          s"${Plural.count(p.fold(0)(_.queries.size), "query", "queries")}, ${Plural.count(p.fold(0)(_.works.size), "work")}"
      case Some(ApprovalKind.ArticlePublish) =>
        a.article match
          case Some(d) if d.mode == GoalPlans.DraftMode.Create => s"publish the new article ${a.subject.getOrElse("?")}: ${d.title}"
          case _ => s"append a section to the article ${a.subject.getOrElse("?")}"
      case None => s"${a.kind} on ${a.subject.getOrElse(a.citekey)}"

  def isOpen(a: ApprovalInfo): Boolean = a.state == ApprovalState.Open.wire

  /** The evidence line under the change — unless the plan block shows it: a plan's evidence IS its
    * rationale, which [[planBlock]] prints at its top (#882: it used to appear twice). */
  def evidenceLine(a: ApprovalInfo): Option[String] =
    Option(a.evidence).filter(_.nonEmpty).filterNot(e => a.plan.exists(_.rationale == e))

  /** The `state` query for the toggle: the open ones, or every one. */
  def stateQuery(all: Boolean): Option[String] = if all then Some("all") else None

  /** The heading's word for what the toggle shows: `open` approvals, or every approval. */
  def qualifier(all: Boolean): Option[String] = Option.when(!all)(ApprovalState.Open.wire)

  /** The self-check line of a draft. */
  def selfCheck(d: GoalPlans.ArticleDraft): String =
    if d.problems.isEmpty then "The code self-check found nothing."
    else s"The code self-check still found ${Plural.count(d.problems.size, "problem")} after the writer's last attempt:"

  /** A plan, as a person weighs it: sub-goals, queries, works (in the corpus or to acquire), dropped. */
  private[web] def planBlock(plan: GoalPlans.Plan): HtmlElement =
    div(cls := "approval-plan",
      Option(plan.rationale).filter(_.nonEmpty).map(r => p(cls := "approval-evidence", r)),
      Option.when(plan.subGoals.nonEmpty)(div(h5("Sub-goals"), ul(plan.subGoals.map(g =>
        li(code(g.slug), s" — ${g.title}", g.notes.map(n => span(cls := "muted", s": $n"))))))),
      Option.when(plan.queries.nonEmpty)(div(h5("Search queries"), ul(plan.queries.map(q => li(q))))),
      Option.when(plan.works.nonEmpty)(div(h5("Works"), ul(plan.works.map { w =>
        li(w.title.getOrElse(w.doi), s" (${w.year.fold("n.d.")(_.toString)}) ",
          a(href := s"https://doi.org/${w.doi}", target := "_blank", rel := "noopener", w.doi),
          if w.inCorpus then span(" — in the corpus as ", w.citekey.fold(span("?"))(ck => SourceLink(ck)))
          else span(cls := "muted", if w.isOa then " — to acquire (open access)" else " — to acquire"),
          Option(w.why).filter(_.nonEmpty).map(y => div(cls := "muted", y)))
      }))),
      Option.when(plan.dropped.nonEmpty)(htmlTag("details")(htmlTag("summary")(s"dropped by code (${plan.dropped.size})"),
        ul(plan.dropped.map(d => li(cls := "muted", d))))))

  /** A draft: its self-check, what it cites and leaves out, and the text itself. */
  private[web] def draftBlock(d: GoalPlans.ArticleDraft): HtmlElement =
    div(cls := "approval-draft",
      p(cls := "muted", s"${d.kind} · \"${d.title}\"" + d.section.fold("")(s => s" · under $s") +
        s" · cites ${Plural.count(d.cited.size, "source")} · ${d.filter}"),
      p(cls := (if d.problems.isEmpty then "form-ok" else "run-paused"), selfCheck(d)),
      Option.when(d.problems.nonEmpty)(ul(d.problems.map(x => li(x)))),
      Option.when(d.notUsed.nonEmpty)(htmlTag("details")(htmlTag("summary")(s"not used (${d.notUsed.size})"),
        ul(d.notUsed.map(n => li(cls := "muted", n))))),
      htmlTag("details")(htmlTag("summary")("the text"), pre(cls := "trace-body draft-text", d.text)))

  // ── state and view ──────────────────────────────────────────────────────────────────────────────

  /** The rows and "Show older" ([[PolledList]]): nothing is polled — a decision, a settled run
    * ([[changed]]) or the toggle reloads — but the window gets the same restart when the last row shown
    * is gone, and drops an answer that lands after it closed. */
  private val list = PolledList[ApprovalInfo](
    fetch = (upTo, before) => ApiClient.approvals(stateQuery(UiState.approvalsAll.now()), Some(Limit), upTo = upTo, before = before),
    id = key,
    live = (_, _) => false)
  private val busy    = Var(Set.empty[Long])

  private def key(a: ApprovalInfo): String = a.id.toString

  /** Reload what is shown, keeping the pages loaded ([[ListPaging]]). */
  private def refresh(): Unit = list.refresh()

  private def decide(a: ApprovalInfo, d: Decision, note: String): Unit =
    busy.update(_ + a.id)
    ApiClient.decide(a.id, d, Some(note).map(_.trim).filter(_.nonEmpty)).onComplete { r =>
      busy.update(_ - a.id)
      r.failed.foreach(e => list.failure.set(Some(e.getMessage)))
      refresh()
      // decided here or in a goal's window: the count drops, and the decision is not news to its maker
      Notices.ownWrite(com.histmersion.core.UiNotices.Board.Approvals)
    }

  def pane: HtmlElement =
    div(
      cls := "tool-pane approvals-pane",
      list.lifecycle,
      changed.events --> (_ => refresh()),
      UiState.approvalsAll.signal.changes --> (_ => list.restart()),
      div(cls := "pane-bar",
        LayerToggle(Icon.Approvals, "decided too", UiState.approvalsAll.signal,
          on => UiState.set(UiState.approvalsAll := on)()),
        button(typ := "button", cls := "btn", "refresh", onClick --> (_ => list.restart()))),
      p(cls := "muted", "Changes agent runs proposed. Approving applies the change to the source; " +
        "rejecting keeps it on record and changes nothing."),
      ErrorLine(list.failure.signal),
      child.maybe <-- list.listing.signal.combineWith(UiState.approvalsAll.signal, list.loaded.signal).map { (l, all, isLoaded) =>
        Option.when(isLoaded && l.rows.nonEmpty)(p(cls := "muted list-heading", ListPaging.heading(l, "approval", qualifier(all), _.state)))
      },
      child.maybe <-- list.listing.signal.combineWith(UiState.approvalsAll.signal, list.loaded.signal).map { (l, all, isLoaded) =>
        if !isLoaded then Some(p(cls := "muted", "Loading…"))
        else Option.when(l.rows.isEmpty)(p(cls := "muted", if all then "No approvals yet." else "Nothing waits for a decision."))
      },
      // keyed by the row itself: an unchanged row keeps its element (and the note being typed in it)
      ul(cls := "job-list approval-list", children <-- list.listing.signal.map(_.rows).split(identity)((a, _, _) => row(a))),
      list.olderButton
    )

  /** One approval: what it would change, the evidence, and approve / reject. Shared with the goal
    * window, which shows the open plan of its goal. */
  private[web] def row(a: ApprovalInfo): HtmlElement =
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
            case Some(ApprovalKind.GoalPlan) =>
              span("plan for goal ", a.subject.fold(span("?"))(g =>
                button(typ := "button", cls := "link-btn", g, onClick --> (_ => WindowManager.open(WindowKey.Goal(g))))))
            case Some(ApprovalKind.ArticlePublish) =>
              span(if a.article.exists(_.mode == GoalPlans.DraftMode.Create) then "publish the new article " else "append a section to ",
                a.subject.fold(span("?"))(s => if a.article.exists(_.mode == GoalPlans.DraftMode.Create) then strong(s) else com.histmersion.web.ArticleLink(s, s)))
            case _ =>
              span("status of ", SourceLink(a.citekey), s": ${a.current.getOrElse("?")} → ",
                strong(a.status.getOrElse("?")))),
        evidenceLine(a).map(e => p(cls := "approval-evidence", e)),
        a.plan.map(planBlock),
        a.article.map(draftBlock),
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
                input(typ := "text", cls := "field", aria.label := "note", placeholder := "note (optional)",
                  controlled(value <-- note.signal, onInput.mapToValue --> note.writer)),
                button(typ := "button", cls := "btn btn--primary", "approve", disabled <-- busy.signal.map(_(a.id)),
                  onClick --> (_ => decide(a, Decision.Approve, note.now()))),
                button(typ := "button", cls := "btn", "reject", disabled <-- busy.signal.map(_(a.id)),
                  onClick --> (_ => decide(a, Decision.Reject, note.now()))))
          }))
      )
    )
