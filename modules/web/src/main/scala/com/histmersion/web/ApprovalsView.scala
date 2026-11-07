package com.histmersion.web

import com.histmersion.api.{Paging, Plural, Wording}
import com.histmersion.api.automation.Agents.{ApprovalInfo, ApprovalKind, ApprovalState, Decision}
import com.raquo.laminar.api.L.*
import com.histmersion.api.automation.ListFilters

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.automation.ManagersMarkdown
import com.histmersion.api.automation.Goal

/** The approvals window (docs/research-embedded-agents.md §5.2): changes agent runs PROPOSED — a source's status, a
  * disagreement edge — each with its evidence and the quote code verified, and approve / reject behind the write gate.
  * Approving applies the change through the ordinary source patch. Reloaded when the window opens, after a decision,
  * and when a run it watches settles ([[changed]]); there is nothing to poll for otherwise. A page at a time
  * ([[ListPaging]]): a reload after a decision or a settled run keeps the pages loaded; the toggle and "refresh" start
  * over.
  */
object ApprovalsView:
  type ApprovalId = ApprovalId.T
  object ApprovalId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindFilter = KindFilter.T
  object KindFilter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Picked = Picked.T
  object Picked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** How many a page lists. */

  type States = StatesValue
  type StatesValue = StatesValue.T
  object StatesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Limit = 100

  /** A run settled (it may have opened approvals): an open window reloads. */
  val changed: EventBus[Unit] = EventBus()

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** What approving each kind does, and where it sits in a goal's life (goal-plan → article-plan → compile-article's
    * article-publish → review); a tooltip on the kind wherever it is shown.
    */
  def kindMeaning(kind: com.histmersion.api.automation.Approval.Kind.Wire): Option[String] = ApprovalKind.parse(kind).map {
    case ApprovalKind.GoalPlan =>
      "A research goal's plan: its sub-goals, search queries and works to read. " +
        "Approving creates them; that unblocks proposing the goal's articles."
    case ApprovalKind.ArticlePlan =>
      "Which new articles a goal needs (propose-articles). " +
        "Approving unblocks its seeding manager to write drafts of them."
    case ApprovalKind.ArticlePublish =>
      "Makes the draft an agent wrote (compile-article) readable as an article, " +
        "not yet reviewed (or appends its section). Approving unblocks its review."
    case ApprovalKind.ArticleFilter =>
      "An article's coverage filter: which sources it should account for. " +
        "Approving records it on the article; its review waits for this decision."
    case ApprovalKind.StatusChange => "A source's status (current, contested, superseded…). Approving changes it."
    case ApprovalKind.Edge         =>
      "A typed link between two sources (challenges, refutes, superseded by…). Approving adds it."
    case ApprovalKind.HintEffect =>
      "An action for a manager (hold, prioritise, skip, a setting) a model proposed " +
        "from your hint. Applied only if you approve it."
  }

  /** What each state the filter buttons offer means; a tooltip on the button. */
  def stateMeaning(state: com.histmersion.api.automation.Approval.State.Wire): Option[String] =
    if state == "all" then Some("Every approval, whatever its state.")
    else
      ApprovalState.parse(state).map {
        case ApprovalState.Open     => "Waiting for a decision: approve it to apply the change, or reject it."
        case ApprovalState.Approved => "Approved, and the change applied."
        case ApprovalState.Rejected => "Rejected: kept on record, and nothing changed."
        case ApprovalState.Failed   =>
          "Approved, but applying it failed (its subject had changed or was gone); the reason is on its row."
      }

  /** Who decided an approval, in a sentence; a tooltip on the "decided by" chips. */
  def decidedByMeaning(by: com.histmersion.api.automation.Activity.Actor): Option[String] = ListFilters.DecidedBy.parse(by).map {
    case ListFilters.DecidedBy.Person =>
      "Decided by a person: every decision that is not the autopilot's or an automatic one."
    case ListFilters.DecidedBy.Autopilot =>
      "Decided by the autopilot's code checks, within the day's cap, with no person involved."
    case ListFilters.DecidedBy.Auto =>
      "Applied the moment it was proposed: its kind is set to be approved automatically."
  }

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
          case Some(d) if d.mode == Goal.Plans.DraftMode.Create =>
            s"publish the new article ${a.subject.getOrElse("?")}: ${d.title}"
          case _ => s"append a section to the article ${a.subject.getOrElse("?")}"
      case Some(ApprovalKind.ArticleFilter) =>
        s"record the coverage filter of ${a.subject.getOrElse("?")}: ${a.filter.fold("?")(_.filter.line)}"
      case Some(ApprovalKind.ArticlePlan) =>
        s"compile ${Plural.count(a.articles.fold(0)(_.articles.size), "new article")} for goal ${a.subject.getOrElse("?")}"
      case Some(ApprovalKind.HintEffect) =>
        s"manager ${a.subject.getOrElse("?")}: ${a.hintEffect.fold("an action")(p => ManagersMarkdown.effect(p.effect))}"
      case None => s"${a.kind} on ${a.subject.getOrElse(a.citekey)}"

  def isOpen(a: ApprovalInfo): Boolean = a.state == ApprovalState.Open.wire

  /** The evidence line under the change — unless the plan block shows it: a plan's evidence IS its rationale, which
    * [[planBlock]] prints at its top (#882: it used to appear twice).
    */
  def evidenceLine(a: ApprovalInfo): Option[String] =
    Option(a.evidence).filter(_.nonEmpty).filterNot(e => a.plan.exists(_.rationale == e))

  /** The states the filter buttons offer, in order; `all` = every state. */
  val States: List[com.histmersion.web.ApprovalsView.States] = ApprovalState.values.toList.map(_.wire) :+ "all"

  /** The state a button stands for as the URL keeps it: the open ones are the default (`None`). */
  def stateValue(button: com.histmersion.api.automation.Approval.State.Wire): Option[String] = Option.when(button != ApprovalState.Open.wire)(button)

  /** The state the list shows: the open ones unless another was picked. */
  def shownState(field: Option[com.histmersion.api.automation.Approval.State.Wire]): String = field.getOrElse(ApprovalState.Open.wire)

  /** The query the window sends, from its filter fields. Pure. */
  def filterOf(
      state: Option[com.histmersion.api.automation.Approval.State.Wire],
      kinds: List[com.histmersion.web.ApprovalsView.KindFilter],
      by: List[com.histmersion.api.automation.Activity.Actor],
      subject: Option[com.histmersion.api.automation.ListFilters.ApprovalFilter.Subject]
  ): ListFilters.ApprovalFilter =
    ListFilters.ApprovalFilter(state = state, kind = kinds, subject = subject, decidedBy = by)

  /** The filter in the window's fields now. */
  def currentFilter(): ListFilters.ApprovalFilter =
    filterOf(
      UiState.approvalState.now(),
      UiState.approvalKinds.now(),
      UiState.approvalBy.now(),
      UiState.approvalSubject.now()
    )

  /** The rows a filter shows, from the rows already loaded — what the list shows the moment a state or a kind is
    * picked, before the server's page replaces it. (The text and the decider are left to the server.)
    */
  def visible(as: List[ApprovalInfo], f: ListFilters.ApprovalFilter): List[ApprovalInfo] =
    val n = f.normalized
    val state = shownState(n.state)
    as.filter(a => (state == "all" || a.state == state) && (n.kind.isEmpty || n.kind.contains(a.kind)))

  /** The heading's words before the noun: `open` (or the state picked) and the kinds picked. */
  def qualifier(f: ListFilters.ApprovalFilter): Option[com.histmersion.api.automation.ListFilters.Qualifier] = f.qualifier

  /** The approvals a bulk decision would apply to: those selected that are still shown and open. */
  def bulkTargets(selected: Set[com.histmersion.web.ApprovalsView.ApprovalId], shown: List[ApprovalInfo]): List[ApprovalInfo] =
    shown.filter(a => selected(a.id) && isOpen(a))

  /** What the confirmation of a bulk decision asks. */
  def confirmText(d: Decision, as: List[ApprovalInfo]): String =
    val kinds = as
      .groupMapReduce(_.kind)(_ => 1)(_ + _)
      .toList
      .sortBy(-_._2)
      .map((k, n) => s"$n ${kindLabel(k).toLowerCase}")
      .mkString(", ")
    s"${if d == Decision.Approve then "Approve" else "Reject"} ${Plural.count(as.size, "approval")} ($kinds)? " +
      "Each is decided as if its own button were pressed, one after another, with the same checks."

  /** Who decided an approval, as its row says it (nothing while it is open). */
  def decidedLine(a: ApprovalInfo): Option[String] = ListFilters.DecidedBy.of(a.state, a.note, a.result).map(_.describe)

  /** The self-check line of a draft. */
  def selfCheck(d: Goal.Plans.ArticleDraft): String =
    if d.problems.isEmpty then "The code self-check found nothing."
    else s"The code self-check still found ${Plural.count(d.problems.size, "problem")} after the writer's last attempt:"

  /** A plan, as a person weighs it: sub-goals, queries, works (in the corpus or to acquire), dropped. */
  private[web] def planBlock(plan: Goal.Plans.Plan): HtmlElement =
    div(
      cls := "approval-plan",
      Option(plan.rationale).filter(_.nonEmpty).map(r => p(cls := "approval-evidence", r)),
      Option.when(plan.subGoals.nonEmpty)(
        div(
          h5("Sub-goals"),
          ul(
            plan.subGoals.map(g => li(code(g.slug), s" — ${g.title}", g.notes.map(n => span(cls := "muted", s": $n"))))
          )
        )
      ),
      Option.when(plan.queries.nonEmpty)(div(h5("Search queries"), ul(plan.queries.map(q => li(q))))),
      Option.when(plan.works.nonEmpty)(
        div(
          h5("Works"),
          ul(plan.works.map { w =>
            li(
              w.title.getOrElse(w.doi),
              s" (${w.year.fold("n.d.")(_.toString)}) ",
              a(href := s"https://doi.org/${w.doi}", target := "_blank", rel := "noopener", w.doi),
              if w.inCorpus then span(" — in the corpus as ", w.citekey.fold(span("?"))(ck => SourceLink(ck)))
              else span(cls := "muted", if w.isOa then " — to acquire (open access)" else " — to acquire"),
              Option(w.why).filter(_.nonEmpty).map(y => div(cls := "muted", y))
            )
          })
        )
      ),
      Option.when(plan.dropped.nonEmpty)(
        htmlTag("details")(
          htmlTag("summary")(s"dropped by code (${plan.dropped.size})"),
          ul(plan.dropped.map(d => li(cls := "muted", d)))
        )
      )
    )

  /** A draft: its self-check, what it cites and leaves out, and the text itself. */
  private[web] def draftBlock(d: Goal.Plans.ArticleDraft): HtmlElement =
    div(
      cls := "approval-draft",
      p(
        cls := "muted",
        s"${d.kind} · \"${d.title}\"" + d.section.fold("")(s => s" · under $s") +
          s" · cites ${Plural.count(d.cited.size, "source")} · ${d.filter}"
      ),
      p(cls := (if d.problems.isEmpty then "form-ok" else "run-paused"), selfCheck(d)),
      Option.when(d.problems.nonEmpty)(ul(d.problems.map(x => li(x)))),
      Option.when(d.notUsed.nonEmpty)(
        htmlTag("details")(
          htmlTag("summary")(s"not used (${d.notUsed.size})"),
          ul(d.notUsed.map(n => li(cls := "muted", n)))
        )
      ),
      htmlTag("details")(htmlTag("summary")("the text"), pre(cls := "trace-body draft-text", d.text))
    )

  // ── state and view ──────────────────────────────────────────────────────────────────────────────

  /** The window's filter fields (they live in the URL hash). */
  private val filterFields: List[Field[?]] =
    List(UiState.approvalState, UiState.approvalKinds, UiState.approvalBy, UiState.approvalSubject)

  /** The filter, as a signal: a change restarts the list from the newest page. */
  private val filterSignal: Signal[ListFilters.ApprovalFilter] =
    UiState.approvalState.signal
      .combineWith(UiState.approvalKinds.signal, UiState.approvalBy.signal, UiState.approvalSubject.signal)
      .map(filterOf)
      .distinct

  /** The rows and "Show older" ([[PolledList]]): nothing is polled — a decision, a settled run ([[changed]]) or a
    * filter reloads — but the window gets the same restart when the last row shown is gone, and drops an answer that
    * lands after it closed.
    */
  private val list = PolledList[ApprovalInfo](
    fetch = (upTo, before) => ApiClient.approvals(currentFilter(), Some(Limit), upTo = upTo, before = before),
    id = key,
    live = (_, _) => false
  )
  private val busy = Var(Set.empty[Long])

  /** The open approvals ticked for a bulk decision. */
  private val selected = Var(Set.empty[Long])
  private val bulkBusy = Var(false)

  private def key(a: ApprovalInfo): String = a.id.toString

  /** Reload what is shown, keeping the pages loaded ([[ListPaging]]). */
  private def refresh(): Unit = list.refresh()

  private def decide(a: ApprovalInfo, d: Decision, note: com.histmersion.web.ApiClient.Note): Unit =
    busy.update(_ + a.id)
    ApiClient.decide(a.id, d, Some(note).map(_.trim).filter(_.nonEmpty)).onComplete { r =>
      busy.update(_ - a.id)
      r.failed.foreach(e => list.failure.set(Some(e.getMessage)))
      refresh()
      // decided here or in a goal's window: the count drops, and the decision is not news to its maker
      Notices.ownWrite(com.histmersion.api.instance.UiNotices.Board.Approvals)
    }

  /** A bulk decision, after the person confirmed it: one request per approval, ONE AFTER ANOTHER, each through the very
    * endpoint (and checks) of its own button — an approval decided meanwhile is refused (409) and the rest go on; the
    * refusals are reported together.
    */
  private def decideAll(as: List[ApprovalInfo], d: Decision): Unit =
    bulkBusy.set(true)
    busy.update(_ ++ as.map(_.id))
    val done = as.foldLeft(scala.concurrent.Future.successful(List.empty[String])) { (acc, a) =>
      acc.flatMap(errs =>
        ApiClient
          .decide(a.id, d, None)
          .transform(r => scala.util.Success(r.failed.toOption.fold(errs)(e => errs :+ s"#${a.id}: ${e.getMessage}")))
      )
    }
    done.foreach { errs =>
      bulkBusy.set(false)
      busy.update(_ -- as.map(_.id))
      selected.set(Set.empty)
      list.failure.set(
        Option.when(errs.nonEmpty)(s"${Plural.count(errs.size, "approval")} not decided: ${errs.mkString("; ")}")
      )
      refresh()
      Notices.ownWrite(com.histmersion.api.instance.UiNotices.Board.Approvals)
    }

  /** The listing with the rows the filter shows — at once, from the rows loaded; the server's page replaces them a
    * moment later.
    */
  private def shownSignal: Signal[(ListPaging.Listing[ApprovalInfo], ListFilters.ApprovalFilter)] =
    list.listing.signal.combineWith(filterSignal).map((l, f) => (l.copy(rows = visible(l.rows, f)), f))

  /** An approval's state (or "all") as its button and badge read it. */
  def stateLabel(s: String): String =
    if s == "all" then "All" else ApprovalState.parse(s).fold(Wording.humanize(s))(Wording.approvalState)

  /** An approval's kind as a person reads it ([[Wording.approvalKind]]). */
  def kindLabel(kind: com.histmersion.api.automation.Approval.Kind.Wire): String = ApprovalKind.parse(kind).fold(Wording.humanize(kind))(Wording.approvalKind)

  private def stateButtons: HtmlElement =
    def count(l: ListPaging.Listing[ApprovalInfo], s: String) =
      ListPaging.count(l, Option.when(s != "all")(s), _.state)
    div(
      cls := "btn-group jobs-filter approvals-state",
      States.map { s =>
        button(
          typ := "button",
          cls := "btn btn--toggle",
          dataAttr("state") := s,
          stateMeaning(s).map(m => title := m),
          aria.pressed <-- UiState.approvalState.signal.map(v => (shownState(v) == s).toString),
          span(stateLabel(s)),
          child.text <-- list.listing.signal.map { l =>
            val n = count(l, s); if n > 0 then s" ${Paging.grouped(n)}" else ""
          },
          onClick --> (_ => UiState.set(UiState.approvalState := stateValue(s))())
        )
      }
    )

  private def bulkBar: HtmlElement =
    val targets = selected.signal.combineWith(shownSignal).map((sel, l, _) => bulkTargets(sel, l.rows))
    val openShown = shownSignal.map(_._1.rows.filter(isOpen))
    div(
      cls := "btn-group approvals-bulk",
      cls("is-hidden") <-- openShown.combineWith(Auth.writable).map((o, w) => o.isEmpty || !w),
      button(
        typ := "button",
        cls := "btn btn--sm",
        disabled <-- bulkBusy.signal,
        child.text <-- openShown.map(o => s"select all ${o.size} shown"),
        onClick(_.sample(openShown)) --> (o => selected.set(o.map(_.id).toSet))
      ),
      button(
        typ := "button",
        cls := "btn btn--sm btn--ghost",
        disabled <-- targets.map(_.isEmpty),
        "unselect",
        onClick --> (_ => selected.set(Set.empty))
      ),
      List(Decision.Approve, Decision.Reject).map { d =>
        button(
          typ := "button",
          cls := (if d == Decision.Approve then "btn btn--sm btn--primary" else "btn btn--sm"),
          dataAttr("bulk") := d.wire,
          disabled <-- targets.combineWith(bulkBusy.signal).map((t, b) => t.isEmpty || b),
          child.text <-- targets.map(t => s"${Wording.capitalize(d.wire)} selected (${t.size})"),
          onClick(_.sample(targets)) --> { t =>
            if t.nonEmpty && org.scalajs.dom.window.confirm(confirmText(d, t)) then decideAll(t, d)
          }
        )
      }
    )

  /** One kind of the digest, in a line: how many, the oldest, who decides them now. */
  def digestLine(g: com.histmersion.api.automation.ApprovalDigest.KindGroup): String =
    s"${kindLabel(g.kind)} ${Paging.grouped(g.count)}" + g.oldest.fold("")(o => s" (oldest $o)") +
      (g.decidedBy match
        case "checked" => " · the autopilot's checks decide them within the day's cap"
        case com.histmersion.api.automation.ApprovalDigest.Waits => " · waiting: nobody needs to decide them now"
        case _                                                   => " · a person decides")

  /** The digest (#3762 proposal 3): everything waiting for a person, by kind — a click shows one kind in the list below
    * (its kind filter), where the bulk bar decides them all at once.
    */
  private def digestBar: HtmlElement =
    val d = Var(Option.empty[com.histmersion.api.automation.ApprovalDigest.Digest])
    def load(): Unit = ApiClient.approvalDigest.onComplete(_.toOption.foreach(x => d.set(Some(x))))
    div(
      cls := "approvals-digest",
      onMountCallback(_ => load()),
      changed.events --> (_ => load()),
      child.maybe <-- d.signal.map(_.filter(x => x.total > 0 || x.heldForPerson > 0).map { x =>
        div(
          p(
            cls := "muted",
            s"Waiting for a person: ${Plural.count(x.total, "approval")}" +
              (if x.heldForPerson > 0 then s", ${Plural.count(x.heldForPerson, "manager item")} held for a person"
               else "") +
              " — pick a kind, then decide the shown ones at once."
          ),
          x.goalPlansWait.map(w => p(cls := "muted", s"${w.capitalize}; a person may still approve one.")),
          div(
            cls := "btn-group",
            x.kinds.map(g =>
              button(
                typ := "button",
                cls := "btn btn--sm",
                digestLine(g),
                title := (kindMeaning(g.kind).toList :+ g.subjects
                  .map(s => s"${s.subject} (${s.count})")
                  .mkString(", ")).mkString("\n"),
                onClick --> (_ => UiState.set(UiState.approvalKinds := List(g.kind), UiState.approvalState := None)())
              )
            )
          )
        )
      })
    )

  def pane: HtmlElement =
    div(
      cls := "tool-pane approvals-pane",
      digestBar,
      list.lifecycle,
      changed.events --> (_ => refresh()),
      filterSignal.changes --> (_ => { selected.set(Set.empty); list.restart() }),
      // a tick outlives its row: once the row is gone or decided it is not a bulk target
      list.listing.signal.map(_.rows.filter(isOpen).map(_.id).toSet) --> (ids => selected.update(_.intersect(ids))),
      div(
        cls := "pane-bar",
        stateButtons,
        button(typ := "button", cls := "btn", "Refresh", onClick --> (_ => list.restart()))
      ),
      div(
        cls := "toolbar list-filters",
        ListFilterBar.chips(
          "kind",
          list.listing.signal.map(_.page.facet("kind")),
          UiState.approvalKinds,
          hint = kindMeaning,
          about = "Show only approvals of the kinds picked (any of them): what the change is."
        ),
        ListFilterBar.chips(
          "decided by",
          list.listing.signal.map(_.page.facet("decidedBy")),
          UiState.approvalBy,
          hint = decidedByMeaning,
          about = "Show only approvals decided the ways picked (any of them)."
        ),
        div(
          cls := "btn-group",
          ListFilterBar.text("subject: a goal, article, manager or citekey", UiState.approvalSubject),
          ListFilterBar.clearButton(filterFields)
        )
      ),
      p(
        cls := "muted",
        "Changes agent runs proposed. Approving applies the change; " +
          "rejecting keeps it on record and changes nothing."
      ),
      ErrorLine(list.failure.signal),
      bulkBar,
      child.maybe <-- shownSignal.combineWith(list.loaded.signal).map { (l, f, isLoaded) =>
        val st = shownState(f.normalized.state)
        Option.when(isLoaded && l.rows.nonEmpty)(
          p(
            cls := "muted list-heading",
            ListPaging.heading(
              l,
              "approval",
              Option.when(st != "all")(st),
              _.state,
              Option.when(st != "all")(stateLabel(st)).toList ++ f.normalized.kind.map(kindLabel)
            )
          )
        )
      },
      child.maybe <-- shownSignal.combineWith(list.loaded.signal).map { (l, f, isLoaded) =>
        if !isLoaded then Some(p(cls := "muted", "Loading…"))
        else
          Option.when(l.rows.isEmpty)(
            p(
              cls := "muted",
              if f.normalized != ListFilters.ApprovalFilter() then "No approvals match the filter."
              else "Nothing waits for a decision."
            )
          )
      },
      // keyed by the row itself: an unchanged row keeps its element (and the note being typed in it)
      ul(
        cls := "job-list approval-list",
        aria.busy <-- list.loading,
        children <-- shownSignal
          .map(_._1.rows)
          .split(identity)((a, _, _) =>
            row(
              a,
              Option.when(isOpen(a))(
                Pick(selected.signal.map(_(a.id)), on => selected.update(s => if on then s + a.id else s - a.id))
              )
            )
          )
      ),
      list.olderButton
    )

  /** A row's tick box for a bulk decision: whether it is ticked, and what a click does. */
  final private[web] case class Pick(on: Signal[Pick.On], set: Pick.SetArg1 => Unit)

  object Pick:
    type SetArg1 = com.histmersion.web.ApprovalsView.Picked
    val SetArg1: _root_.com.histmersion.web.ApprovalsView.Picked.type = _root_.com.histmersion.web.ApprovalsView.Picked

    type On = On.T
    object On extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One approval: what it would change, the evidence, and approve / reject. Shared with the goal window, which shows
    * the open plan of its goal.
    */
  private[web] def row(a: ApprovalInfo, pick: Option[Pick] = None): HtmlElement =
    // the note being typed is this row's own until a decision sends it
    val note = Var("")
    li(
      cls := s"job-row approval-row approval-${a.state}",
      dataAttr("kind") := a.kind,
      pick.map(pk =>
        child.maybe <-- Auth.writable.map(w =>
          Option.when(w)(
            input(
              typ := "checkbox",
              cls := "approval-pick",
              aria.label := s"select approval ${a.id}",
              checked <-- pk.on,
              onClick.mapToChecked --> (c => pk.set(c))
            )
          )
        )
      ),
      span(cls := s"job-state state-${a.state}", title := a.state, stateLabel(a.state)),
      div(
        cls := "job-main",
        div(
          cls := "job-subject approval-change",
          ApprovalKind.parse(a.kind) match
            case Some(ApprovalKind.Edge) =>
              span(
                SourceLink(a.citekey),
                s" ${a.edgeKind.map(_.replace('_', ' ')).getOrElse("?")} ",
                a.target.fold(span("?"))(t => SourceLink(t))
              )
            case Some(ApprovalKind.GoalPlan) =>
              span(
                "Plan for the goal ",
                a.subject.fold(span("?"))(g => WinLink(WindowKey.Goal(g), title := g, Wording.humanize(g)))
              )
            case Some(ApprovalKind.ArticlePublish) =>
              span(
                if a.article.exists(_.mode == Goal.Plans.DraftMode.Create) then "publish the new article "
                else "append a section to ",
                a.subject.fold(span("?"))(s =>
                  if a.article.exists(_.mode == Goal.Plans.DraftMode.Create) then strong(s)
                  else com.histmersion.web.ArticleLink(s, s)
                )
              )
            case Some(ApprovalKind.HintEffect) =>
              span(
                a.subject.fold(span("A manager"))(id =>
                  WinLink(WindowKey.Manager(id), title := id, Wording.manager(id))
                ),
                ": ",
                strong(a.hintEffect.fold("an action")(p => ManagersMarkdown.effect(p.effect))),
                a.hintEffect.map(p =>
                  span(
                    cls := "muted",
                    s" — proposed by ${Option(p.by).filter(_.nonEmpty).getOrElse("a model")} " +
                      s"from hint #${p.hint}: “${p.text.take(200)}”"
                  )
                )
              )
            case Some(ApprovalKind.ArticleFilter) | Some(ApprovalKind.ArticlePlan) => span(describe(a))
            case _                                                                 =>
              span(
                "status of ",
                SourceLink(a.citekey),
                s": ${a.current.getOrElse("?")} → ",
                strong(a.status.getOrElse("?"))
              )
        ),
        evidenceLine(a).map(e => p(cls := "approval-evidence", e)),
        a.plan.map(planBlock),
        a.article.map(draftBlock),
        a.quote.filter(_.nonEmpty).map(q => blockQuote(cls := "approval-quote", q)),
        a.note.filter(_.nonEmpty).map(n => p(cls := "muted", s"note: $n")),
        a.result.filter(_.nonEmpty).map(r => p(cls := "muted", r)),
        div(
          cls := "job-meta muted",
          span(s"#${a.id}"),
          a.runId.map(id => span(" · ", WinLink(WindowKey.Run(id), s"run ${id.take(8)}"))),
          span(
            " · ",
            span(
              cls := "approval-kind",
              title := kindMeaning(a.kind).fold(a.kind)(m => s"$m (${a.kind})"),
              kindLabel(a.kind)
            )
          ),
          span(s" · proposed ${a.createdAt}"),
          decidedLine(a).map(d => span(cls := "approval-decided", s" · $d"))
        ),
        Option.when(isOpen(a))(
          div(
            cls := "approval-actions",
            child <-- Auth.writable.map { w =>
              if !w then Auth.keyNeeded("decide")
              else
                div(
                  cls := "form-row",
                  input(
                    typ := "text",
                    cls := "field",
                    aria.label := "note",
                    placeholder := "note (optional)",
                    controlled(value <-- note.signal, onInput.mapToValue --> note.writer)
                  ),
                  button(
                    typ := "button",
                    cls := "btn btn--primary",
                    "Approve",
                    disabled <-- busy.signal.map(_(a.id)),
                    onClick --> (_ => decide(a, Decision.Approve, note.now()))
                  ),
                  button(
                    typ := "button",
                    cls := "btn",
                    "Reject",
                    disabled <-- busy.signal.map(_(a.id)),
                    onClick --> (_ => decide(a, Decision.Reject, note.now()))
                  )
                )
            }
          )
        )
      )
    )
