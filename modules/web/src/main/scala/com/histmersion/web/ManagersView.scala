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
    else if note.startsWith("focus:") then "queued"
    else "active"

  /** What a person can do to this item: by its status — but an item the autopilot's focus holds is not
    * released by hand (pin its goal instead). */
  def itemActionsFor(i: ManagerItem): List[String] =
    if i.hold.exists(_.focus.isDefined) then itemActions(i.status).filterNot(_ == "release") else itemActions(i.status)

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
      "lowWater", "refillBatch", "maxFailures", "staleAfterDays", "minSources", "maxArticles", "autoIngestPerDay", "extendMinNew")
      .filter(k => g(k).nonEmpty && int(g(k)).isEmpty)
    def onOff(k: String, what: String): Either[String, Option[Boolean]] = g(k).toLowerCase match
      case "" => Right(None)
      case "true" | "on" | "yes" => Right(Some(true))
      case "false" | "off" | "no" => Right(Some(false))
      case other => Left(s"$what: '$other' is not on or off")
    val sweeps = list(g("sweeps"), ",").map(ws => ws.map(w => Sweep.parse(w).toRight(w)))
    val infer = for
      i  <- onOff("inferFilter", "infer filter")
      ai <- onOff("autoIngest", "ingest legal open access")
      aa <- onOff("autoApprove", "decide its approvals by code checks")
      _  <- sweeps.toList.flatten.collectFirst { case Left(w) => w }
              .fold[Either[String, Unit]](Right(()))(w => Left(s"sweeps: '$w' is not acquisition or currency"))
    yield (i, ai, aa)
    val usd = g("budgetUsd")
    val daily = g("dailyUsd")
    val models = list(g("models"), ";").getOrElse(Nil).map(Agents.ModelOverride.parse)
    if bad.nonEmpty then Left(s"not a whole number: ${bad.mkString(", ")}")
    else if usd.nonEmpty && usd.toDoubleOption.forall(_ <= 0) then Left("budget USD is not a positive number")
    else if daily.nonEmpty && daily.toDoubleOption.forall(_ < 0) then Left("daily cap USD is not a number (0 = no cap)")
    else infer.flatMap((inf, ai, aa) => models.collectFirst { case Left(e) => e } match
      case Some(e) => Left(s"models: $e")
      case None =>
        Right(ManagerConfig(int(g("maxRuns")), int(g("maxOpen")), int(g("rounds")), int(g("maxRunsPerArticle")),
          int(g("noProgressRuns")), int(g("reopenLimit")), int(g("cooldownMinutes")), int(g("lowWater")), int(g("refillBatch")),
          int(g("maxFailures")), usd.toDoubleOption, list(g("includeTags"), ","), Option(g("includeGoal")).filter(_.nonEmpty),
          Option(models.collect { case Right(m) => m }).filter(_.nonEmpty), int(g("staleAfterDays")), inf,
          int(g("minSources")), int(g("maxArticles")), sweeps.map(_.collect { case Right(s) => s.wire }.distinct), ai,
          int(g("autoIngestPerDay")), daily.toDoubleOption, aa, int(g("extendMinNew")))))

  /** A config as the form shows it (blank = the default). */
  def fields(c: ManagerConfig): Map[String, String] = Map(
    "maxRuns" -> c.maxRuns, "maxOpen" -> c.maxOpen, "rounds" -> c.rounds, "maxRunsPerArticle" -> c.maxRunsPerArticle,
    "noProgressRuns" -> c.noProgressRuns, "reopenLimit" -> c.reopenLimit, "cooldownMinutes" -> c.cooldownMinutes,
    "lowWater" -> c.lowWater, "refillBatch" -> c.refillBatch, "maxFailures" -> c.maxFailures,
    "staleAfterDays" -> c.staleAfterDays, "minSources" -> c.minSources, "maxArticles" -> c.maxArticles,
    "autoIngestPerDay" -> c.autoIngestPerDay, "extendMinNew" -> c.extendMinNew
  ).view.mapValues(_.fold("")(_.toString)).toMap ++ Map("inferFilter" -> c.inferFilter.fold("")(b => if b then "on" else "off"),
    "autoApprove" -> c.autoApprove.fold("")(b => if b then "on" else "off"),
    "autoIngest" -> c.autoIngest.fold("")(b => if b then "on" else "off"), "sweeps" -> c.sweeps.fold("")(_.mkString(", ")),
    "budgetUsd" -> c.budgetUsd.fold("")(_.toString), "dailyUsd" -> c.dailyUsd.fold("")(_.toString),
    "includeTags" -> c.includeTags.getOrElse(Nil).mkString(", "),
    "includeGoal" -> c.includeGoal.getOrElse(""), "models" -> c.models.getOrElse(Nil).map(_.text).mkString("; "))

  /** The settings the form offers, with their labels. */
  val Settings: List[(String, String)] = List(
    "maxRuns" -> "runs at once", "maxOpen" -> "open runs (incl. paused)", "rounds" -> "rounds per run",
    "maxRunsPerArticle" -> "runs per article", "noProgressRuns" -> "no-progress window (runs)",
    "reopenLimit" -> "reopen limit", "cooldownMinutes" -> "cooldown (minutes)", "lowWater" -> "refill below",
    "refillBatch" -> "refill batch", "maxFailures" -> "failures before stop", "budgetUsd" -> "budget per run (USD)",
    "dailyUsd" -> "daily cap (USD of metered spend; plan-billed runs count 0; 0 = none)",
    "includeTags" -> "only articles tagged (comma-separated)", "includeGoal" -> "only a goal's articles",
    "models" -> "models (role=provider:model; …)", "staleAfterDays" -> "review again after (days; 0 never)",
    "inferFilter" -> "infer a missing filter first (on/off)", "minSources" -> "seed: sources per article at least",
    "maxArticles" -> "seed: articles per plan at most", "sweeps" -> "watch: sweeps (acquisition, currency)",
    "autoIngest" -> "watch: ingest legal open-access finds (on/off; legal open access only)", "autoIngestPerDay" -> "watch: ingests a day at most",
    "autoApprove" -> "decide its runs' plans and drafts by code checks (on/off; blank = agents.approvals)",
    "extendMinNew" -> "extend an article at this many new sources (0 never)")

  /** What a manager's settings form offers: every setting, but a watcher's only to a watcher (and the
    * review settings not to one). */
  def settingsFor(kind: String): List[(String, String)] =
    val watchOnly = Set("sweeps", "autoIngest", "autoIngestPerDay")
    val reviewOnly = Set("rounds", "maxRunsPerArticle", "noProgressRuns", "reopenLimit", "lowWater", "refillBatch",
      "includeTags", "includeGoal", "staleAfterDays", "inferFilter", "extendMinNew")
    if kind == Kind.Watch.wire then Settings.filterNot((k, _) => reviewOnly(k))
    else Settings.filterNot((k, _) => watchOnly(k))

  /** A watcher's sweeps as its events tell them: each settled sweep and each work it ingested, newest first. */
  def sweepEvents(es: List[ManagerEvent]): List[ManagerEvent] =
    es.filter(e => e.kind == Feed.Swept || e.kind == Feed.Ingested)

  /** The hint box → a hint. `action` none/prioritise/hold/skip; `targets` comma-separated slugs; `suggest`
    * = the server's model proposes the action (none chosen here; a person approves what it proposes). */
  def hint(text: String, item: String, once: Boolean, action: String, targets: String, tag: String, until: String,
      untilSource: String, suggest: Boolean = false): Either[String, HintAdd] =
    val t = text.trim
    val a = action.trim.toLowerCase
    if t.isEmpty then Left("a hint needs text")
    else if suggest && a.nonEmpty && a != "none" then Left("choose an action, or let the model suggest one — not both")
    else if suggest then Right(HintAdd(t, Option(item.trim).filter(_.nonEmpty), Some(once), suggest = Some(true)))
    else if a.isEmpty || a == "none" then Right(HintAdd(t, Option(item.trim).filter(_.nonEmpty), Some(once)))
    else if !HintEffect.Actions.contains(a) then Left(s"unknown action $a")
    else
      val e = HintEffect(a, list(targets, ",").getOrElse(Nil), Option(tag.trim).filter(_.nonEmpty), None, None,
        Option(until.trim).filter(_.nonEmpty), Option(untilSource.trim).filter(_.nonEmpty))
      if a == HintEffect.HoldAction && e.until.isEmpty && e.untilSource.isEmpty then Left("hold: give a date or a source")
      else if e.targets.isEmpty && e.tag.isEmpty && item.trim.isEmpty then Left(s"$a: name the articles or a tag")
      else Right(HintAdd(t, Option(item.trim).filter(_.nonEmpty), Some(once), Some(e)))

  // ── the page buttons: Review, Extend, Enrich ────────────────────────────────────────────────────

  /** What an enqueue did, in one line: the items queued (and how many were skipped as enriched). */
  def enqueuedNote(kind: EnqueueKind, r: EnqueueResult): String =
    val what = kind match
      case EnqueueKind.Review => "queued for review"
      case EnqueueKind.Extend => "queued to be extended, then reviewed"
      case EnqueueKind.Enrich => "queued for enrichment"
      case EnqueueKind.Seed   => "queued: its seeding manager proposes the next articles"
    val skipped = if r.skipped.isEmpty then "" else s"; ${Plural.count(r.skipped.size, "source")} enriched already"
    r.items match
      case Nil if kind == EnqueueKind.Enrich => s"nothing to enrich$skipped"
      case Nil                               => "nothing queued"
      case List(i)                           => s"$what (item #${i.id}, ${i.status})$skipped"
      case is                                => s"${Plural.count(is.size, "item")} $what$skipped"

  /** Buttons that hand a target to a manager (only where the server runs agents), with the line saying
    * what was queued and a link to the manager. */
  def enqueueButtons(target: String, buttons: List[(EnqueueKind, Icon, String, String)]): HtmlElement =
    val note = Var(Option.empty[(String, String)])
    def go(kind: EnqueueKind): Unit =
      ApiClient.enqueue(kind, target).onComplete(r => note.set(Some(r.fold(
        e => (s"not queued: ${e.getMessage}", ""), x => (enqueuedNote(kind, x), x.managerId)))))
    div(
      cls := "wm-actions",
      child.maybe <-- RunsView.enabled.signal.map(on => Option.when(on)(div(cls := "btn-group",
        buttons.map((kind, icon, label, tip) => WriteButton(icon, label, tip, () => go(kind)))))),
      child.maybe <-- note.signal.map(_.map((n, id) => span(cls := "muted wm-note", n,
        Option.when(id.nonEmpty)(button(typ := "button", cls := "link-btn", " open the manager",
          onClick --> (_ => WindowManager.open(WindowKey.Manager(id)))))))))

  /** The article window: Review (the review manager, first in line), Extend (compile the new sources of
    * its filter into it, then review it) and Enrich (the sources it cites that are not enriched yet). */
  def articleActions(slug: String): HtmlElement =
    enqueueButtons(slug, List(
      (EnqueueKind.Review, Icon.Managers, "review", "queue this article for the review manager (first in line)"),
      (EnqueueKind.Extend, Icon.Edit, "extend", "compile the new sources of its filter into this article, then review it"),
      (EnqueueKind.Enrich, Icon.Runs, "enrich", "enrich the sources this article cites that are not enriched yet")))

  /** The goal window: Extend (its seeding manager proposes its next articles — a person approves the plan)
    * and Enrich (the sources linked to it that are not enriched yet). */
  def goalActions(slug: String): HtmlElement =
    enqueueButtons(slug, List(
      (EnqueueKind.Seed, Icon.Edit, "extend", "propose the next articles of this research area (you approve the plan)"),
      (EnqueueKind.Enrich, Icon.Runs, "enrich", "enrich the sources linked to this goal that are not enriched yet")))

  // ── the autopilot ───────────────────────────────────────────────────────────────────────────────

  /** One goal's line in the autopilot's table: its counts and its pace. */
  def goalLine(g: Autopilot.GoalProgress): String =
    s"${Plural.count(g.sources, "source")} · ${Plural.count(g.articles, "article")}" +
      (if g.openApprovals > 0 then s" · ${Plural.count(g.openApprovals, "open approval")}" else "") +
      s" · ${g.doneLast7} done in 7 days, ${g.open} open" + g.etaDays.fold("")(d => s" (~$d d)")

  /** An active or queued goal's pace, in a line. */
  def focusPace(g: Autopilot.FocusGoal): String =
    s"${g.doneLast7} done in 7 days, ${g.open} open" + g.etaDays.fold("")(d => s" (~$d d)")

  /** The goals that cannot use a focus slot now, by why, the commonest first. */
  def waitingSummary(ws: List[Autopilot.FocusWaiting]): String =
    ws.groupBy(_.code).toList.sortBy((c, xs) => (-xs.size, c)).map((c, xs) => s"${xs.size} $c").mkString(" · ")

  /** The focus's heading: the cap and where it comes from, or why it is not in force. */
  def focusHead(f: Autopilot.FocusInfo): String =
    (if f.on then s"Focus: at most ${f.max} active goals" else "Focus: not in force") +
      (if f.maxFrom == "person" then " (cap set by a person)" else "") +
      (if f.held > 0 then s" · ${Plural.count(f.held, "item")} held" else "")

  /** The focus: the active goals (why each was chosen, what it does or waits for, its pace), the queue in
    * the order the next become active, and why the others cannot use a slot; pin / unpin a goal and set
    * the cap (each behind the write gate). */
  private def focusPanel(f: Autopilot.FocusInfo, changed: Autopilot.FocusInfo => Unit, failure: Var[Option[String]]): HtmlElement =
    val cap = Var(f.max.toString)
    def go(what: String)(call: => scala.concurrent.Future[Autopilot.FocusInfo]): Unit =
      if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
      else call.onComplete(_.fold(e => failure.set(Some(s"focus $what: ${e.getMessage}")), x => { failure.set(None); changed(x) }))
    def pinButton(g: Autopilot.FocusGoal) =
      val (verb, tip) = if g.pinned then ("unpin", "let the focus choose again") else ("pin", "keep this goal active (it takes a slot now)")
      button(typ := "button", cls := "btn btn--sm", verb, title := tip, onClick --> (_ => go(s"$verb ${g.slug}")(ApiClient.setFocus(verb, Some(g.slug)))))
    def goalLink(slug: String) = button(typ := "button", cls := "link-btn", slug, onClick --> (_ => WindowManager.open(WindowKey.Goal(slug))))
    div(cls := "autopilot-focus",
      div(cls := "toolbar",
        div(cls := "btn-group", span(cls := "autopilot-state", focusHead(f))),
        div(cls := "btn-group",
          input(typ := "number", cls := "field manager-prio", aria.label := "the cap of active goals", minAttr := "0",
            controlled(value <-- cap.signal, onInput.mapToValue --> cap.writer)),
          button(typ := "button", cls := "btn btn--sm", "set cap", title := "at most this many goals active for model work (0 = no cap)",
            onClick --> (_ => int(cap.now()).foreach(n => go("cap")(ApiClient.setFocus("max", value = Some(n)))))),
          Option.when(f.maxFrom == "person")(button(typ := "button", cls := "btn btn--sm", "default",
            title := "back to agents.autopilot.max-active-goals", onClick --> (_ => go("cap")(ApiClient.setFocus("max"))))))),
      f.note.map(n => div(cls := "muted", n)),
      Option.when(f.changes.nonEmpty)(div(cls := "form-ok", f.changes.mkString("; ").take(400))),
      ul(cls := "job-list focus-active", f.active.map(g =>
        li(cls := "job-row",
          span(cls := s"job-state state-${g.status}", g.stage),
          div(cls := "job-main",
            div(cls := "job-subject", goalLink(g.slug), Option.when(g.pinned)(span(cls := "muted", " · pinned")),
              span(cls := "muted", s" · ${g.title}")),
            div(cls := "job-progress", g.why),
            g.doing.map(d => div(cls := "muted", s"now: $d")),
            div(cls := "job-meta muted", focusPace(g)),
            child.maybe <-- Auth.writable.map(w => Option.when(w)(div(cls := "form-row", pinButton(g)))))))),
      Option.when(f.queue.nonEmpty)(div(
        div(cls := "muted", s"next (${f.queued} waiting for a slot):"),
        ol(cls := "job-list focus-queue", f.queue.map(g =>
          li(cls := "job-row",
            span(cls := s"job-state state-${g.status}", g.stage),
            div(cls := "job-main",
              div(cls := "job-subject", goalLink(g.slug)),
              div(cls := "muted", g.why),
              child.maybe <-- Auth.writable.map(w => Option.when(w)(div(cls := "form-row", pinButton(g)))))))))),
      Option.when(f.waiting.nonEmpty)(div(cls := "muted", s"cannot use a slot now: ${waitingSummary(f.waiting)}")))

  /** The goals by stage, in the order a goal moves through them: how many in each. */
  def stages(gs: List[Autopilot.GoalProgress]): String =
    GoalStatus.all.map(st => st -> gs.count(_.status == st)).filter(_._2 > 0).map((st, n) => s"$n $st").mkString(" · ")

  /** The switch and the status: on/off (the click shows the new state at once; the server's answer
    * follows), the approval rules, today's ingests, and every goal's stage, counts and pace. */
  def autopilotSection: HtmlElement =
    val st      = Var(Option.empty[Autopilot.AutopilotStatus])
    val pending = Var(Option.empty[Boolean])
    val failure = Var(Option.empty[String])
    def load(): Unit = ApiClient.autopilot.onComplete(_.fold(e => failure.set(Some(e.getMessage)), s => { failure.set(None); st.set(Some(s)) }))
    def flip(on: Boolean): Unit =
      pending.set(Some(on))
      ApiClient.setAutopilot(on).onComplete { r =>
        pending.set(None)
        r.fold(e => failure.set(Some(s"autopilot: ${e.getMessage}")), s => { failure.set(None); st.set(Some(s)) })
      }
    val shownOn = st.signal.combineWith(pending.signal).map((s, p) => p.orElse(s.map(_.on)).getOrElse(false))
    div(
      cls := "autopilot",
      onMountCallback(_ => load()),
      div(cls := "toolbar",
        div(cls := "btn-group",
          span(cls := "autopilot-state", cls("is-on") <-- shownOn, child.text <-- shownOn.map(on => if on then "Autopilot ON" else "Autopilot off")),
          child <-- shownOn.map(on =>
            if on then WriteButton(Icon.Managers, "turn off", "pause the seeding managers and watchers the autopilot runs", () => flip(false))
            else WriteButton(Icon.Managers, "turn on",
              "every research goal gets an active seeding manager and watcher and moves from planned to reviewed by itself", () => flip(true))))),
      ErrorLine(failure.signal),
      child.maybe <-- st.signal.map(_.map { s =>
        div(cls := "autopilot-status",
          div(cls := "muted", s.since.fold("")(t => s"since $t") + s.by.fold("")(b => s" by $b") +
            s" · ${stages(s.goals)} · ingests today ${s.ingestedToday}${if s.ingestCap > 0 then s" of ${s.ingestCap}" else ""}" +
            s" · at most ${s.maxConcurrentRuns} runs at once"),
          Option.when(s.backlog > 0)(div(cls := "muted", ManagersMarkdown.backlog(s.backlog, s.backlogDays))),
          div(cls := "muted", "approvals: " + s.approvals.map(r => s"${r.kind} ${r.mode}").mkString(" · ")),
          Option.when(!s.cronsEnabled)(p(cls := "form-error", role := "alert", Autopilot.AutopilotStatus.CronsOff)),
          Option.when(s.changes.nonEmpty)(div(cls := "form-ok", s.changes.mkString("; ").take(400))),
          s.focus.map(f => focusPanel(f, x => st.update(_.map(_.copy(focus = Some(x)))), failure)),
          ul(cls := "job-list autopilot-goals", s.goals.map(g =>
            li(cls := "job-row",
              span(cls := s"job-state state-${g.status}", g.status),
              div(cls := "job-main",
                div(cls := "job-subject",
                  button(typ := "button", cls := "link-btn", g.slug, onClick --> (_ => WindowManager.open(WindowKey.Goal(g.slug)))),
                  span(cls := "muted", s" · ${g.title}")),
                div(cls := "job-meta muted", goalLine(g)),
                Option.when(g.managers.nonEmpty)(div(cls := "muted", g.managers.map(m => s"${m.id} ${m.state}").mkString(" · "))))))))
      })
    )

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
      autopilotSection,
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
              div(cls := "job-meta muted", counts(m.counts), s" · ${Plural.count(m.inFlight, "run")} in flight",
                s" · ${ManagersMarkdown.today(m)}"),
              Option.when(m.backlog > 0)(div(cls := "muted", ManagersMarkdown.backlog(m.backlog, m.backlogDays))),
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
      child.maybe <-- detail.signal.map(_.filter(_.manager.kind == Kind.Watch.wire).map(d => div(cls := "goal-section manager-sweeps",
        h4("Sweeps"),
        p(cls := "muted", d.manager.cron.fold("No cron: its sweeps run only when re-opened by hand.")(c =>
          s"Cron `$c` (UTC)" + d.manager.nextTickAt.fold("")(t => s", next $t") +
            (if d.manager.state == State.Active.wire then "" else " — fires once it is resumed") + ". " +
            (if d.manager.effective.autoIngest.contains(true)
             then s"Legal open-access picks are ingested, at most ${d.manager.effective.autoIngestPerDay.getOrElse(0)} a day (legal open access only)."
             else "Its picks go to the acquisition list (auto-ingest off)."))),
        child <-- events.signal.map(sweepEvents).map(es =>
          if es.isEmpty then p(cls := "muted", "No sweep has settled yet.")
          else ul(cls := "manager-events", es.map(e => li(span(cls := "muted", s"${e.at} · "), strong(e.kind), s" ${e.text}"))))))),
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
      Option.when(m.state == State.Paused.wire && m.inFlight == 0)(
        p(cls := "muted", "Paused: it starts nothing (nothing is spent) until you resume it.")),
      dl(cls := "run-facts",
        dt("items"), dd(counts(m.counts)),
        m.cron.toList.flatMap(c => List(dt("cron"), dd(s"$c (UTC)" + m.nextTickAt.fold("")(t => s", next $t")))),
        dt("in flight"), dd(Plural.count(m.inFlight, "run")),
        Option.when(m.backlog > 0)(List(dt("ingest backlog"), dd(ManagersMarkdown.backlog(m.backlog, m.backlogDays)))).toList.flatten,
        dt("today"), dd(span(ManagersMarkdown.today(m)), span(cls := "muted",
          " — the daily cap counts metered spend; a plan-billed run (Claude or ChatGPT plan) costs 0 USD, its tokens are shown")),
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
          h.focus.fold("")(g => s" for the focus (goal $g; pin it in the managers window to run it now)") +
          Option(h.reason).filter(_.nonEmpty).fold("")(r => s" — $r"))),
        i.notBefore.map(t => div(cls := "muted", s"not before $t")),
        i.outcome.filter(_.nonEmpty).map(o => div(cls := "muted", o)),
        child.maybe <-- Auth.writable.map(w => Option.when(w && itemActionsFor(i).nonEmpty)(
          div(cls := "form-row manager-item-actions",
            itemActionsFor(i).map {
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
    val suggest = Var(false)
    val error  = Var(Option.empty[String])
    div(cls := "goal-section manager-hint-box",
      h4("Hint"),
      p(cls := "muted", "A note the fixer and the skeptic of its next runs read (subordinate to the record). " +
        "Optionally, an action applied now: put articles first, hold them, or skip them — or let the server's model " +
        "read the note and suggest the action (it waits under Approvals until you approve it)."),
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
              child.maybe <-- RunsView.enabled.signal.map(on => Option.when(on)(label(
                title := "the server's planner model proposes the action the note asks for; nothing changes until you approve it",
                input(typ := "checkbox", controlled(checked <-- suggest.signal, onClick.mapToChecked --> suggest.writer)),
                " suggest an action"))),
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
              hint(text.now(), item.now(), once.now(), action.now(), targets.now(), tag.now(), until.now(), source.now(),
                suggest.now()) match
                case Left(e) => error.set(Some(e))
                case Right(h) =>
                  error.set(None)
                  write(if h.suggest.contains(true) then "hint (a model suggests its action: see Approvals)" else "hint")(
                    ApiClient.managerAddHint(id, h))
                  text.set(""); targets.set(""); tag.set(""); until.set(""); source.set(""); suggest.set(false)
            }))
      })

  private def settings(m: ManagerInfo, busy: Signal[Boolean], write: String => (=> scala.concurrent.Future[Any]) => Unit): HtmlElement =
    val form  = Var(fields(m.config))
    val cron  = Var(m.cron.getOrElse(""))
    val error = Var(Option.empty[String])
    val eff   = fields(m.effective)
    htmlTag("details")(cls := "goal-section manager-settings",
      htmlTag("summary")("Settings"),
      p(cls := "muted", "Blank = the server's default (shown as the placeholder)."),
      Option.when(m.kind != Kind.Enrich.wire)(label(cls := "manager-setting", span("cron (UTC: five fields, or @daily/@weekly/@monthly; blank = none)"),
        input(typ := "text", cls := "field", aria.label := "cron", placeholder := (if m.kind == Kind.Watch.wire then "@weekly" else "none"),
          controlled(value <-- cron.signal, onInput.mapToValue --> cron.writer)))),
      div(cls := "manager-settings-grid",
        settingsFor(m.kind).map { (k, lbl) =>
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
            case Right(c) =>
              error.set(None)
              // the cron goes only when it changed (sending it again would move its next firing)
              val cr = Option(cron.now().trim).filter(_ != m.cron.getOrElse(""))
              write("settings")(ApiClient.managerUpdate(m.id, ManagerUpdate(Some(c), cr)))
        })
      })

/** The settings line a manager window shows (the server's own wording). */
object ManagersHandlersText:
  def describe(c: ManagerConfig): String =
    List(c.maxRuns.map(n => s"$n at once"), c.rounds.map(n => s"$n rounds"), c.maxRunsPerArticle.map(n => s"≤ $n runs per article"),
      c.noProgressRuns.map(n => s"no-progress window $n"), c.reopenLimit.map(n => s"reopen limit $n"),
      c.cooldownMinutes.map(n => s"cooldown $n min"), c.includeTags.filter(_.nonEmpty).map(t => s"tags ${t.mkString(", ")}"),
      c.includeGoal.map(g => s"goal $g"), c.models.filter(_.nonEmpty).map(ms => ms.map(_.text).mkString("; ")),
      c.budgetUsd.map(u => s"$u USD per run"), c.dailyUsd.filter(_ > 0).map(u => s"$u USD a day")).flatten.mkString(" · ")

object ManagerPolicyText:
  private val Date = "^\\d{4}-\\d{2}-\\d{2}(T.*)?$".r
  /** A date (or an instant) rather than a DOI or a citekey. */
  def isDate(s: String): Boolean = Date.matches(s.trim)
