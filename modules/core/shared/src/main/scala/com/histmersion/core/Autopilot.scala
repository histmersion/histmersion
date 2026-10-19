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

  /** `GET /api/agents/autopilot`. `since`/`by` = when and by whom it was last switched; `changes` = what
    * the switch just did (an on/off answer only); `ingestedToday` / `ingestCap` = the legal open-access
    * ingests of all watchers today and their shared cap (0 = none); `backlog` / `backlogDays` = the legal
    * open-access works waiting in the watchers' ingest backlogs and the days they take to drain at the
    * current allowance (0 = today; None = they do not drain); the last housekeeping runs. */
  final case class AutopilotStatus(on: Boolean, since: Option[String] = None, by: Option[String] = None,
      agentsEnabled: Boolean = false, managersEnabled: Boolean = false, cronsEnabled: Boolean = true, approvals: List[ApprovalRule] = Nil,
      ingestedToday: Int = 0, ingestCap: Int = 0, maxConcurrentRuns: Int = 0, lastBackup: Option[String] = None,
      lastLint: Option[String] = None, library: List[ManagerBrief] = Nil, goals: List[GoalProgress] = Nil,
      changes: List[String] = Nil, backlog: Int = 0, backlogDays: Option[Int] = None) derives KindlingsCodecAsObject
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

  def status(s: AutopilotStatus): String =
    val head = s"# Autopilot: ${if s.on then "ON" else "off"}" +
      s.since.fold("")(t => s" (since $t${s.by.fold("")(b => s", by $b")})")
    val warn = List(
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
    (List(head, "") ++ warn ++ facts).mkString("\n") + changes + s"\n\n## Goals (${s.goals.size})\n\n" + goals
