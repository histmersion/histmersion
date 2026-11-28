package com.histmersion
package domain.automation.scheduler

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.automation.scheduler.JobType
import com.histmersion.domain.automation.scheduler.Strategy
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.SchedulerDefaults
import com.histmersion.Newtype

final case class Settings(
    strategy: Strategy,
    agingHours: Settings.AgingHours = Settings.AgingHours(24),
    maxWaitHours: Settings.MaxWaitHours = Settings.MaxWaitHours(72),
    minShare: Settings.MinShare = Settings.MinShare(0.05),
    weights: Map[JobType, Settings.WeightsValue] = Map.empty,
    typePriority: Map[JobType, Settings.TypePriorityValue] = Map.empty,
    gatheringSlots: Settings.GatheringSlots = Settings.GatheringSlots(1)
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def weight(t: JobType): Settings.WeightsValue =
    Settings.WeightsValue(math.max(0, weights.get(t).fold(1)(_.value)))
  def priorityOf(t: JobType): Settings.TypePriorityValue =
    typePriority.getOrElse(t, Settings.TypePriorityValue(0))

object Settings:
  export SchedulerDefaults.{AgingHours, MaxWaitHours, MinShare}
  type WeightsValue = WeightsValue.T
  object WeightsValue extends Newtype[Int, Pure]
  type TypePriorityValue = TypePriorityValue.T
  object TypePriorityValue extends Newtype[Int, Pure]
  export SchedulerDefaults.GatheringSlots
