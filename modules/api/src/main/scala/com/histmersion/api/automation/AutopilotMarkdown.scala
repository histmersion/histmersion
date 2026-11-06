package com.histmersion.api.automation

import com.histmersion.api.Plural
import com.histmersion.api.given
object AutopilotMarkdown:
  type BriefResult = BriefResult.T
  object BriefResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalResult = GoalResult.T
  object GoalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaceResult = PaceResult.T
  object PaceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusResult = FocusResult.T
  object FocusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusResult = StatusResult.T
  object StatusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusIngestedBy = StatusIngestedBy.T
  object StatusIngestedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusBeforeBacklog = StatusBeforeBacklog.T
  object StatusBeforeBacklog extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusAfterBacklog = StatusAfterBacklog.T
  object StatusAfterBacklog extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import Autopilot.*

  private def brief(m: ManagerBrief): com.histmersion.api.automation.AutopilotMarkdown.BriefResult =
    s"${m.id} ${m.state}" + m.note.filter(n => n.nonEmpty && n != m.state).fold("")(n => s" ($n)")

  def goal(g: GoalProgress): com.histmersion.api.automation.AutopilotMarkdown.GoalResult =
    val mgrs = if g.managers.isEmpty then "no managers" else g.managers.map(brief).mkString("; ")
    val pace = s"${g.doneLast7} done in 7 days, ${g.open} open" + g.etaDays.fold("")(d => s", ~$d day(s) at that pace")
    s"**${g.slug}** _(${g.status})_${g.parent.fold("")(p => s" ⊂ $p")} · ${Plural.count(g.sources, _root_.com.histmersion.api.Plural.CountSingular("source"))}, " +
      s"${Plural.count(g.articles, "article")}" + (if g.openApprovals > 0 then
                                                     s", ${Plural.count(g.openApprovals, "open approval")}"
                                                   else "") +
      s" · $mgrs · $pace" + g.lastActivity.fold("")(t => s" · last activity $t")

  private def pace(g: FocusGoal): com.histmersion.api.automation.AutopilotMarkdown.PaceResult =
    _root_.com.histmersion.api.automation.AutopilotMarkdown.PaceResult(s"${g.doneLast7} done in 7 days, ${g.open} open" + g.etaDays.fold("")(d => s", ~$d day(s) at that pace"))

  def focus(f: FocusInfo): com.histmersion.api.automation.AutopilotMarkdown.FocusResult =
    val head = s"## Focus: ${if ((f.on) == _root_.com.histmersion.api.automation.Autopilot.FocusInfo.On(true)) then s"at most ${f.max} active goals" else "not in force"}" +
      (if f.maxFrom == _root_.com.histmersion.api.automation.Autopilot.FocusInfo.MaxFrom("person") then " (set by a person)" else " (agents.autopilot.max-active-goals)")
    val lines = List(
      f.note.map(n => s"_${n}_"),
      Option.when(f.held > 0)(
        s"${Plural.count(f.held, "model-using item")} held for the focus (nothing cancelled; runs in flight finish)"
      ),
      Option.when(f.changes.nonEmpty)(s"Just now: ${f.changes.mkString("; ")}")
    ).flatten
    val active =
      if f.active.isEmpty then "_No goal is active._"
      else
        f.active.zipWithIndex
          .map { (g, i) =>
            s"${i + 1}. **${g.slug}** _(${g.status}, ${g.stage})_${if g.pinned then " (pinned)" else ""} — ${g.why}" +
              g.doing.fold("")(d => s"\n   - now: $d") + s"\n   - ${pace(g)}"
          }
          .mkString("\n")
    val queue =
      if f.queue.isEmpty then "_Nothing waits for a slot._"
      else
        f.queue.zipWithIndex.map((g, i) => s"${i + 1}. ${g.slug} _(${g.stage})_ — ${g.why}").mkString("\n") +
          (if f.queued > f.queue.size then s"\n\n…and ${f.queued - f.queue.size} more" else "")
    val waits = f.waiting.groupBy(_.code).toList.sortBy(-_._2.size).map { (c, ws) =>
      s"- ${Plural.count(_root_.com.histmersion.api.Plural.CountN((ws.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("goal"))} $c: " + ws
        .take(5)
        .map(w => s"${w.slug}${if w.pinned then " (pinned)" else ""}")
        .mkString(", ") +
        (if ws.size > 5 then ", …" else "") + ws.headOption.fold("")(w => s" — e.g. ${w.why.take(200)}")
    }
    (List(head, "") ++ lines
      .flatMap(l => List(l, "")) ++ List("### Active", "", active, "", s"### Queue (${f.queued})", "", queue) ++
      (if waits.isEmpty then Nil else List("", "### Cannot use a slot now", "") ++ waits)).mkString("\n")

  /** The autopilot's status; `ingestedBy` ends the line of today's ingests, `beforeBacklog` and `afterBacklog` are the
    * lines the page shows around the backlog's (the library's upstreams and its re-check).
    */
  def status(
      s: Autopilot.EngineStatus,
      ingestedBy: com.histmersion.api.automation.AutopilotMarkdown.StatusIngestedBy = _root_.com.histmersion.api.automation.AutopilotMarkdown.StatusIngestedBy(""),
      beforeBacklog: List[com.histmersion.api.automation.AutopilotMarkdown.StatusBeforeBacklog] = Nil,
      afterBacklog: List[com.histmersion.api.automation.AutopilotMarkdown.StatusAfterBacklog] = Nil
  ): com.histmersion.api.automation.AutopilotMarkdown.StatusResult =
    val head = s"# Autopilot: ${if ((s.on) == _root_.com.histmersion.api.automation.Autopilot.EngineStatus.On(true)) then "ON" else "off"}" +
      s.since.fold("")(t => s" (since $t${s.by.fold("")(b => s", by $b")})")
    val warn = List(
      s.drain.map(d =>
        if d.drained then
          s"DRAINED since ${d.drainedAt.getOrElse(d.since)} (draining since ${d.since}" +
            s"${
                if d.by.nonEmpty then s" by ${d.by}" else ""
              }): nothing runs and nothing new starts — safe to stop the server; " +
            "`histmersion agent autopilot undrain` lifts it"
        else
          s"DRAINING since ${d.since}${if d.by.nonEmpty then s" by ${d.by}" else ""}: nothing new starts; " +
            s"${Plural.count(d.inFlight, "run")} in flight still finishing"
      ),
      Option.when(!((s.agentsEnabled) == _root_.com.histmersion.api.automation.Autopilot.EngineStatus.AgentsEnabled(true)))("agents are disabled on this server (agents.enabled = false): nothing runs"),
      Option.when(((s.agentsEnabled) == _root_.com.histmersion.api.automation.Autopilot.EngineStatus.AgentsEnabled(true)) && !((s.managersEnabled) == _root_.com.histmersion.api.automation.Autopilot.EngineStatus.ManagersEnabled(true)))(
        "the managers are disabled (agents.managers.enabled = false): nothing runs"
      ),
      Option.when(!((s.cronsEnabled) == _root_.com.histmersion.api.automation.Autopilot.EngineStatus.CronsEnabled(true)))(Autopilot.EngineStatus.CronsOff),
      s.goalPlansWait.map(w => s"$w; a person may still approve a plan")
    ).flatten.map(w => s"- **$w**")
    val rules =
      s.approvals.map(r => s"${r.kind} ${r.mode}${if r.cap.nonEmpty then s" (${r.cap})" else ""}").mkString(", ")
    val facts = List(
      s"- approvals: ${if rules.isEmpty then "a person's" else rules}",
      s"- legal open-access ingests today: ${s.ingestedToday}${
          if s.ingestCap > 0 then s" of ${s.ingestCap} (all watchers)" else ""
        }" + ingestedBy
    ) ++
      beforeBacklog ++ List(
        s"- ${ManagersMarkdown.backlog(s.backlog, s.backlogDays)}"
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
    _root_.com.histmersion.api.automation.AutopilotMarkdown.StatusResult((List(head, "") ++ warn ++ facts).mkString("\n") + changes + s.focus.fold("")(f => "\n\n" + focus(f)) +
      s"\n\n## Goals (${s.goals.size})\n\n" + goals)
