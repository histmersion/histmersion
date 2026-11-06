package com.histmersion.api.automation

import com.histmersion.api.Plural
import com.histmersion.api.given
object AgentSchedulerMarkdown:
  type GoalG = GoalG.T
  object GoalG extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitingResult = WaitingResult.T
  object WaitingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinutesResult = MinutesResult.T
  object MinutesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinutesM = MinutesM.T
  object MinutesM extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusResult = StatusResult.T
  object StatusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WhyResult = WhyResult.T
  object WhyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import AgentScheduler.*

  private def goal(g: Option[com.histmersion.api.automation.AgentSchedulerMarkdown.GoalG]) = g.fold("library work")(x => s"goal $x")

  def waiting(w: Waiting): com.histmersion.api.automation.AgentSchedulerMarkdown.WaitingResult =
    val place = if (w.position).value > 0 then s"${w.position}. " else "- "
    s"$place#${w.item} ${w.jobType} **${w.target}** (${w.manager}; ${goal(w.goal)}; priority ${w.priority}; " +
      s"waited ${AgentSchedulerMarkdown.minutes(w.waitedMinutes)}) — _${w.code}_: ${w.why}" +
      (if w.behind.isEmpty then "" else s" · behind ${w.behind.mkString(", ")}")

  def minutes(m: com.histmersion.api.automation.AgentSchedulerMarkdown.MinutesM): com.histmersion.api.automation.AgentSchedulerMarkdown.MinutesResult =
    if (m).value < 60 then _root_.com.histmersion.api.automation.AgentSchedulerMarkdown.MinutesResult(s"$m min") else _root_.com.histmersion.api.automation.AgentSchedulerMarkdown.MinutesResult(s"${(m).value / 60} h" + (if (m).value % 60 != 0 then s" ${(m).value % 60} min" else ""))

  def status(s: SchedulerStatus): com.histmersion.api.automation.AgentSchedulerMarkdown.StatusResult =
    val head = s"# Scheduler: ${s.strategy}" + (if s.strategyFrom == _root_.com.histmersion.api.automation.AgentScheduler.SchedulerStatus.StrategyFrom("person") then " (set by a person)"
                                                else " (agents.scheduler.strategy)")
    val facts = List(
      Some(
        s.at.fold("- no pass yet")(t =>
          s"- ${if ((s.dry) == _root_.com.histmersion.api.automation.AgentScheduler.SchedulerStatus.Dry(true)) then "what a pass would do now (nothing started)" else s"last pass $t"}"
        )
      ),
      s.stop.map(x => s"- **nothing starts: $x**"),
      Some(
        s"- runs at once: ${s.occupied} of ${s.maxRuns} occupied (agents.max-concurrent-runs; a run waiting for a person " +
          "holds no place)"
      ),
      Option.when(s.pools.nonEmpty)(
        "- plan pools: " + s.pools.map(p => s"${p.pool} ${p.used} of ${p.cap}").mkString(", ")
      ),
      Option.when((s.forPerson).value > 0)(
        s"- ${Plural.count(s.forPerson, _root_.com.histmersion.api.Plural.NounSingular("item"))} wait for a person (an approval or a hold): they hold no place"
      ),
      Option.when(s.settings.nonEmpty)("- " + s.settings.mkString(" · "))
    ).flatten
    val types = if s.types.isEmpty then ""
    else
      "\n\n## Job types\n\n| type | weight | priority | recent starts | waiting |\n|---|---|---|---|---|\n" +
        s.types.map(t => s"| ${t.jobType} | ${t.weight} | ${t.priority} | ${t.recent} | ${t.waiting} |").mkString("\n")
    val started = if s.started.isEmpty then ""
    else
      "\n\n## Started by the last pass\n\n" +
        s.started
          .map(x => s"- #${x.item} ${x.jobType} **${x.target}** (${x.manager}; ${goal(x.goal)}) — ${x.why}")
          .mkString("\n")
    val queue = "\n\n## Waiting (" + s.waitingTotal + ")\n\n" +
      (if s.waiting.isEmpty then "_Nothing waits._"
       else
         s.waiting.map(waiting).mkString("\n") +
           (if (s.waitingTotal).value > s.waiting.size then s"\n\n…and ${(s.waitingTotal).value - s.waiting.size} more" else ""))
    val strategies = "\n\n## Strategies\n\n" + s.strategies
      .map(x => s"- `${x.name}`${if x.name == s.strategy then " (in effect)" else ""}: ${x.describe}")
      .mkString("\n")
    val changes = if s.changes.isEmpty then "" else "\n\n## Just now\n\n" + s.changes.map(c => s"- $c").mkString("\n")
    (head :: "" :: facts).mkString("\n") + changes + types + started + queue + strategies

  def why(w: WhyInfo): com.histmersion.api.automation.AgentSchedulerMarkdown.WhyResult =
    s"# #${w.item} ${w.kind} ${w.target}\n\n- manager: ${w.manager}\n- status: ${w.status}" +
      w.run.fold("")(r => s" (run ${r.take(8)})") + w.approval.fold("")(a => s"\n- waits for approval #$a") +
      s"\n- ${if w.code.nonEmpty then s"_${w.code}_: " else ""}${w.why}" +
      (if (w.position).value > 0 then s"\n- place in the order: ${w.position}" else "") +
      (if w.behind.isEmpty then "" else s"\n- behind: ${w.behind.mkString(", ")}")
