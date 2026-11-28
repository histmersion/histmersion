package com.histmersion
package domain.automation.autopilot

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.AutopilotPolicy.DepthLimit
import com.histmersion.domain.AutopilotPolicy.ApprovedToday
import com.histmersion.domain.AutopilotPolicy.Cap
import com.histmersion.domain.AutopilotPolicy.Rationale
import com.histmersion.domain.AutopilotPolicy.counted

final case class GoalPlanFacts(
    goal: GoalPlanFacts.Goal,
    subGoals: GoalPlanFacts.SubGoals,
    queries: GoalPlanFacts.Queries,
    works: GoalPlanFacts.Works,
    depth: GoalPlanFacts.Depth
)

object GoalPlanFacts:
  type Goal = Goal.T
  object Goal extends Newtype[String, Pure]
  type SubGoals = SubGoals.T
  object SubGoals extends Newtype[Int, Pure]
  type Queries = Queries.T
  object Queries extends Newtype[Int, Pure]
  type Works = Works.T
  object Works extends Newtype[Int, Pure]
  type Depth = Depth.T
  object Depth extends Newtype[Int, Pure]

  extension (p: GoalPlanFacts)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def goalPlan(maxDepth: DepthLimit, approvedToday: ApprovedToday, cap: Cap): Ruling =
      if p.subGoals.value + p.queries.value + p.works.value == 0 then
        Ruling.Reject(Rationale("the plan proposes nothing"))
      else if p.subGoals.value > 0 && p.depth.value >= maxDepth.value then
        Ruling.Reject(
          Rationale(
            s"it proposes ${counted(p.subGoals.value, "sub-goal")} for a goal ${depthText(p.depth)}; " +
              s"sub-goals stop at depth $maxDepth (the deepest sub-goal the checks approve)"
          )
        )
      else if approvedToday.value >= cap.value then
        Ruling.Leave(Rationale(s"the daily cap of $cap goal plans is reached; decided tomorrow"))
      else
        Ruling.Approve(
          Rationale(
            s"${counted(p.subGoals.value, "sub-goal")}, ${counted(p.queries.value, "query", "queries")}, ${counted(p.works.value, "work")}" +
              s" (depth ${p.depth} of $maxDepth); ${approvedToday.value + 1} of $cap today"
          )
        )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def depthText(d: Depth): String =
    if d.value == 0 then "at the root" else s"$d level(s) below a root"
