package com.histmersion
package domain.autopilotpolicy

import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.Manager
import com.histmersion.domain.AutopilotPolicy.Cron
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

final case class ManagerRow(
    id: ManagerRow.Id,
    kind: Manager.Kind.Wire,
    goal: Option[Goal.Slug],
    state: Manager.State.Wire,
    cron: Option[Cron],
    autoIngest: Option[ManagerRow.AutoIngest],
    planItem: ManagerRow.PlanItem = ManagerRow.PlanItem(false),
    extendMinNew: Option[Pacing.ExtendMinNew] = None
)

object ManagerRow:
  type Id = Id.T
  object Id extends Newtype[String, Pure]:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def seed(goal: Goal.Slug): Id = Id(s"seed:${goal.value}")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def watch(goal: Goal.Slug): Id = Id(s"watch:${goal.value}")
  type AutoIngest = AutoIngest.T
  object AutoIngest extends Newtype[Boolean, Pure]
  type PlanItem = PlanItem.T
  object PlanItem extends Newtype[Boolean, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def spread(key: Id): Int = key.value.foldLeft(17)((h, c) => 31 * h + c) & Int.MaxValue
