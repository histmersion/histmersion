package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The autopilot's wire types (docs/research-process-managers.md "As built: the autopilot"): the switch
  * (`POST /api/agents/autopilot`), and what `GET /api/agents/autopilot` reports — per research goal its
  * stage, managers, sources and articles, open approvals, last activity and the pace of the last week.
  * The rules are in `core/jvm` ([[AutopilotPolicy]]), the work in the server's supervisor. */
object Autopilot:

  /** `POST /api/agents/autopilot` — turn it on or off (idempotent); `by` = who (default `person`). */
  final case class AutopilotSet(on: Boolean, by: Option[String] = None) derives KindlingsCodecAsObject
  object AutopilotSet:
    given Schema[AutopilotSet] = KindlingsSchema.derived[AutopilotSet].schema

  /** One manager, in a line. */
  final case class ManagerBrief(id: String, kind: String, state: String, note: Option[String] = None)
      derives KindlingsCodecAsObject
  object ManagerBrief:
    given Schema[ManagerBrief] = KindlingsSchema.derived[ManagerBrief].schema

  /** One research goal: its status (the stage), its managers, the sources and articles linked to it, the
    * approvals open about it (its plans and its articles' drafts), when one of its managers last did
    * something, the items its managers settled in the last 7 days, the items still open, and the days
    * those would take at that pace (None: nothing open, or nothing done to measure by). */
  final case class GoalProgress(slug: String, title: String, status: String, parent: Option[String] = None,
      managers: List[ManagerBrief] = Nil, sources: Int = 0, articles: Int = 0, openApprovals: Int = 0,
      lastActivity: Option[String] = None, doneLast7: Int = 0, open: Int = 0, etaDays: Option[Int] = None)
      derives KindlingsCodecAsObject
  object GoalProgress:
    given Schema[GoalProgress] = KindlingsSchema.derived[GoalProgress].schema

  /** How an approval kind is decided now: `person`, or `checked` (the supervisor's code checks). */
  final case class ApprovalRule(kind: String, mode: String, cap: String = "") derives KindlingsCodecAsObject
  object ApprovalRule:
    given Schema[ApprovalRule] = KindlingsSchema.derived[ApprovalRule].schema

  /** One goal of the focus (docs/research-process-managers.md "As built: the focus"): active — why it was
    * chosen, what it is doing or waiting for, its pace (items settled in 7 days, open, days at that pace) — or
    * queued — why it stands where it does. `stage` = reviewing | compiling | ready | gathering. */
  final case class FocusGoal(slug: String, title: String = "", status: String = "", stage: String = "", why: String = "",
      pinned: Boolean = false, priority: Int = 0, since: Option[String] = None, doing: Option[String] = None,
      open: Int = 0, doneLast7: Int = 0, etaDays: Option[Int] = None) derives KindlingsCodecAsObject
  object FocusGoal:
    given Schema[FocusGoal] = KindlingsSchema.derived[FocusGoal].schema

  /** A goal that cannot use a slot now: `code` = children (its sub-goals first) | person (an approval only a
    * person decides) | held (a person's hold) | stuck (its items stopped) | idle (nothing to do until its
    * cron or new sources) | plans (enough goal plans wait for a person already). */
  final case class FocusWaiting(slug: String, code: String, why: String = "", pinned: Boolean = false)
      derives KindlingsCodecAsObject
  object FocusWaiting:
    given Schema[FocusWaiting] = KindlingsSchema.derived[FocusWaiting].schema

  /** `GET /api/agents/autopilot/focus`: whether the focus is in force (the autopilot on and a cap above 0),
    * the cap and where it comes from (`config` = agents.autopilot.max-active-goals, `person` = set with
    * `focus max`), the active goals, the next `queue` of the `queued` candidates (in the order they become
    * active), the goals that cannot use a slot now, how many items it holds, and what the last change did. */
  final case class FocusInfo(on: Boolean, max: Int = 0, maxFrom: String = "config", active: List[FocusGoal] = Nil,
      queue: List[FocusGoal] = Nil, queued: Int = 0, waiting: List[FocusWaiting] = Nil, held: Int = 0,
      note: Option[String] = None, changes: List[String] = Nil) derives KindlingsCodecAsObject
  object FocusInfo:
    given Schema[FocusInfo] = KindlingsSchema.derived[FocusInfo].schema

  /** `POST /api/agents/autopilot/focus`: `pin` / `unpin` a goal (a pinned goal is always active), its
    * `priority` (`value`; higher first, after the stage and the tree), or `max` (`value`: the cap, 0 = none;
    * absent: back to agents.autopilot.max-active-goals). `by` = who. */
  final case class FocusSet(action: String, goal: Option[String] = None, value: Option[Int] = None, by: Option[String] = None)
      derives KindlingsCodecAsObject
  object FocusSet:
    given Schema[FocusSet] = KindlingsSchema.derived[FocusSet].schema
    val Actions = List("pin", "unpin", "priority", "max")

  /** Drain mode (#3762 proposal 4): since when and by whom; `drained` = no agent run is queued or running
    * (safe to stop the server: what waits is checkpointed), since `drainedAt`; `inFlight` = runs queued or
    * running now. */
  final case class DrainInfo(since: String, by: String = "", drained: Boolean = false, drainedAt: Option[String] = None,
      inFlight: Int = 0) derives KindlingsCodecAsObject
  object DrainInfo:
    given Schema[DrainInfo] = KindlingsSchema.derived[DrainInfo].schema

  /** `POST /api/agents/autopilot/drain {on}`: start draining (nothing new starts; the runs in flight finish)
    * or lift it. */
  final case class DrainSet(on: Boolean, by: Option[String] = None) derives KindlingsCodecAsObject
  object DrainSet:
    given Schema[DrainSet] = KindlingsSchema.derived[DrainSet].schema

  /** `GET /api/agents/autopilot`. `since`/`by` = when and by whom it was last switched; `changes` = what
    * the switch just did (an on/off answer only); `ingestedToday` / `ingestCap` = the legal open-access
    * ingests of all watchers today and their shared cap (0 = none); `backlog` / `backlogDays` = the legal
    * open-access works waiting in the watchers' ingest backlogs and the days they take to drain at the
    * current allowance (0 = today; None = they do not drain); the last housekeeping runs. */
  final case class AutopilotStatus(on: Boolean, since: Option[String] = None, by: Option[String] = None,
      agentsEnabled: Boolean = false, managersEnabled: Boolean = false, cronsEnabled: Boolean = true, approvals: List[ApprovalRule] = Nil,
      ingestedToday: Int = 0, ingestCap: Int = 0, maxConcurrentRuns: Int = 0, lastBackup: Option[String] = None,
      lastLint: Option[String] = None, library: List[ManagerBrief] = Nil, goals: List[GoalProgress] = Nil,
      changes: List[String] = Nil, backlog: Int = 0, backlogDays: Option[Int] = None, focus: Option[FocusInfo] = None,
      drain: Option[DrainInfo] = None)
      derives KindlingsCodecAsObject
  object AutopilotStatus:
    given Schema[AutopilotStatus] = KindlingsSchema.derived[AutopilotStatus].schema
    /** What the status says while `agents.scheduler.enabled` is false. */
    val CronsOff = "the managers' crons are off (agents.scheduler.enabled = false, HISTMERSION_AGENTS_SCHEDULER): " +
      "each watcher sweeps once and never again, and no seeding manager proposes again — new sources are not looked for"

