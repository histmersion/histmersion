package com.histmersion
package domain

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class SchedulerDefaults(
    strategy: SchedulerDefaults.Strategy,
    agingHours: SchedulerDefaults.AgingHours,
    maxWaitHours: SchedulerDefaults.MaxWaitHours,
    minShare: SchedulerDefaults.MinShare,
    builtInWeights: SchedulerDefaults.BuiltInWeights,
    typeWeights: SchedulerDefaults.TypeWeights,
    builtInPriorities: SchedulerDefaults.BuiltInPriorities,
    typePriorities: SchedulerDefaults.TypePriorities,
    gatheringSlots: SchedulerDefaults.GatheringSlots
)

object SchedulerDefaults:
  type Strategy = Strategy.T
  object Strategy extends RefinedType[String, Pure]
  type AgingHours = AgingHours.T
  object AgingHours extends RefinedType[Int, Pure]
  type MaxWaitHours = MaxWaitHours.T
  object MaxWaitHours extends RefinedType[Int, Pure]
  type MinShare = MinShare.T
  object MinShare extends RefinedType[Double, Pure]
  type BuiltInWeights = BuiltInWeights.T
  object BuiltInWeights extends RefinedType[String, Pure]
  type TypeWeights = TypeWeights.T
  object TypeWeights extends RefinedType[String, Pure]
  type BuiltInPriorities = BuiltInPriorities.T
  object BuiltInPriorities extends RefinedType[String, Pure]
  type TypePriorities = TypePriorities.T
  object TypePriorities extends RefinedType[String, Pure]
  type GatheringSlots = GatheringSlots.T
  object GatheringSlots extends RefinedType[Int, Pure]
