package com.histmersion
package domain

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

@movesTo(FeaturePlace.AutomationScheduler)
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
  object Strategy extends Newtype[String, Pure]
  type AgingHours = AgingHours.T
  object AgingHours extends Newtype[Int, Pure]
  type MaxWaitHours = MaxWaitHours.T
  object MaxWaitHours extends Newtype[Int, Pure]
  type MinShare = MinShare.T
  object MinShare extends Newtype[Double, Pure]
  type BuiltInWeights = BuiltInWeights.T
  object BuiltInWeights extends Newtype[String, Pure]
  type TypeWeights = TypeWeights.T
  object TypeWeights extends Newtype[String, Pure]
  type BuiltInPriorities = BuiltInPriorities.T
  object BuiltInPriorities extends Newtype[String, Pure]
  type TypePriorities = TypePriorities.T
  object TypePriorities extends Newtype[String, Pure]
  type GatheringSlots = GatheringSlots.T
  object GatheringSlots extends Newtype[Int, Pure]