/** `POST /api/agents/autopilot/repair-acquire` and what it did: the one-shot repair of the acquisition
  * list (docs/research-process-managers.md "As built: the ingest backlog"). */
object RepairAcquire:

  /** `dryRun` = list what it would do and change nothing; `by` = who asked. */
  final case class RepairRequest(dryRun: Boolean = false, by: Option[String] = None) derives KindlingsCodecAsObject
  object RepairRequest:
    given Schema[RepairRequest] = KindlingsSchema.derived[RepairRequest].schema

  /** One acquisition item a watcher's sweep created: `move` (a legal open-access work: into the watcher's
    * ingest backlog, item `backlogItem`, and the acquisition item removed), `remove` (in the corpus
    * already: the acquisition item removed), `keep` (why it stays on the list). */
  final case class RepairRow(item: Int, doi: String, goals: List[String] = Nil, watcher: String = "", action: String = "",
      detail: String = "", backlogItem: Option[Long] = None) derives KindlingsCodecAsObject
  object RepairRow:
    given Schema[RepairRow] = KindlingsSchema.derived[RepairRow].schema

  /** What it did (a dry run: would do). `untouched` = the open acquisition items NOT created by a
    * watcher's sweep (a person's, a plan's, a currency check's): never changed. `backlog` / `backlogDays`
    * = the ingest backlog afterwards and the days to drain it at the current allowance. */
  final case class RepairResult(dryRun: Boolean, moved: Int = 0, removed: Int = 0, kept: Int = 0, untouched: Int = 0,
      rows: List[RepairRow] = Nil, backlog: Int = 0, backlogDays: Option[Int] = None) derives KindlingsCodecAsObject
  object RepairResult:
    given Schema[RepairResult] = KindlingsSchema.derived[RepairResult].schema

  val Move   = "move"
  val Remove = "remove"
  val Keep   = "keep"

  def markdown(r: RepairResult): String =
    val verb = if r.dryRun then "would " else ""
    val head = s"# Repair of the acquisition list${if r.dryRun then " (dry run: nothing changed)" else ""}\n\n" +
      s"- ${verb}move ${Plural.count(r.moved, "legal open-access work")} into the watchers' ingest backlogs\n" +
      s"- ${verb}remove ${Plural.count(r.removed, "item")} already in the corpus\n" +
      s"- keep ${Plural.count(r.kept, "sweep item")} on the list (no legal open-access copy known, or a watcher that does not ingest)\n" +
      s"- ${Plural.count(r.untouched, "open acquisition item")} not created by a watcher's sweep: untouched\n" +
      s"- ${ManagersMarkdown.backlog(r.backlog, r.backlogDays)}"
    val rows = r.rows.map { row =>
      val what = row.action match
        case Move   => s"${verb}move to ${row.watcher}" + row.backlogItem.fold("")(i => s" (backlog item #$i)")
        case Remove => s"${verb}remove"
        case _      => "keep"
      s"- #${row.item} https://doi.org/${row.doi} · goal ${row.goals.mkString(", ")} · $what" +
        (if row.detail.nonEmpty then s": ${row.detail}" else "")
    }
    if rows.isEmpty then head + "\n\n_No acquisition item created by a watcher's sweep is open._"
    else head + "\n\n## Items\n\n" + rows.mkString("\n")

