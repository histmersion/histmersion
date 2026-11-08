package com.histmersion
package domain

import io.github.iltotore.iron.constraint.any.Pure

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
  object MaxFailures extends Newtype[Int, Pure]
  type BackoffMinutes = BackoffMinutes.T
  object BackoffMinutes extends Newtype[Int, Pure]
  type MaxBackoffMinutes = MaxBackoffMinutes.T
  object MaxBackoffMinutes extends Newtype[Int, Pure]
  type DailyUsd = DailyUsd.T
  object DailyUsd extends Newtype[Double, Pure]
