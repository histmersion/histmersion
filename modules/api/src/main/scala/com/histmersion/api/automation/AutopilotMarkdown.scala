package com.histmersion
package api.automation

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.Plural
import com.histmersion.api.given
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
object AutopilotMarkdown:

  import Autopilot.*

  private def counted(n: Long, singular: String): Plural.Phrase =
    Plural.count(Plural.CountN(n), Plural.CountSingular(singular))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def brief(m: ManagerBrief): String =
    s"${m.id} ${m.state}" + m.note
      .filter(n => (n).value.nonEmpty && (n).value != (m.state).value)
      .fold("")(n => s" ($n)")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def goal(g: GoalProgress): String =
    val mgrs = if g.managers.isEmpty then "no managers" else g.managers.map(brief).mkString("; ")
    val pace = s"${g.doneLast7} done in 7 days, ${g.open} open" + g.etaDays.fold("")(d => s", ~$d day(s) at that pace")
    s"**${g.slug}** _(${g.status})_${g.parent.fold("")(p => s" ⊂ $p")} · ${counted((g.sources).value.toLong, "source")}, " +
      s"${counted((g.articles).value.toLong, "article")}" + (if (g.openApprovals).value > 0 then
                                                               s", ${counted((g.openApprovals).value.toLong, "open approval")}"
                                                             else "") +
      s" · $mgrs · $pace" + g.lastActivity.fold("")(t => s" · last activity $t")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pace(g: FocusGoal): String =
    (s"${g.doneLast7} done in 7 days, ${g.open} open" + g.etaDays.fold("")(d => s", ~$d day(s) at that pace"))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def focus(f: FocusInfo): String =
    val head =
      s"## Focus: ${if (f.on) == FocusInfo.On(true) then s"at most ${f.max} active goals" else "not in force"}" +
        (if f.maxFrom == FocusInfo.MaxFrom("person") then " (set by a person)"
         else " (agents.autopilot.max-active-goals)")
    val lines = List(
      f.note.map(n => s"_${n}_"),
      Option.when((f.held).value > 0)(
        s"${counted((f.held).value.toLong, "model-using item")} held for the focus (nothing cancelled; runs in flight finish)"
      ),
      Option.when(f.changes.nonEmpty)(s"Just now: ${f.changes.mkString("; ")}")
    ).flatten
    val active =
      if f.active.isEmpty then "_No goal is active._"
      else
        f.active.zipWithIndex
          .map { (g, i) =>
            s"${i + 1}. **${g.slug}** _(${g.status}, ${g.stage})_${
                if (g.pinned) == FocusGoal.Pinned(true) then " (pinned)" else ""
              } — ${g.why}" +
              g.doing.fold("")(d => s"\n   - now: $d") + s"\n   - ${pace(g)}"
          }
          .mkString("\n")
    val queue =
      if f.queue.isEmpty then "_Nothing waits for a slot._"
      else
        f.queue.zipWithIndex.map((g, i) => s"${i + 1}. ${g.slug} _(${g.stage})_ — ${g.why}").mkString("\n") +
          (if (f.queued).value > f.queue.size then s"\n\n…and ${(f.queued).value - f.queue.size} more" else "")
    val waits = f.waiting.groupBy(_.code).toList.sortBy(-_._2.size).map { (c, ws) =>
      s"- ${Plural.count(Plural.CountN((ws.size).toLong), Plural.CountSingular("goal"))} $c: " + ws
        .take(5)
        .map(w => s"${w.slug}${if (w.pinned) == FocusWaiting.Pinned(true) then " (pinned)" else ""}")
        .mkString(", ") +
        (if ws.size > 5 then ", …" else "") + ws.headOption.fold("")(w => s" — e.g. ${s"${w.why}".take(200)}")
    }
    (List(head, "") ++ lines
      .flatMap(l => List(l, "")) ++ List("### Active", "", active, "", s"### Queue (${f.queued})", "", queue) ++
      (if waits.isEmpty then Nil else List("", "### Cannot use a slot now", "") ++ waits)).mkString("\n")

  /** The autopilot's status; `ingestedBy` ends the line of today's ingests, `beforeBacklog` and `afterBacklog` are the
    * lines the page shows around the backlog's (the library's upstreams and its re-check).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def status(
      s: Autopilot.EngineStatus,
      ingestedBy: String = (""),
      beforeBacklog: List[String] = Nil,
      afterBacklog: List[String] = Nil
  ): String =
    val head = s"# Autopilot: ${if (s.on) == EngineStatus.On(true) then "ON" else "off"}" +
      s.since.fold("")(t => s" (since $t${s.by.fold("")(b => s", by $b")})")
    val warn = List(
      s.drain.map(d =>
        if (d.drained) == DrainInfo.Drained(true) then
          s"DRAINED since ${d.drainedAt.getOrElse(d.since)} (draining since ${d.since}" +
            s"${
                if (d.by).value.nonEmpty then s" by ${d.by}" else ""
              }): nothing runs and nothing new starts — safe to stop the server; " +
            "`histmersion agent autopilot undrain` lifts it"
        else
          s"DRAINING since ${d.since}${if (d.by).value.nonEmpty then s" by ${d.by}" else ""}: nothing new starts; " +
            s"${counted((d.inFlight).value.toLong, "run")} in flight still finishing"
      ),
      Option.when(!((s.agentsEnabled) == EngineStatus.AgentsEnabled(true)))(
        "agents are disabled on this server (agents.enabled = false): nothing runs"
      ),
      Option.when(
        ((s.agentsEnabled) == EngineStatus.AgentsEnabled(true)) && !((s.managersEnabled) == EngineStatus
          .ManagersEnabled(true))
      )(
        "the managers are disabled (agents.managers.enabled = false): nothing runs"
      ),
      Option.when(!((s.cronsEnabled) == EngineStatus.CronsEnabled(true)))(Autopilot.EngineStatus.CronsOff),
      s.goalPlansWait.map(w => s"$w; a person may still approve a plan")
    ).flatten.map(w => s"- **$w**")
    val rules =
      s.approvals
        .map(r => s"${r.kind} ${r.mode}${if (r.cap).value.nonEmpty then s" (${r.cap})" else ""}")
        .mkString(", ")
    val facts = List(
      s"- approvals: ${if rules.isEmpty then "a person's" else rules}",
      s"- legal open-access ingests today: ${s.ingestedToday}${
          if (s.ingestCap).value > 0 then s" of ${s.ingestCap} (all watchers)" else ""
        }" + ingestedBy
    ) ++
      beforeBacklog ++ List(
        s"- ${ManagersMarkdown.backlog(Managers.ManagerInfo.Backlog(s.backlog.value), s.backlogDays.map(d => Managers.ManagerInfo.BacklogDays(d.value)))}"
      ) ++
      afterBacklog ++ List(
        s"- runs at once: at most ${s.maxConcurrentRuns} (agents.max-concurrent-runs)",
        s"- last backup: ${s.lastBackup.getOrElse("never by the autopilot")} · last lint --fix: ${s.lastLint.getOrElse("never by the autopilot")}",
        s"- library: ${
            if s.library.isEmpty then "no review or enrich manager" else s.library.map(brief).mkString("; ")
          }"
      )
    val changes = if s.changes.isEmpty then "" else "\n\n## Just now\n\n" + s.changes.map(c => s"- $c").mkString("\n")
    val goals = if s.goals.isEmpty then "_No research goals._" else s.goals.map(g => s"- ${goal(g)}").mkString("\n")
    ((List(head, "") ++ warn ++ facts).mkString("\n") + changes + s.focus.fold("")(f => "\n\n" + focus(f)) +
      s"\n\n## Goals (${s.goals.size})\n\n" + goals)