/** The autopilot as Markdown — every surface (CLI, MCP) prints this. Pure. */
object AutopilotMarkdown:
  import Autopilot.*

  private def brief(m: ManagerBrief): String =
    s"${m.id} ${m.state}" + m.note.filter(n => n.nonEmpty && n != m.state).fold("")(n => s" ($n)")

  def goal(g: GoalProgress): String =
    val mgrs = if g.managers.isEmpty then "no managers" else g.managers.map(brief).mkString("; ")
    val pace = s"${g.doneLast7} done in 7 days, ${g.open} open" + g.etaDays.fold("")(d => s", ~$d day(s) at that pace")
    s"**${g.slug}** _(${g.status})_${g.parent.fold("")(p => s" ⊂ $p")} · ${Plural.count(g.sources, "source")}, " +
      s"${Plural.count(g.articles, "article")}" + (if g.openApprovals > 0 then s", ${Plural.count(g.openApprovals, "open approval")}" else "") +
      s" · $mgrs · $pace" + g.lastActivity.fold("")(t => s" · last activity $t")

  private def pace(g: FocusGoal): String =
    s"${g.doneLast7} done in 7 days, ${g.open} open" + g.etaDays.fold("")(d => s", ~$d day(s) at that pace")

  /** The focus: the active goals with why and what each is doing, the queue, the goals that wait. */
  def focus(f: FocusInfo): String =
    val head = s"## Focus: ${if f.on then s"at most ${f.max} active goals" else "not in force"}" +
      (if f.maxFrom == "person" then " (set by a person)" else " (agents.autopilot.max-active-goals)")
    val lines = List(
      f.note.map(n => s"_${n}_"),
      Option.when(f.held > 0)(s"${Plural.count(f.held, "model-using item")} held for the focus (nothing cancelled; runs in flight finish)"),
      Option.when(f.changes.nonEmpty)(s"Just now: ${f.changes.mkString("; ")}")).flatten
    val active =
      if f.active.isEmpty then "_No goal is active._"
      else f.active.zipWithIndex.map { (g, i) =>
        s"${i + 1}. **${g.slug}** _(${g.status}, ${g.stage})_${if g.pinned then " (pinned)" else ""} — ${g.why}" +
          g.doing.fold("")(d => s"\n   - now: $d") + s"\n   - ${pace(g)}"
      }.mkString("\n")
    val queue =
      if f.queue.isEmpty then "_Nothing waits for a slot._"
      else f.queue.zipWithIndex.map((g, i) => s"${i + 1}. ${g.slug} _(${g.stage})_ — ${g.why}").mkString("\n") +
        (if f.queued > f.queue.size then s"\n\n…and ${f.queued - f.queue.size} more" else "")
    val waits = f.waiting.groupBy(_.code).toList.sortBy(-_._2.size).map { (c, ws) =>
      s"- ${Plural.count(ws.size, "goal")} $c: " + ws.take(5).map(w => s"${w.slug}${if w.pinned then " (pinned)" else ""}").mkString(", ") +
        (if ws.size > 5 then ", …" else "") + ws.headOption.fold("")(w => s" — e.g. ${w.why.take(200)}")
    }
    (List(head, "") ++ lines.flatMap(l => List(l, "")) ++ List("### Active", "", active, "", s"### Queue (${f.queued})", "", queue) ++
      (if waits.isEmpty then Nil else List("", "### Cannot use a slot now", "") ++ waits)).mkString("\n")

  def status(s: AutopilotStatus): String =
    val head = s"# Autopilot: ${if s.on then "ON" else "off"}" +
      s.since.fold("")(t => s" (since $t${s.by.fold("")(b => s", by $b")})")
    val warn = List(
      s.drain.map(d => if d.drained then s"DRAINED since ${d.drainedAt.getOrElse(d.since)} (draining since ${d.since}" +
          s"${if d.by.nonEmpty then s" by ${d.by}" else ""}): nothing runs and nothing new starts — safe to stop the server; " +
          "`histmersion agent autopilot undrain` lifts it"
        else s"DRAINING since ${d.since}${if d.by.nonEmpty then s" by ${d.by}" else ""}: nothing new starts; " +
          s"${Plural.count(d.inFlight, "run")} in flight still finishing"),
      Option.when(!s.agentsEnabled)("agents are disabled on this server (agents.enabled = false): nothing runs"),
      Option.when(s.agentsEnabled && !s.managersEnabled)("the managers are disabled (agents.managers.enabled = false): nothing runs"),
      Option.when(!s.cronsEnabled)(AutopilotStatus.CronsOff)
    ).flatten.map(w => s"- **$w**")
    val rules = s.approvals.map(r => s"${r.kind} ${r.mode}${if r.cap.nonEmpty then s" (${r.cap})" else ""}").mkString(", ")
    val facts = List(
      s"- approvals: ${if rules.isEmpty then "a person's" else rules}",
      s"- legal open-access ingests today: ${s.ingestedToday}${if s.ingestCap > 0 then s" of ${s.ingestCap} (all watchers)" else ""}",
      s"- ${ManagersMarkdown.backlog(s.backlog, s.backlogDays)}",
      s"- runs at once: at most ${s.maxConcurrentRuns} (agents.max-concurrent-runs)",
      s"- last backup: ${s.lastBackup.getOrElse("never by the autopilot")} · last lint --fix: ${s.lastLint.getOrElse("never by the autopilot")}",
      s"- library: ${if s.library.isEmpty then "no review or enrich manager" else s.library.map(brief).mkString("; ")}")
    val changes = if s.changes.isEmpty then "" else "\n\n## Just now\n\n" + s.changes.map(c => s"- $c").mkString("\n")
    val goals = if s.goals.isEmpty then "_No research goals._" else s.goals.map(g => s"- ${goal(g)}").mkString("\n")
    (List(head, "") ++ warn ++ facts).mkString("\n") + changes + s.focus.fold("")(f => "\n\n" + focus(f)) +
      s"\n\n## Goals (${s.goals.size})\n\n" + goals
