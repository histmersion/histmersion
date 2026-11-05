package com.histmersion.domain

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
  object Strategy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgingHours = AgingHours.T
  object AgingHours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxWaitHours = MaxWaitHours.T
  object MaxWaitHours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinShare = MinShare.T
  object MinShare extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuiltInWeights = BuiltInWeights.T
  object BuiltInWeights extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypeWeights = TypeWeights.T
  object TypeWeights extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuiltInPriorities = BuiltInPriorities.T
  object BuiltInPriorities extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypePriorities = TypePriorities.T
  object TypePriorities extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GatheringSlots = GatheringSlots.T
  object GatheringSlots extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
