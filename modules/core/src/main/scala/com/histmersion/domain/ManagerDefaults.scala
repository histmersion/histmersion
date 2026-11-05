package com.histmersion.domain

final case class ManagerDefaults(
    maxFailures: ManagerDefaults.MaxFailures,
    backoffMinutes: ManagerDefaults.BackoffMinutes,
    maxBackoffMinutes: ManagerDefaults.MaxBackoffMinutes,
    dailyUsd: ManagerDefaults.DailyUsd,
    review: ManagerRunDefaults,
    enrich: ManagerRunDefaults,
    seed: ManagerRunDefaults,
    watch: ManagerRunDefaults
)

object ManagerDefaults:
  type MaxFailures = MaxFailures.T
  object MaxFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackoffMinutes = BackoffMinutes.T
  object BackoffMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxBackoffMinutes = MaxBackoffMinutes.T
  object MaxBackoffMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DailyUsd = DailyUsd.T
  object DailyUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
