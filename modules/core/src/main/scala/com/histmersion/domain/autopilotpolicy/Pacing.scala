package com.histmersion
package domain.autopilotpolicy

import com.histmersion.domain.AutopilotPolicy.Cron
import io.github.iltotore.iron.constraint.any.Pure

final case class Pacing(
    watchCron: Cron,
    seedCron: Cron,
    finishedWatchCron: Cron,
    watchIngestPerDay: Pacing.WatchIngestPerDay,
    extendMinNew: Pacing.ExtendMinNew
)

object Pacing:
  type WatchIngestPerDay = WatchIngestPerDay.T
  object WatchIngestPerDay extends Newtype[Int, Pure]
  type ExtendMinNew = ExtendMinNew.T
  object ExtendMinNew extends Newtype[Int, Pure]
