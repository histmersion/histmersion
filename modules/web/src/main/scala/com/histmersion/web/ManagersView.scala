package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.core.Managers.*
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The process managers (docs/research-process-managers.md §5): the managers window (each one's
  * state — with what the supervisor derived: starved, at capacity, idle — its items, runs in flight
  * and last event) and one window per manager: pause (with or without its runs) / resume / stop, the
  * items in flight with their runs' progress, the items with hold / release / priority / skip /
  * re-open, the hint box, the live hints, the events and the settings. A manager window polls while
  * it has runs in flight (the runs window's schedule); every change is behind the write gate. Also
  * the article window's Review button. */
object ManagersView:

  /** Items per page, events per page. */
  val ItemLimit  = 25
  val EventLimit = 30

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** The badge of a manager: its person-set state, or — active — what the supervisor derived. */
  def badge(m: ManagerInfo): String =
    val note = m.statusNote.getOrElse("")
    if m.state != State.Active.wire then m.state
    else if note.startsWith("starved") then "starved"
    else if note.startsWith("budget") then "budget"
    else if note.startsWith("at capacity") then "at capacity"
    else if note == "idle" then "idle"
    else "active"

  /** What a person can do to an item in this status. */
  def itemActions(status: String): List[String] = ItemStatus.parse(status) match
    case Some(ItemStatus.Pending) => List("hold", "priority", "skip")
    case Some(ItemStatus.Held)    => List("release", "priority", "skip")
    case Some(ItemStatus.Active)  => List("priority", "skip")
    case Some(s) if s.isFinal     => List("reopen")
    case _                        => Nil

  /** A whole number the person typed, or nothing. */
  def int(s: String): Option[Int] = s.trim.toIntOption.filter(_ >= 0)

  private def list(s: String, sep: String): Option[List[String]] =
    Option(s.split(sep).toList.map(_.trim).filter(_.nonEmpty)).filter(_.nonEmpty)

  /** The settings form → a config (blank fields are the server's defaults). Left = what does not parse. */
  def config(f: Map[String, String]): Either[String, ManagerConfig] =
    def g(k: String) = f.getOrElse(k, "").trim
    val bad = List("maxRuns", "maxOpen", "rounds", "maxRunsPerArticle", "noProgressRuns", "reopenLimit", "cooldownMinutes",
      "lowWater", "refillBatch", "maxFailures").filter(k => g(k).nonEmpty && int(g(k)).isEmpty)
    val usd = g("budgetUsd")
    val models = list(g("models"), ";").getOrElse(Nil).map(Agents.ModelOverride.parse)
    if bad.nonEmpty then Left(s"not a whole number: ${bad.mkString(", ")}")
    else if usd.nonEmpty && usd.toDoubleOption.forall(_ <= 0) then Left("budget USD is not a positive number")
    else models.collectFirst { case Left(e) => e } match
      case Some(e) => Left(s"models: $e")
      case None =>
        Right(ManagerConfig(int(g("maxRuns")), int(g("maxOpen")), int(g("rounds")), int(g("maxRunsPerArticle")),
          int(g("noProgressRuns")), int(g("reopenLimit")), int(g("cooldownMinutes")), int(g("lowWater")), int(g("refillBatch")),
          int(g("maxFailures")), usd.toDoubleOption, list(g("includeTags"), ","), Option(g("includeGoal")).filter(_.nonEmpty),
          Option(models.collect { case Right(m) => m }).filter(_.nonEmpty)))

  /** A config as the form shows it (blank = the default). */
  def fields(c: ManagerConfig): Map[String, String] = Map(
    "maxRuns" -> c.maxRuns, "maxOpen" -> c.maxOpen, "rounds" -> c.rounds, "maxRunsPerArticle" -> c.maxRunsPerArticle,
    "noProgressRuns" -> c.noProgressRuns, "reopenLimit" -> c.reopenLimit, "cooldownMinutes" -> c.cooldownMinutes,
    "lowWater" -> c.lowWater, "refillBatch" -> c.refillBatch, "maxFailures" -> c.maxFailures
  ).view.mapValues(_.fold("")(_.toString)).toMap ++ Map(
    "budgetUsd" -> c.budgetUsd.fold("")(_.toString), "includeTags" -> c.includeTags.getOrElse(Nil).mkString(", "),
    "includeGoal" -> c.includeGoal.getOrElse(""), "models" -> c.models.getOrElse(Nil).map(_.text).mkString("; "))

  /** The settings the form offers, with their labels. */
  val Settings: List[(String, String)] = List(
    "maxRuns" -> "runs at once", "maxOpen" -> "open runs (incl. paused)", "rounds" -> "rounds per run",
    "maxRunsPerArticle" -> "runs per article", "noProgressRuns" -> "no-progress window (runs)",
    "reopenLimit" -> "reopen limit", "cooldownMinutes" -> "cooldown (minutes)", "lowWater" -> "refill below",
    "refillBatch" -> "refill batch", "maxFailures" -> "failures before stop", "budgetUsd" -> "budget per run (USD)",
    "includeTags" -> "only articles tagged (comma-separated)", "includeGoal" -> "only a goal's articles",
    "models" -> "models (role=provider:model; …)")

  /** The hint box → a hint. `action` none/prioritise/hold/skip; `targets` comma-separated slugs. */
  def hint(text: String, item: String, once: Boolean, action: String, targets: String, tag: String, until: String,
      untilSource: String): Either[String, HintAdd] =
    val t = text.trim
    val a = action.trim.toLowerCase
    if t.isEmpty then Left("a hint needs text")
    else if a.isEmpty || a == "none" then Right(HintAdd(t, Option(item.trim).filter(_.nonEmpty), Some(once)))
    else if !HintEffect.Actions.contains(a) then Left(s"unknown action $a")
    else
      val e = HintEffect(a, list(targets, ",").getOrElse(Nil), Option(tag.trim).filter(_.nonEmpty), None, None,
        Option(until.trim).filter(_.nonEmpty), Option(untilSource.trim).filter(_.nonEmpty))
      if a == HintEffect.HoldAction && e.until.isEmpty && e.untilSource.isEmpty then Left("hold: give a date or a source")
      else if e.targets.isEmpty && e.tag.isEmpty && item.trim.isEmpty then Left(s"$a: name the articles or a tag")
      else Right(HintAdd(t, Option(item.trim).filter(_.nonEmpty), Some(once), Some(e)))

  // ── the article window's button ─────────────────────────────────────────────────────────────────

  /** Review: the article goes to the review manager, first in line (only where the server runs agents). */
  def articleActions(slug: String): HtmlElement =
    val note = Var(Option.empty[String])
    div(
      cls := "wm-actions",
      child.maybe <-- RunsView.enabled.signal.map(on => Option.when(on)(
        WriteButton(Icon.Managers, "review", "queue this article for the review manager (first in line)", () =>
          ApiClient.enqueueReview(slug).onComplete(r => note.set(Some(r.fold(e => s"not queued: ${e.getMessage}", i =>
            s"queued for review (item #${i.id}, ${i.status})"))))))),
      child.maybe <-- note.signal.map(_.map(n => span(cls := "muted wm-note", n,
        button(typ := "button", cls := "link-btn", " open the manager", onClick --> (_ => WindowManager.open(WindowKey.Manager(ReviewId))))))))

  // ── the managers window ─────────────────────────────────────────────────────────────────────────

  private def stateBadge(m: ManagerInfo): HtmlElement =
    val b = badge(m)
    span(cls := s"job-state state-${b.replace(' ', '-')}", title := m.statusNote.getOrElse(m.state), b)

  def pane: HtmlElement =
    val ms      = Var(List.empty[ManagerInfo])
    val loaded  = Var(false)
    val failure = Var(Option.empty[String])
    def refresh(): Unit =
      ApiClient.managers.onComplete(_.fold(e => failure.set(Some(e.getMessage)), xs => { failure.set(None); ms.set(xs); loaded.set(true) }))
    div(
      cls := "tool-pane managers-pane",
      onMountCallback(_ => refresh()),
      div(cls := "pane-bar", button(typ := "button", cls := "btn", "refresh", onClick --> (_ => refresh()))),
      child.maybe <-- RunsView.enabled.signal.map(on => Option.when(!on)(p(cls := "muted",
        "Embedded agents are off on this server (agents.enabled): no manager starts a run."))),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child <-- ms.signal.combineWith(loaded.signal).map { (xs, isLoaded) =>
        if !isLoaded then p(cls := "muted", "Loading…")
        else if xs.isEmpty then p(cls := "muted", "No managers yet. The review manager is created (paused) when the server's agents start.")
        else ul(cls := "job-list manager-list", xs.map(m =>
          li(cls := "job-row manager-row",
            stateBadge(m),
            div(cls := "job-main",
              div(cls := "job-subject",
                button(typ := "button", cls := "link-btn", m.id, onClick --> (_ => WindowManager.open(WindowKey.Manager(m.id)))),
                span(cls := "muted", s" · ${m.kind}${m.goal.fold("")(g => s" · goal $g")}")),
              m.statusNote.filter(_.nonEmpty).map(n => div(cls := "job-progress", n)),
              div(cls := "job-meta muted", counts(m.counts), s" · ${Plural.count(m.inFlight, "run")} in flight"),
              m.lastEvent.map(e => div(cls := "muted", s"${e.at} · ${e.kind}: ${e.text.take(160)}"))))))
      }
    )

  private def counts(c: ItemCounts): String =
    List("pending" -> c.pending, "held" -> c.held, "active" -> c.active, "done" -> c.done, "stopped" -> c.stopped,
      "failed" -> c.failed).filter(_._2 > 0).map((k, n) => s"$n $k").mkString(", ") match
      case "" => "no items"
      case s  => s

  // ── one manager ─────────────────────────────────────────────────────────────────────────────────

  def managerPane(id: String): HtmlElement =
    val detail  = Var(Option.empty[ManagerDetail])
    val page    = Var(Option.empty[ItemPage])
    val offset  = Var(0)
    val status  = Var(Option.empty[String])
    val events  = Var(List.empty[ManagerEvent])
    val older   = Var(Option.empty[Long])
    val loadingOlder = Var(false)
    val failure = Var(Option.empty[String])
    val message = Var(Option.empty[String])
    val busy    = Var(false)
    // `var`s local to this window: its pending poll, the delay, whether it is mounted, whether older
    // events were loaded (a poll then keeps them)
    var timer: Int         = 0
    var delay: Option[Int] = None
    var mounted: Boolean   = false
    var extended: Boolean  = false

    def items(): Unit =
      ApiClient.managerItems(id, status.now(), offset.now(), ItemLimit).foreach(p => page.set(Some(p)))

    def refresh(changed: Boolean): Unit =
      dom.window.clearTimeout(timer)
      ApiClient.manager(id).onComplete { r =>
        if mounted then
          r.fold(e => failure.set(Some(e.getMessage)), d => {
            val moved = changed || !detail.now().contains(d)
            failure.set(None)
            detail.set(Some(d))
            if extended then events.update(ListPaging.keepOlder(_, d.events, _.id < _.id))
            else
              events.set(d.events)
              older.set(Option.when(d.events.nonEmpty)(d.events.last.id))
            delay = Poll.next(delay, d.manager.inFlight > 0 || d.current.nonEmpty, moved)
            delay.foreach(ms => timer = dom.window.setTimeout(() => refresh(false), ms))
          })
      }
      items()

    def write[A](what: String)(f: => scala.concurrent.Future[A]): Unit =
      busy.set(true)
      f.onComplete { r =>
        busy.set(false)
        r.fold(e => failure.set(Some(s"$what: ${e.getMessage}")), _ => { failure.set(None); message.set(Some(s"$what: done")) })
        refresh(changed = true)
      }

    def showOlder(): Unit =
      older.now().foreach { b =>
        loadingOlder.set(true)
        ApiClient.managerEvents(id, Some(b), EventLimit).onComplete { r =>
          loadingOlder.set(false)
          r.foreach { p =>
            val shown = events.now().map(_.id).toSet
            events.update(_ ++ p.events.filterNot(e => shown(e.id)))
            older.set(p.before)
            extended = true
          }
        }
      }

    div(
      cls := "tool-pane manager-pane",
      onMountCallback { _ => mounted = true; extended = false; refresh(changed = true) },
      onUnmountCallback { _ => mounted = false; dom.window.clearTimeout(timer) },
      offset.signal.changes --> (_ => items()),
      status.signal.changes --> { _ => offset.set(0); items() },
      ErrorLine(failure.signal),
      child.maybe <-- message.signal.map(_.map(m => p(cls := "form-ok", m))),
      child <-- detail.signal.map {
        case None    => p(cls := "muted", "Loading…")
        case Some(d) => head(d, busy.signal, write)
      },
      div(cls := "goal-section",
        h4("In flight"),
        child <-- detail.signal.map(_.toList.flatMap(_.current)).map { cur =>
          if cur.isEmpty then p(cls := "muted", "Nothing in flight.")
          else ul(cls := "job-list", cur.map(i => itemRow(id, i, busy.signal, write)))
        }),
      div(cls := "goal-section",
        h4("Items"),
        div(cls := "toolbar",
          div(cls := "btn-group",
            (None :: ItemStatus.values.toList.map(s => Some(s.wire))).map(s =>
              button(typ := "button", cls := "btn btn--toggle btn--sm", aria.pressed <-- status.signal.map(v => (v == s).toString),
                s.getOrElse("all"), onClick --> (_ => status.set(s)))))),
        child <-- page.signal.map {
          case None => p(cls := "muted", "Loading…")
          case Some(pg) if pg.items.isEmpty => p(cls := "muted", "No items.")
          case Some(pg) => ul(cls := "job-list manager-items", pg.items.map(i => itemRow(id, i, busy.signal, write)))
        },
        Pager("items", offset, ItemLimit, page.signal.map(_.fold(0)(_.items.size)), page.signal.map(_.fold(0)(_.total)))),
      hintBox(id, busy.signal, write),
      div(cls := "goal-section",
        h4("Hints"),
        child <-- detail.signal.map(_.toList.flatMap(_.hints)).map { hs =>
          if hs.isEmpty then p(cls := "muted", "No live hints.")
          else ul(cls := "manager-hints", hs.map(h => li(
            span(ManagersMarkdown.hint(h)),
            child.maybe <-- Auth.writable.map(w => Option.when(w)(button(typ := "button", cls := "link-btn", " retire",
              disabled <-- busy, onClick --> (_ => write("retire hint")(ApiClient.managerRemoveHint(id, h.id)))))))))
        }),
      div(cls := "goal-section",
        h4("Events"),
        child <-- events.signal.map(es =>
          if es.isEmpty then p(cls := "muted", "No events yet.")
          else ul(cls := "manager-events", es.map(e => li(span(cls := "muted", s"${e.at} · "), strong(e.kind), s" ${e.text}",
            e.runId.map(r => span(" ", button(typ := "button", cls := "link-btn", s"run ${r.take(8)}",
              onClick --> (_ => WindowManager.open(WindowKey.Run(r)))))))))),
        // newest first with a `before` cursor: the list-paging branch's "Show older"
        ListPaging.olderButton(older.signal.map(_.isDefined), loadingOlder.signal, () => showOlder())),
      child <-- detail.signal.map(_.map(_.manager)).map {
        case None    => emptyNode
        case Some(m) => settings(m, busy.signal, write)
      }
    )

  private def head(d: ManagerDetail, busy: Signal[Boolean], write: String => (=> scala.concurrent.Future[Any]) => Unit): HtmlElement =
    val m = d.manager
    div(cls := "goal-detail",
      div(cls := "run-head", stateBadge(m), span(cls := "run-title", s" ${m.id} · ${m.kind}${m.goal.fold("")(g => s" · goal $g")}")),
      m.statusNote.filter(_.nonEmpty).map(n => p(cls := "job-progress", n)),
      Option.when(m.state == State.Paused.wire && m.counts.open == 0 && m.inFlight == 0 && m.kind == Kind.Review.wire)(
        p(cls := "muted", "Paused: it starts nothing (nothing is spent) until you resume it.")),
      dl(cls := "run-facts",
        dt("items"), dd(counts(m.counts)),
        dt("in flight"), dd(Plural.count(m.inFlight, "run")),
        dt("settings"), dd(ManagersHandlersText.describe(m.effective))),
      div(cls := "form-row",
        child <-- Auth.writable.map { w =>
          if !w then Auth.keyNeeded("steer this manager")
          else
            val st = State.parse(m.state)
            div(cls := "toolbar",
              div(cls := "btn-group",
                Option.when(st.contains(State.Active))(List(
                  button(typ := "button", cls := "btn", "pause", disabled <-- busy, title := "start no new runs; its runs in flight go on",
                    onClick --> (_ => write("pause")(ApiClient.managerAction(m.id, "pause")))),
                  button(typ := "button", cls := "btn", "pause + runs", disabled <-- busy, title := "also pause its runs in flight",
                    onClick --> (_ => write("pause with its runs")(ApiClient.managerAction(m.id, "pause", runs = true)))))).toList.flatten,
                Option.when(!st.contains(State.Active))(button(typ := "button", cls := "btn btn--primary", "resume", disabled <-- busy,
                  onClick --> (_ => write("resume")(ApiClient.managerAction(m.id, "resume"))))),
                Option.when(!st.contains(State.Stopped))(button(typ := "button", cls := "btn btn--danger", "stop", disabled <-- busy,
                  title := "cancel its runs in flight and its open items",
                  onClick --> (_ => if dom.window.confirm(s"Stop ${m.id}: cancel its runs in flight and its open items?") then
                    write("stop")(ApiClient.managerAction(m.id, "stop")))))))
        }))

  private def itemRow(mid: String, i: ManagerItem, busy: Signal[Boolean],
      write: String => (=> scala.concurrent.Future[Any]) => Unit): HtmlElement =
    val prio  = Var(i.priority.toString)
    val until = Var("")
    li(cls := s"job-row manager-item item-${i.status}",
      span(cls := s"job-state state-${i.status}", i.status),
      div(cls := "job-main",
        div(cls := "job-subject", com.histmersion.web.ArticleLink(i.target, i.target),
          span(cls := "muted", s" · priority ${i.priority} · ${Plural.count(i.attempts, "run")}" +
            (if i.failures > 0 then s", ${i.failures} failed" else ""))),
        i.run.map(r => div(cls := "job-progress",
          button(typ := "button", cls := "link-btn", s"run ${r.id.take(8)} ${r.status}", onClick --> (_ => WindowManager.open(WindowKey.Run(r.id)))),
          r.progress.filter(_.nonEmpty).map(p => span(cls := "muted", s" · $p")))),
        i.hold.map(h => div(cls := "muted", "held" + h.until.fold("")(u => s" until $u") + h.untilSource.fold("")(s => s" until $s exists") +
          Option(h.reason).filter(_.nonEmpty).fold("")(r => s" — $r"))),
        i.notBefore.map(t => div(cls := "muted", s"not before $t")),
        i.outcome.filter(_.nonEmpty).map(o => div(cls := "muted", o)),
        child.maybe <-- Auth.writable.map(w => Option.when(w && itemActions(i.status).nonEmpty)(
          div(cls := "form-row manager-item-actions",
            itemActions(i.status).map {
              case "hold" => span(
                input(typ := "text", cls := "field", aria.label := "hold until", placeholder := "until 2026-11-01 or a DOI",
                  controlled(value <-- until.signal, onInput.mapToValue --> until.writer)),
                button(typ := "button", cls := "btn btn--sm", "hold", disabled <-- busy, onClick --> { _ =>
                  val u = until.now().trim
                  val patch = if u.isEmpty then ItemPatch("hold")
                    else if ManagerPolicyText.isDate(u) then ItemPatch("hold", until = Some(u)) else ItemPatch("hold", untilSource = Some(u))
                  write(s"hold ${i.target}")(ApiClient.managerPatchItem(mid, i.id.toString, patch))
                }))
              case "priority" => span(
                input(typ := "number", cls := "field manager-prio", aria.label := "priority",
                  controlled(value <-- prio.signal, onInput.mapToValue --> prio.writer)),
                button(typ := "button", cls := "btn btn--sm", "set priority", disabled <-- busy, onClick --> (_ =>
                  int(prio.now()).orElse(prio.now().trim.toIntOption).foreach(p =>
                    write(s"priority of ${i.target}")(ApiClient.managerPatchItem(mid, i.id.toString, ItemPatch("priority", priority = Some(p))))))))
              case a => button(typ := "button", cls := (if a == "skip" then "btn btn--sm btn--danger" else "btn btn--sm"), a,
                disabled <-- busy, onClick --> (_ => write(s"$a ${i.target}")(ApiClient.managerPatchItem(mid, i.id.toString, ItemPatch(a)))))
            }))))
    )

  private def hintBox(id: String, busy: Signal[Boolean], write: String => (=> scala.concurrent.Future[Any]) => Unit): HtmlElement =
    val text   = Var("")
    val item   = Var("")
    val once   = Var(false)
    val action = Var("none")
    val targets = Var("")
    val tag    = Var("")
    val until  = Var("")
    val source = Var("")
    val error  = Var(Option.empty[String])
    div(cls := "goal-section manager-hint-box",
      h4("Hint"),
      p(cls := "muted", "A note the fixer and the skeptic of its next runs read (subordinate to the record). " +
        "Optionally, an action applied now: put articles first, hold them, or skip them."),
      child <-- Auth.writable.map { w =>
        if !w then Auth.keyNeeded("give this manager a hint")
        else
          div(
            textArea(cls := "field manager-hint-text", aria.label := "hint", placeholder := "e.g. prefer the 2019 dating; the skeptic's Kristiansen critique is covered in [[x]]",
              controlled(value <-- text.signal, onInput.mapToValue --> text.writer)),
            div(cls := "form-row",
              input(typ := "text", cls := "field", aria.label := "only about this article", placeholder := "only about (article slug, optional)",
                controlled(value <-- item.signal, onInput.mapToValue --> item.writer)),
              label(input(typ := "checkbox", controlled(checked <-- once.signal, onClick.mapToChecked --> once.writer)), " next run only"),
              select(cls := "field", aria.label := "action",
                List("none", HintEffect.Prioritise, HintEffect.HoldAction, HintEffect.Skip).map(a => option(value := a, a)),
                controlled(value <-- action.signal, onChange.mapToValue --> action.writer))),
            child.maybe <-- action.signal.map(a => Option.when(a != "none")(div(cls := "form-row",
              input(typ := "text", cls := "field", aria.label := "articles", placeholder := "articles (slugs, comma-separated)",
                controlled(value <-- targets.signal, onInput.mapToValue --> targets.writer)),
              input(typ := "text", cls := "field", aria.label := "or a tag", placeholder := "or a tag",
                controlled(value <-- tag.signal, onInput.mapToValue --> tag.writer)),
              Option.when(a == HintEffect.HoldAction)(List(
                input(typ := "text", cls := "field", aria.label := "until a date", placeholder := "until 2026-11-01",
                  controlled(value <-- until.signal, onInput.mapToValue --> until.writer)),
                input(typ := "text", cls := "field", aria.label := "until a source exists", placeholder := "or until a DOI/citekey exists",
                  controlled(value <-- source.signal, onInput.mapToValue --> source.writer)))).toList.flatten))),
            ErrorLine(error.signal),
            button(typ := "button", cls := "btn btn--primary", "add hint", disabled <-- busy, onClick --> { _ =>
              hint(text.now(), item.now(), once.now(), action.now(), targets.now(), tag.now(), until.now(), source.now()) match
                case Left(e) => error.set(Some(e))
                case Right(h) =>
                  error.set(None)
                  write("hint")(ApiClient.managerAddHint(id, h))
                  text.set(""); targets.set(""); tag.set(""); until.set(""); source.set("")
            }))
      })

  private def settings(m: ManagerInfo, busy: Signal[Boolean], write: String => (=> scala.concurrent.Future[Any]) => Unit): HtmlElement =
    val form  = Var(fields(m.config))
    val error = Var(Option.empty[String])
    val eff   = fields(m.effective)
    htmlTag("details")(cls := "goal-section manager-settings",
      htmlTag("summary")("Settings"),
      p(cls := "muted", "Blank = the server's default (shown as the placeholder)."),
      div(cls := "manager-settings-grid",
        Settings.map { (k, lbl) =>
          label(cls := "manager-setting", span(lbl),
            input(typ := "text", cls := "field", aria.label := lbl, placeholder := eff.getOrElse(k, ""),
              controlled(value <-- form.signal.map(_.getOrElse(k, "")), onInput.mapToValue --> (v => form.update(_ + (k -> v))))))
        }),
      ErrorLine(error.signal),
      child <-- Auth.writable.map { w =>
        if !w then Auth.keyNeeded("change these settings")
        else button(typ := "button", cls := "btn btn--primary", "save settings", disabled <-- busy, onClick --> { _ =>
          config(form.now()) match
            case Left(e)  => error.set(Some(e))
            case Right(c) => error.set(None); write("settings")(ApiClient.managerUpdate(m.id, ManagerUpdate(Some(c))))
        })
      })

/** The settings line a manager window shows (the server's own wording). */
object ManagersHandlersText:
  def describe(c: ManagerConfig): String =
    List(c.maxRuns.map(n => s"$n at once"), c.rounds.map(n => s"$n rounds"), c.maxRunsPerArticle.map(n => s"≤ $n runs per article"),
      c.noProgressRuns.map(n => s"no-progress window $n"), c.reopenLimit.map(n => s"reopen limit $n"),
      c.cooldownMinutes.map(n => s"cooldown $n min"), c.includeTags.filter(_.nonEmpty).map(t => s"tags ${t.mkString(", ")}"),
      c.includeGoal.map(g => s"goal $g"), c.models.filter(_.nonEmpty).map(ms => ms.map(_.text).mkString("; ")),
      c.budgetUsd.map(u => s"$u USD per run")).flatten.mkString(" · ")

object ManagerPolicyText:
  private val Date = "^\\d{4}-\\d{2}-\\d{2}(T.*)?$".r
  /** A date (or an instant) rather than a DOI or a citekey. */
  def isDate(s: String): Boolean = Date.matches(s.trim)
